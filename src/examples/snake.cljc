(ns examples.snake
  (:require [engine.world :as ew :refer [defsys]]
            [engine.utils :refer [millis!]]))

(defrecord Stopwatch [ms last-time event loops?])

(defn create-timer
  ([ms event loops?] (->Stopwatch ms (millis!) event loops?)))

(defsys add-timer "" {}
  [[:add [:resources :timer] [(create-timer 1000 :tick true)]]])

;; Note: I should add something like this to the library
(defsys tick "Updates the timer"
  {:resources [:timer]}
  (let [timer (:timer resources)
        now (millis!)
        ms-diff (- now (:last-time timer))
        next-timer (assoc timer :last-time now)
        event-command [:add [:events (:event timer)] nil]
        set-command [:set [:resources :timer] next-timer]]
    (if (< ms-diff (:ms timer))
      [event-command set-command]
      [set-command])))

(defsys shout "Shouts every tick"
  {:events :tick}
  (when-not empty? events (println "TICK")))

(-> (ew/create-world)
    (ew/add-stage :start-up :start-up)
    (ew/add-stage :update :update)
    (ew/add-system :start-up add-timer)
    (ew/add-system :update tick)
    (ew/add-system :update shout)
    (ew/add-system-dependency shout tick))

