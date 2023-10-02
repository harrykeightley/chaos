(ns chaos.plugins.timer
  (:require [chaos.engine.utils :refer [millis!]]
            [chaos.engine.world :refer [defsys]]
            [chaos.engine.utils :as utils])
  #?(:cljs (:require-macros [chaos.plugins.timer])))

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
  "Creates a system which updates the timer at the supplied `resource-name`.
  
  e.g. `(create-timer-resource-system tick! :timer)`"
  [system-name resource-name]
  `(defsys ~(symbol system-name)
     {:resources [~resource-name]}
     (let [timer# (~resource-name ~'resources)
           next-timer# (update-timer timer#)
           event-command# [:add [:events (:event timer#)] [(:payload timer#)]]
           set-command# [:set [:resources ~resource-name] next-timer#]]
       (when (should-emit? timer#)
         [set-command# event-command#]))))


(defmacro create-timer-system
  "Creates a system which updates all timers at the supplied `component-name`.
  
  e.g. `(create-timer-system tick! :timer)`"
  [system-name component-name]
  `(defsys ~(symbol system-name)
     {:components [~component-name]}
     (let [timers# (->> (map first ~'components) (filter should-emit?))
           event-groups# (->> timers#
                              (map #(mapv (into {} %) [:event :payload]))
                              (group-by first)
                              (#(utils/map-keys % (partial mapv second))))
           group->command# #(vector :add [:events (first %)] (second %))
           event-commands# (mapv group->command# (seq event-groups#))]
       (conj (or event-commands# [])
             [:update [:components ~component-name] update-timer]))))

(macroexpand '(create-timer-system tick! :timer))

