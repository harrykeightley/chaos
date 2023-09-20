(ns chaos.engine.world)

(defn resolve-params
  "Takes parameters to a def* macro, allowing an optional docstring by sorting
   out which parameter is which.
   Returns the params, body, and docstring it found."
  [args]
  (if (string? (first args))
    [(second args) (drop 2 args) (first args)]
    [(first args) (rest args)]))

(defmacro defsys
  "Creates a macro for specifying systems based on their dependencies, and 
  creates bindings for the all bindings passed in."
  {:clj-kondo/ignore [:unused-binding]}
  [system-name & args]
  (let [[bindings body doc] (resolve-params args)]
    `(defn ~(symbol system-name) {:doc ~doc} [world#]
       (let [~'world world#
             ~'components []
             ~'resources {}
             ~'events []]
         ;; If events requested but those events are empty, return nil
         (if (and ~(:events bindings) (empty? ~'events))
           []
           ;; Return [] if the body of the system returns nil.
           (or (do ~@body) []))))))
