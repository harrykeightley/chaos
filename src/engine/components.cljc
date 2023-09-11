(ns engine.components
  (:refer-clojure :exclude [count contains?]))

(defprotocol DataStore
  "A protocol for defining common update patterns on data."
  (add [store path values]
    "Adds all values to the given path in the data store.")
  (delete
    [store path]
    "Deletes the entire path within the store."
    [store path values]
    "Performs set difference on the path with values.")
  (update-values [store path values]
    "Merges all values with those currently found at the path")
  (set-values
    [store path values]
    "Replaces all values at the path with `values`")
  (set-value
    [store path value] "Update the value at the path"))

(defprotocol SparseSet
  (count [s]
    "Returns the number of entities contained within the set.")
  (components [s]
    "Returns the component types contained within the set.")
  (contains?
    [s id]
    "Returns true iff the entity id is contained within the set"
    [s component id]
    "Returns true iff the component and entity id are contained within the set")
  (add-component [s component constructor]
    "Adds a component type to the set. 
    
    Constructor is a 0-ary function which is used to initialise the 
    corresponding component when other managed components are added to the set.")
  (get-value [s component id]
    "Get the component with the supplied id within the set.")
  (get-values [s component]
    "Gets all components associated with the supplied component type")
  (remove-component [s component]
    "Removes an entire component type from the set."))

(defrecord ComponentStore [max-id n sparse dense components]
  SparseSet
  (count [store] n)

  (components [store] (keys components))

  (contains?
    ([store id]
     (cond (> id max-id) false
           :else (= id (get dense (get sparse id)))))
    ([store component id]
     (and (contains? store id)
          (clojure.core/contains? components component))))

  (add-component [store component constructor]
    ;; TODO: if there are already other components, update all to be correct length. 
    (assoc store component {:constructor constructor :values []}))

  (remove-component [store component]
    (let [components (dissoc components component)]
      (assoc store :components components)))

  (get-value [store component id]
    (when (contains? store component id)
      (let [values (-> components
                       (get component)
                       :values)
            index (get sparse id)]
        (get values index))))

  (get-values [store component id]
    (when (contains? store component id)
      (-> components
          (get component)
          :values)))

  ;; Todo
  DataStore
  (add [store path values])
  (delete
    ([store path])
    ([store path values]))
  (set-value [store path value])
  (set-values [store path values]))

(defn create-component
  "Default constructor for a new ComponentStore."
  [& components]
  (->ComponentStore Integer/MAX_VALUE 0 [] [] components))


