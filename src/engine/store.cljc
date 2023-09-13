(ns engine.store)

(defprotocol Store
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
