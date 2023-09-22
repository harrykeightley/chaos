(ns chaos.plugins.core
  (:require [chaos.engine.world :refer [defsys add-system]]))

(defsys reset-events "Resets all events after a world step"
  {}
  [[:set [:events] {}]])

(defn add-core-plugins [world]
  (-> world
      (add-system :post-step reset-events)))
