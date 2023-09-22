(ns chaos.plugins.timer
  (:require [chaos.engine.utils :refer [millis!]]
            [chaos.engine.world :refer [defsys]]))

(defrecord Timer [ms last-time event payload loops?])

(defn create-timer
  ([ms event loops?] (create-timer ms event nil loops?))
  ([ms event payload loops?] (->Timer ms (millis!) event payload loops?)))

(defn should-emit? [timer]
  (let [now (millis!)
        ms-diff (abs (- now (:last-time timer)))]
    (> ms-diff (:ms timer))))

(defn update-timer [timer]
  (if (and (should-emit? timer) (:loops? timer))
    (assoc timer :last-time (millis!))
    timer))

(defmacro create-timer-resource-system
  "Creates a system which updates the timer at the supplied `resource-name`."
  [system-name resource-name]
  `(defsys ~system-name
     {:resources [~resource-name]}
     (let [timer (~resource-name resources)
           next-timer (update-timer timer)
           event-command [:add [:events (:event timer)] [(:payload timer)]]
           set-command [:set [:resources timer-name] next-timer]]
       (when (should-emit? timer)
         [set-command event-command]))))

(defmacro create-timer-system
  "Creates a system which updates all timers at the supplied `component-name`."
  [system-name component-name]
  `(defsys ~system-name
     {:components [~component-name]}
     (let [timers (->> (map first components) (filter should-emit?))
           event-groups (->> timers
                             (map #(mapv % [:event :payload]))
                             (group-by first))
           group->command #([:add [:events (first (first %))] (map second %)])
           event-commands (map group->command (vals event-groups))]
       (conj event-commands
             [:update [:components ~component-name] update-timer]))))


