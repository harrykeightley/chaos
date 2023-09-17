(ns test.world
  (:require [engine.world :refer :all]
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
    (is (some #{1} entities)))
  )

(run-tests)
