(ns snake
  (:require [engine.world :as ew :refer [defsys]]
            [engine.utils :refer [millis!]]))

(defrecord Stopwatch [ms last-time event loops?])

(defn create-timer
  ([ms event loops?] (->Stopwatch ms (millis!) event loops?)))

(defsys add-timer "" {}
  [[:add [:resources :timer] (create-timer 1000 :tick true)]])

;; Note: I should add something like this to the library
(defsys tick "Updates the timer"
  {:resources [:timer]}
  (let [timer (:timer resources)
        now (millis!)
        ms-diff (abs (- now (:last-time timer)))
        next-timer (assoc timer :last-time now)
        event-command [:add [:events (:event timer)] [nil]]
        set-command [:set [:resources :timer] next-timer]]
    (if (> ms-diff (:ms timer))
      [set-command event-command]
      [])))

(comment (-> (ew/create-world)
             (ew/add-system :start-up add-timer)
             (ew/add-system tick)
             (ew/apply-stage :start-up)
             (ew/apply-stage :update)))

(defsys shout "Shouts every tick"
  {:events :tick}
  (when-not (empty? events)
    (println "TICK"))
  [])

(defsys reset-events "Resets events after a step"
  {}
  [[:set [:events] {}]])

;; Note if systems return nil channels will be annoyed.

(defn -main [& args]
  (-> (ew/create-world)
      (ew/add-system :start-up add-timer)
      (ew/add-system :update tick)
      (ew/add-system :update shout)
      (ew/add-system :post-step reset-events)
      (ew/add-system-dependency shout tick)
      (ew/play)))


