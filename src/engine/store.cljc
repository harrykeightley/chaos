(ns engine.store)

(defprotocol Store
  "A protocol for defining common update patterns on data."
  (adds [store path values]
    "Adds all values to the given path in the data store.")
  (deletes
    [store path] [store path values]
    "Performs set difference on the path with values iff supplied.
    Otherwise, removes the path entirely.")
  (updates [store path f]
    "Akin to `update-in`, calls `f` on the value found at the given path and 
    returns the result.")
  (sets
    [store path values]
    "Replaces all values at the path with `values`"))


