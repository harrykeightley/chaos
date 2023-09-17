(ns test.world
  (:require [engine.world :as ew :refer :all]
            [engine.components :as ec]
            [com.stuartsierra.dependency :as dep]
            [clojure.test :refer [deftest is run-tests]]
            [engine.store :as es]))

;; test add-stage-dependency
(comment (let [world (-> (create-world) (add-stage-dependency :a :b))]
           (-> world :metadata :stage-graph dep/topo-sort)))

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

;; ------- Store Protocol functions --------
(deftest test-add-component
  (let [world (-> (create-world)
                  (es/adds [:components :position 1] [[1 2]]))
        component (-> world :component-stores :position (ec/get-component 1))]
    (is (= [1 2] component))))

(deftest test-adding-component-with-same-id-does-nothing
  (let [world (-> (create-world)
                  (es/adds [:components :position 1] [[1 2]])
                  (es/adds [:components :position 1] [[2 3]]))
        component (-> world :component-stores :position (ec/get-component 1))]
    (is (= [1 2] component))))

(deftest test-set-component
  (let [world (-> (create-world)
                  (es/sets [:components :position 1] [[1 2]]))
        component (-> world :component-stores :position (ec/get-component 1))]
    (is (= [1 2] component))))

(deftest test-setting-component-should-override
  (let [world (-> (create-world)
                  (es/adds [:components :position 1] [[1 2]])
                  (es/sets [:components :position 1] [[2 3]]))
        component (-> world :component-stores :position (ec/get-component 1))]
    (is (= [2 3] component))))

(deftest test-update-component
  (let [world (-> (create-world)
                  (es/adds [:components :position 1] [[1 2]])
                  (es/updates [:components :position 1] #(map (partial * 2) %)))
        component (-> world :component-stores :position (ec/get-component 1))]
    (is (= [2 4] component))))

(deftest test-update-all-components
  (let [world (-> (create-world)
                  (es/adds [:components :position 1] [1])
                  (es/adds [:components :position 2] [2])
                  (es/adds [:components :position 3] [3])
                  (es/adds [:components :position 4] [4])
                  (es/updates [:components :position] (partial * 2)))
        components (-> world :component-stores :position (ec/get-components))]
    (is (every? #{2 4 6 8} components))))

(deftest test-delete-component
  (let [world (-> (create-world)
                  (es/adds [:components :position 1] [[1 2]])
                  (es/adds [:components :position 2] [[2 4]])
                  (es/deletes [:components :position 1])
                  (es/deletes [:components :position] [2]))
        components (-> world :component-stores :position (ec/get-components))]
    (is (empty? components))))

(deftest test-auto-create-entity
  (let [world (-> (create-world)
                  (es/adds [:components :position 1] [[1 2]]))
        entities (-> world :component-stores :id (ec/get-components))]
    (is (some #{1} entities))))

;; Resources
(deftest test-add-resource
  (let [world (-> (create-world)
                  (es/adds [:resources :position] [1 2]))
        resource (-> world :resources :position)]
    (is (= [1 2] resource))))

;; Events
(deftest test-add-null-event
  (let [world (-> (create-world)
                  (es/adds [:events :position] [nil]))
        events (-> world :events :position)]
    (is (= 1 (count events)))))

;; ------------- System and Stage Resolution ------------
(defsys create-component "" {}
  [[:add [:components :test 1] [1]]])

(defsys shout "" {}
  (println "AAAA"))

(comment (let [systems [println]
               stage :update
               world (ew/create-world)
               world (->> (get-in world [:systems stage])
                          (concat systems)
                          (assoc-in world [:systems stage]))]
           world))

(deftest test-add-system-with-explicit-stage
  (let [world (-> (create-world) (ew/add-system :start-up println))
        systems (get-in world [:systems :start-up])]
    (is (some #{println} systems))))

(deftest test-add-system-default
  (let [world (-> (create-world) (ew/add-system println))
        systems (get-in world [:systems :update])]
    (is (some #{println} systems))))

(deftest test-apply-stage
  (let [world (-> (create-world)
                  (ew/add-system :start-up create-component)
                  (ew/apply-stage :start-up))
        component (-> world :component-stores :test (ec/get-component 1))]
    (is (= component 1))))

(run-tests)
