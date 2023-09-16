(ns test.components
  (:require [engine.components :as ec :refer :all]))

;; Test insertion
(comment (-> (create-component-store 10)
             (ec/insert 1 23)
             (ec/insert 1 38)
             (ec/insert 2 21)))

;; Test deletion on first element
(comment (-> (create-component-store 10)
             (ec/insert 1 23)
             (ec/unset 1)))

;; Test deletion on 2nd element
(comment (-> (create-component-store)
             (ec/insert 1 11)
             (ec/insert 2 22)
             (ec/unset 1)
             (ec/get-items)))

