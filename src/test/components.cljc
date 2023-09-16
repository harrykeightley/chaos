(ns test.components
  (:require [engine.components :as ec :refer :all]
            [clojure.test :refer [deftest is run-tests]]))

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

(run-tests)
