(ns engine.world
  (:require [com.stuartsierra.dependency :as dep]
            [clojure.core.async :as async :refer [>! <! <!!]]
            [engine.components :as ec]
            [engine.store :as es :refer [Store]]))

(defprotocol World
  (get-entities [world]
    "Returns all entities in the world")

  (create-entity [world] [world start]
    "Finds a unique entity within the world. If `start` is supplied, starts
    counting from `(+ start 1)`.

    Doesn't actually reserve this id, so if creating multiple entities, the 
    id of the first should be passed in as the `start` parameter when generating 
    the second.")

  (add-system [world stage system] [world system]
    "Adds the system to the world under the supplied stage. 

    If no stage is supplied, adds the system under the `:update` stage")

  (add-systems [world stage systems] [world systems]
    "Adds the systems to the world under the supplied stage.

    If no stage is supplied, adds all systems under the `:update` stage")

  (add-system-dependency [world system dependency]
    "Add intrastage system dependencies")

  (add-stage-dependency [world stage dependency]
    "Makes sure the stage is run after the supplied dependency")

  (query [world required-components]
    "Returns a zipped sequence of components whose entity has all the components 
    supplied to `required-components`
    
    e.g. `(query world [:transform :player])` Might return a sequence of 
    <Transform, Player> component tuples, corresponding to all entities in the 
    world with both Transform and Player components.")

  (get-events [world event]
    "Gets all the events which of the supplied type")

  (get-resources [world resources]
    "Returns a map from supplied resource names to their values.")

  (apply-stage [world stage]
    "Runs all the systems for a given stage and return the resulting game world.")

  (step [world]
    "Runs one step of the world and returns the resulting world. 
    
    Equivalent to running all stages.")

  (play [world]
    "Runs the world until the :exit flag is set within its metadata"))

(declare find-depths)
(declare apply-system-batch)
(declare forward-to-components)
(declare unique-int)

(def reserved-stages
  #{:start-up
    :pre-step :pre-stage :update :post-stage :post-step
    :tear-down})

(defrecord GameWorld [component-stores events resources systems metadata]
  World
  (get-entities [_]
    (-> (get component-stores :id (ec/create-component-store))
        (ec/get-components)
        set))

  (create-entity [world]
    (create-entity world 0))
  (create-entity [world start]
    (unique-int (get-entities world) start))

  (add-systems [world systems]
    (add-systems world :update systems))
  (add-systems [world stage systems]
    (let [world (->> (get-in world [:systems stage])
                     (concat systems)
                     (assoc-in world [:systems stage]))]
      ;; Ensure update happens before user-added stages.
      (if (reserved-stages stage)
        world
        (add-stage-dependency world stage :update))))

  (add-system [world system]
    (add-system world :update system))
  (add-system [world stage system]
    (add-systems world stage (list system)))

  (add-stage-dependency [world stage dependency]
    (let [graph (-> world
                    :metadata
                    (get :stage-graph (dep/graph)))
          next-graph (dep/depend graph stage dependency)
          metadata (assoc (:metadata world) :stage-graph next-graph)]

      (assoc world :metadata metadata)))

  (add-system-dependency [world system dependency]
    (let [graph (-> world
                    :metadata
                    (get :system-graph (dep/graph)))
          next-graph (dep/depend graph system dependency)
          metadata (assoc (:metadata world) :system-graph next-graph)]
      (assoc world :metadata metadata)))

  (query [_ required-component-types]
    (let [length (fn [component] (-> (component-stores component) :n))
          components (filter component-stores required-component-types)
          sorted-components (sort-by length components)]
      (if (empty? components)
        nil
        (let [ids (->> (first sorted-components) ;; component-type with smallest count
                       component-stores
                       ec/get-items
                       (map first))
              other-stores (map component-stores (rest sorted-components))
              reducer (fn [ids store]
                        (filter #(ec/has-id? store %) ids))
              matching-ids (reduce reducer ids other-stores)
              ;; Build n-tuples of components based on original ordering in 
              ;; required-component-types
              stores (map component-stores components)
              build-tuple (fn [id] (map #(ec/get-component % id) stores))]
          (map build-tuple matching-ids)))))

  (get-events [_ event-type]
    (get events event-type))

  (get-resources [_ requested-resources]
    (select-keys resources requested-resources))

  (apply-stage
    [world stage]
    (let [systems (get systems stage)
          graph (get metadata :system-graph (dep/graph))
        ;; TODO I could probably cache this
          depths (find-depths systems graph)
          batched-stages (->> (keys depths)
                              (group-by depths)
                              (vals))]
      (reduce apply-system-batch world batched-stages)))

  (step [world]
    (let [sorted-stages (->> (get-in world [:metadata :stage-graph] (dep/graph))
                             dep/topo-sort
                             (remove (set reserved-stages)))]
      (-> world
          (apply-stage :pre-step)
          ;; TODO feels like this shouldnt be here...
          (apply-stage :update)
          (#(reduce apply-stage % sorted-stages))
          (apply-stage :post-step))))

  (play [world]
    (-> world
        (apply-stage :start-up)
        ;; TODO Should clean up with reduce
        (#(loop [world %]
            (if (contains? (:metadata world) :exit)
              world
              (recur (step world)))))
        (apply-stage :tear-down)))

  Store
  (sets [world path values]
    (case (first path)
      :components (forward-to-components es/sets world path values)
      (assoc-in world path values)))

  (adds [world path values]
    (case (first path)
      :components (forward-to-components es/adds world path values)
      :events (let [event (second path)
                    result (get events event [])
                    result (apply conj result values)]
                (assoc-in world [:events event] result))
      (if (get-in world path)
        world ;; if already exists, just return.
        (es/sets world path values))))

  (deletes [world path]
    (es/deletes world (drop-last 1 path) [(last path)]))

  (deletes [world path values]
    (case (first path)
      :components (forward-to-components es/deletes world path values)
      (update-in world path #(apply dissoc % values))))

  (updates [world path f]
    (case (first path)
      :components (forward-to-components es/updates world path f)
      (update-in world path f))))

(defn- forward-to-components
  "Forwards a `engine.store/Store` method to the relevant component store."
  ([f world path]
   (forward-to-components f world path nil))
  ([f world path values]
   (let [component (second path)
         remaining-path (drop 2 path)
         stores (:component-stores world)
         store (-> (get stores component (ec/create-component-store))
                   (f remaining-path values))
         ;; Create all necessary entity ids if we haven't yet seen them.
         entities (get stores :id (ec/create-component-store))
         ids-to-create (cond (not (#{es/adds es/sets} f)) nil
                             (empty? remaining-path) (map first values)
                             :else remaining-path)
         entities (reduce #(ec/insert %1 %2 %2) entities ids-to-create)]
     (-> world
         (assoc-in [:component-stores component] store)
         (assoc-in [:component-stores :id] entities)))))

(defn create-world
  "Creates a new empty world state"
  ^GameWorld []
  (->GameWorld {} {} {} {} {}))

(defmacro defsys
  "Creates a macro for specifying systems based on their dependencies, and 
  creates bindings for the all bindings passed in."
  {:clj-kondo/ignore [:unresolved-symbol]} ;; supress linting errors
  [system-name doc bindings & body]
  `(defn ~(symbol system-name) ~doc [world#]
     (let [~'world world#
           ~'components (query world# ~(:components bindings))
           ~'resources (get-resources world# ~(:resources bindings))
           ~'events (get-events world# ~(:events bindings))]
       ;; If events requested but those events are empty, return nil
       (if (and ~(:events bindings) (empty? ~'events))
         []
         ;; Return [] if the body of the system returns nil.
         (or (do ~@body) [])))))


(defn find-depths
  "Performs BFS on the systems in a graph to determine their depths.
  Returns a map from systems to depths.
  
  Depth is defined as the maximum steps between that node and a leaf node.
  Depth is used as an analogy for runtime order within this engine."
  [systems graph]
  (let [leaf-nodes (filter #(empty? (dep/immediate-dependencies graph %)) systems)
        initial-nodes (zipmap leaf-nodes (repeat 0))]
    (loop [queue (reduce conj #?(:cljs #queue []
                                 :default clojure.lang.PersistentQueue/EMPTY) initial-nodes)
           result {}]
      (if (empty? queue) result
          (let [[node depth] (peek queue)
                neighbours (dep/immediate-dependents graph node)
                next-nodes (for [neighbour neighbours
                                 :let [next-depth (+ 1 depth)]
                                 :let [next-node [neighbour next-depth]]
                                 :when (< (get result neighbour -1) next-depth)]
                             next-node)]
            (recur (reduce conj (pop queue) next-nodes) (assoc result node depth)))))))

(defn- apply-system-results
  "Takes the results of running a system and applies them to the world. "
  [world results]
  (let [apply-result (fn [world result]
                       (let [[k path values] result
                             f (case k
                                 :add es/adds
                                 :set es/sets
                                 :update es/updates
                                 :delete es/deletes
                                 (do (println "Invalid command:" k)
                                     #(first %&)))]
                         ;; TODO emit a raw event of the results just processed.
                         (f world path values)))]
    (reduce apply-result world results)))

(defn- apply-system-batch
  "Applies a sequence of independent systems and return the resulting world."
  [world batch]
  (let [channel (async/chan)]
    (doseq [system batch]
      (async/go (>! channel (system world))))
    ;; Blocking take from the result thread
    (<!! (async/go-loop [world world i 0]
           (if (>= i (count batch))
             (do (async/close! channel) world)
             (recur (apply-system-results world (<! channel)) (inc i)))))))

(defn unique-int
  "Returns an integer that doesn't exist in the given set, `existing`.

  To optimize, starts searching from `count`.
  "
  [existing start]
  (let [next-entity (inc start)]
    (if (contains? existing next-entity)
      (recur existing next-entity)
      next-entity)))

