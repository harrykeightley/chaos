(ns chaos.engine.utils)

(defn map-keys
  "Applies the function `f` to the value of keys `k` within `m` and returns the 
  resulting map."
  [m k f]
  (let [m (select-keys m k)]
    (zipmap (keys m) (map f (vals m)))))

(defn manip-map
  "Really just `map-keys` but also returns the non-modified keys."
  [m k f]
  (merge m (map-keys m k f)))

(defmacro mapper
  "Turns its passed arguments into a map of those keywordized arguments to their 
  values."
  [& args]
  `(zipmap ~(mapv (comp keyword name) args) [~@args]))

(defn millis!
  "Returns the ms passed since Jan 1 1970."
  []
  #?(:cljs (-> (.. js/Date now) (/ 1000) (.floor js/Math))
     :default (inst-ms (java.time.Instant/now))))

(defn resolve-params
  "Takes parameters to a def* macro, allowing an optional docstring by sorting
   out which parameter is which.
   Returns the params, body, and docstring it found."
  [args]
  (if (string? (first args))
    [(second args) (drop 2 args) (first args)]
    [(first args) (rest args)]))
