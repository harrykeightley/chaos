(ns engine.engine
  (:require [com.stuartsierra.dependency :as dep]
            [clojure.core.async :as async :refer [>! <! <!!]]))

(defprotocol World
  (add-system [world stage system]
    "Adds the system to the world under the supplied stage.")

  (add-systems [world stage systems]
    "Adds the systems to the world under the supplied stage.")

  (add-system-dependency [world system dependency]
    "Add intrastage system dependencies")

  (add-stage [world stage kind])

  (add-stage-dependency [world stage kind]
    "Makes sure the stage is run after all its dependencies have.")

  (step [world]
    "Runs one step of the world and returns the resulting world. 
    
    Equivalent to running all stages."))

(declare apply-stage)

(defrecord GameWorld [entities components events systems metadata]
  World
  (add-systems
    [world stage systems]
    (let [current-systems (:systems world)
          systems-of-stage (get current-systems stage)
          next-systems-of-stage (apply conj systems-of-stage systems)
          next-systems (assoc current-systems stage next-systems-of-stage)]
      (assoc world :systems next-systems)))

  (add-system
    [world stage system]
    (add-systems world stage (list system)))

  (add-stage-dependency
    [world stage dependency]
    (let [graph (-> world
                    :metadata
                    (get :stage-graph (dep/graph)))
          next-graph (dep/depend graph stage dependency)
          metadata (assoc (:metadata world) :stage-graph next-graph)]

      (assoc world :metadata metadata)))

  (add-system-dependency
    [world system dependency]
    (let [graph (-> world
                    :metadata
                    (get :system-graph (dep/graph)))
          next-graph (dep/depend graph system dependency)
          metadata (assoc (:metadata world) :system-graph next-graph)]
      (assoc world :metadata metadata)))

  (step
    [world]
    (let [sorted-stages (-> world :metadata :stage-graph dep/topo-sort)]
        ;; TODO: Maybe include services added to stages without dependencies??
      (reduce apply-stage sorted-stages))))

(defn create-world
  "Creates a new empty world state"
  ^GameWorld []
  (->GameWorld #{} {} {} {} {}))

(defn run
  "Runs the world until the :exit flag is set."
  [world]
  (when-not (contains? (:metadata world) :exit)
    (recur (step world))))

(defn new-entity-id
  "Returns an integer that doesn't exist in the given set, `entities`.

  To optimize, starts searching from `count`.
  "
  [entities count]
  (let [next-entity (inc count)]
    (if (contains? entities next-entity)
      (recur entities next-entity)
      next-entity)))

;; ----- Systems -----

(defrecord WorldSystem [system components])

;; Test add-systems
(comment (-> (create-world)
             (add-systems :update (->WorldSystem add-systems #{:test}))
             (add-systems :update (->WorldSystem #(println "hello") #{:test}))
             (add-systems :next (->WorldSystem #(println "goodbye") #{:test}))))

;; test add-stage-dependency
(comment (let [world (-> (create-world) (add-stage-dependency :a :b))]
           (-> world :metadata :stage-graph dep/topo-sort)))

(defn- apply-system
  "Runs a system and return the resulting changes"
  [world system]
  (:components system))

(defn- find-depths
  [systems graph]
  (let [leaf-nodes (filter #(empty? (dep/immediate-dependencies graph %)) systems)
        initial-nodes (zipmap leaf-nodes (repeat 0))]
    (loop [queue (reduce conj clojure.lang.PersistentQueue/EMPTY initial-nodes)
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

(comment (let [graph (-> (dep/graph)
                         (dep/depend :b :a)
                         (dep/depend :c :a)
                         (dep/depend :d :b)
                         (dep/depend :d :a)
                         (dep/depend :e :c)
                         (dep/depend :f :a)
                         (dep/depend :f :c)
                         (dep/depend :f :d))
               depths (find-depths [:a :b :c :d :e :f] graph)]
           (println (vals (group-by depths (keys depths))))))

(defn- apply-system-results
  "Takes the results of running a system and applies them to the world. "
  [world results] world)

(defn- apply-system-batch
  "Applies a sequence of independent systems and return the resulting world."
  [world batch]
  (let [channel (async/chan)]
    (doseq [system batch]
      (async/go (>! channel (apply-system system world))))
    ;; Blocking take from the result thread
    (<!! (async/go-loop [world world i 0]
           (if (>= i (count batch))
             (do (async/close! channel) world)
             (recur (apply-system-results world (<! channel)) (inc i)))))))

(defn- apply-stage
  "Runs all the systems for a given stage and return the resulting game world."
  [world stage]
  (let [systems (-> world :systems (get stage))
        graph (-> world :metadata (get :system-graph dep/graph))
        depths (find-depths systems graph)
        batched-stages (->> (keys depths)
                            (group-by depths)
                            (vals))]
    (reduce apply-system-batch world batched-stages)))


