(ns test.world
  (:require [engine.world :refer :all]
            [engine.components :as ec]
            [com.stuartsierra.dependency :as dep]
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

;; Adding components
(comment (-> (create-world)
             (es/adds [:components :position 1] [[1 2]])
             (es/sets [:resources :position] [2 3])
             (es/adds [:resources :position] [1 2])
             :component-stores
             :position
             (ec/get-items)))

;; Setting components
(comment (-> (create-world)
             (es/adds [:components :position 1] [[1 2]])
             (es/sets [:components :position 1] (partial inc))
             :component-stores
             :position
             (ec/get-items)))
