(ns test.components
  (:require [chaos.engine.components :as ec :refer :all]
            [chaos.engine.store :as es]
            [clojure.test :refer [deftest is run-tests]]))

;; ------ SPARSE SET PROTOCOL METHODS --------
(deftest test-get-component
  (let [store (-> (create-component-store 10)
                  (ec/insert 1 23))]
    (is (= 23 (ec/get-component store 1)))))

(deftest test-get-components
  (let [store (-> (create-component-store 10)
                  (ec/insert 1 11)
                  (ec/insert 2 22)
                  (ec/insert 3 33))]
    (is (every? #{11 22 33} (ec/get-components store)))))

(deftest test-insertion
  (let [store (-> (create-component-store 10)
                  (ec/insert 1 23)
                  (ec/insert 1 38)
                  (ec/insert 2 21))]
    (is (ec/has-id? store 1))))

(deftest test-delete-one
  (let [store (-> (create-component-store 10)
                  (ec/insert 1 23)
                  (ec/unset 1))]
    (is (empty? (ec/get-items store)))))

(deftest test-delete-one-after-two-inserts
  (let [store (-> (create-component-store 10)
                  (ec/insert 1 11)
                  (ec/insert 2 22)
                  (ec/unset 1))]
    (is (= 1 (count (ec/get-items store))))
    (is (not (ec/has-id? store 1)))
    (is (ec/has-id? store 2))))

(deftest test-set
  (let [store (-> (create-component-store 10)
                  (ec/insert 1 23)
                  (ec/insert 1 24))]
    (is (= 1 (count (ec/get-items store))))
    (is (some #{24} (ec/get-components store)))))

;; ----------- STORE PROTOCOL METHODS --------------
(deftest test-store-add
  (let [store (-> (create-component-store 10)
                  (es/adds [1] [23])
                  (es/adds [1] [40])
                  (es/adds nil [[2 40]]))] ;; other way of adding
    (is (= 2 (count (ec/get-items store))))
    (is (= 23 (ec/get-component store 1)))
    (is (= 40 (ec/get-component store 2)))))

(deftest test-store-set
  (let [store (-> (create-component-store 10)
                  (es/adds [1] [23])
                  (es/sets [1] [40])
                  (es/sets nil [[2 40]]))] ;; other way of setting
    (is (= 2 (count (ec/get-items store))))
    (is (= 40 (ec/get-component store 1)))
    (is (= 40 (ec/get-component store 2)))))

(deftest test-store-delete
  (let [store (-> (create-component-store 10)
                  (es/adds [1] [23])
                  (es/adds [2] [40])
                  (es/adds [3] [30])
                  (es/deletes [1])
                  (es/deletes nil [2 3]))] ;; other way of setting
    (is (empty? (ec/get-items store)))))

(deftest test-store-update
  (let [store (-> (create-component-store 10)
                  (es/adds [1] [10])
                  (es/adds [2] [20])
                  (es/adds [3] [30])
                  (es/updates [1] (partial * 2))
                  (es/updates [2 3] (partial * 3)))] ;; other way of setting
    (is (every? #{20 60 90} (ec/get-components store)))))

(run-tests)
