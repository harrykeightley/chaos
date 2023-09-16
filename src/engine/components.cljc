(ns engine.components
  (:require [engine.store :as es :refer [Store]]
            [engine.utils :refer [mapper manip-map]]))

(defprotocol SparseSet
  (has-id? [s id]
    "Returns true iff the entity id is contained within the set")
  (get-component [s id]
    "Get the component with the supplied id within the set.")
  (get-components [s]
    "Gets all components associated with the supplied component type")
  (get-items [s]
    "Returns all <id, component> tuples.")
  (insert [s id component]
    "Removes the component associated with the given `id` in the set, and returns 
    the resulting set.
    
    Does nothing if `id` is not in the set.")
  (unset [s id]
    "Removes the component associated with the given `id` in the set, and returns 
    the resulting set.
    
    Does nothing if `id` is not in the set."))

(declare create-component-store)

(defrecord ComponentStore [max-id n sparse dense components]
  SparseSet
  (has-id?
    [_ id]
    (if (> id max-id)
      false
      (= id (get dense (get sparse id)))))

  (insert [store id component]
    (cond (has-id? store id) (assoc-in store [:components (sparse id)] component)
          (> id max-id) store
          :else (let [sparse (assoc sparse id n)
                      dense (assoc dense n id)
                      components (assoc components n component)
                      n (inc n)]
                  (merge store (mapper sparse dense components n)))))

  (get-component [_ id]
    (->> (get sparse id) (get components)))

  (get-components [_] (take n components))

  (get-items [_] (take n (map vector dense components)))

  (unset [store id]
    (cond (not (has-id? store id)) store
          ;; Probably inneficint
          (= n 1) (create-component-store max-id)
          :else (let [last-index (- n 1)
                      old-id (get sparse id)
                      replacement-id (get dense last-index)
                      replacement (get components last-index)
                      dense (assoc dense old-id replacement-id)
                      sparse (assoc sparse replacement-id old-id)
                      components (assoc components old-id replacement)
                      n (dec n)]
                  (merge store (mapper sparse dense components n)))))

  Store
  ;; Note: Expects values to be (id, component) pairs or for path to be 
  ;; a sequence of ids and values to be a sequence of matching components.
  (adds [store path values]
    (if (empty? path)
      (let [reducer (fn [store pair]
                      (let [[id component] pair]
                        (if (has-id? store id) store
                            (insert store id component))))]
        (reduce reducer store values))
      (es/adds store nil (map vector path values))))

  ;; Resets the store if path is empty, else deletes all remaining ids on path.
  (deletes [store path]
    (if (empty? path)
      (create-component-store)
      (es/deletes store nil path)))

  ;; Expects values to be a sequence of ids
  (deletes [store _ values]
    (reduce unset store values))

  ;; Applies `f` to the all ids at `path`, or all components if path is nil.
  (updates [store path f]
    (if (empty? path)
      (update-in store [:components] (partial map f))
      (let [reducer (fn [store id]
                      (insert store id (f (get-component store id))))
            ids (filter (partial has-id? store) path)]
        (reduce reducer store ids))))

;; Note: Expects values to be (id, component) pairs or for path to be 
  ;; a sequence of ids and values to be a sequence of matching components.
  (sets [store path values]
    (if (empty? path)
      (let [reducer (fn [store pair] (apply insert store pair))]
        (reduce reducer store values))
      (es/sets store nil (map vector path values))))

  Object
  (toString [store]
    (manip-map store [:sparse :dense :components] #(take n %))))

(defn create-component-store
  "Default constructor for a new ComponentStore."
  ([]
   (create-component-store 65536))
  ([size]
   (let [empty-array (vec (repeat size 0))]
     (->ComponentStore size 0 empty-array empty-array empty-array))))



