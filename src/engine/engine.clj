(ns engine.engine
  (:require [com.stuartsierra.dependency :as dep]
            [clojure.core.async :as async :refer [>! <!]]))

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

(defn- can-apply-system [system graph completed]
  (every? #(contains? completed %) (dep/transitive-dependencies graph system)))

(defn- apply-stage
  "Runs all the systems for a given stage and return the resulting game world."
  [world stage]
  (let [systems (-> world :systems (get stage))
        graph (-> world :metadata (get :system-graph dep/graph))
        leaf-nodes (filter #(empty? (dep/immediate-dependents graph %)) systems)
        channel (async/chan (count leaf-nodes))]

        ;; New set for finished systems...
        ;; Each system in the graph without dependencies gets its own thread to 
        ;; build results from
        ;; Run all systems in parallel and merge results
        ;; Merge with world.
        ;; See merge vs deep merging in merge docs.

    ;; Return resulting world from stage.
    (doseq [node leaf-nodes]
      (async/go (>! channel (list node (apply-system world node)))))

    ;; 1. Setup merged node set
    ;; 2. Setup results map
    ;; 3. When receiving results, check all dependants of the node. If we have 
    ;; all their results, merge with world and loop? 
    (loop [merged {}
           results {}]
      (let [[node instructions] (<! channel)
            results (assoc results node instructions)]
      (async/go (<! channel))))
    ;; I have no fucking clue what to do here.

    (async/close! channel)))


