(ns engine.utils)

(defn map-keys [m k f]
  (let [m (select-keys m k)]
    (zipmap (keys m) (map f (vals m)))))

(defn manip-map [m k f]
  (merge m (map-keys m k f)))

(comment (manip-map {:a 3 :b 5} [:b] (partial * 2)))

(defmacro mapper [& args]
  `(zipmap ~(mapv (comp keyword name) args) [~@args]))

(defn millis! []
  #?(:cljs (-> (.. js/Date now) (/ 1000) (.floor js/Math))
     :default (inst-ms (java.time.Instant/now))))

(comment (let [a 1 b 2 c 3]
           (macroexpand
            (mapper a b c))))

