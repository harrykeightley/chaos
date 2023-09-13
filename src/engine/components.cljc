(ns engine.components)

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
    (cond (has-id? store id) (assoc-in store [:dense (sparse id)] component)
          (> id max-id) store
          :else (let [sparse (assoc sparse id n)
                      dense (assoc dense n id)
                      components (assoc components n component)
                      n (inc n)]
                  (merge store {:keys [sparse dense components n]}))))

  (get-component [_ id]
    (->> (get sparse id) (get components)))

  (get-components [_] (take n components))

  (get-items [_] (map vector dense components))

  (unset [store id]
    (cond (not (has-id? store id)) store
          (= n 1) (create-component-store)
          :else (let [last-index (- n 1)
                      old-id (get sparse id)
                      replacement-id (get dense last-index)
                      replacement (get components last-index)
                      dense (assoc dense old-id replacement-id)
                      sparse (assoc sparse replacement-id old-id)
                      components (assoc components old-id replacement)
                      n (dec n)]
                  (merge store {:keys [sparse dense components n]})))))

(defn create-component-store
  "Default constructor for a new ComponentStore."
  ([]
   (create-component-store
    #?(:cljs 2147483647 ;; 2 ** 32
       :default Integer/MAX_VALUE)))
  ([size]
   (let [empty-array (vec (repeat size nil))]
     (->ComponentStore size 0 empty-array empty-array empty-array))))



