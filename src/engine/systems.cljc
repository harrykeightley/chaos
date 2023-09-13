(ns engine.systems)

;; ----- Systems -----
(defn- make-bindings [bindings body]
  `(let ~(-> bindings seq flatten vec) ~body))

(defn extract-info [world bindings]
  (let []) 
)

;; TODO 
(defmacro defsys
  "Creates a macro for specifying systems based on their dependencies, and 
  creates bindings for the all bindings passed in."
  {:clj-kondo/ignore [:unresolved-symbol]}
  [system-name doc bindings body]
  `(defn ~system-name ~doc [~'world]
     ;; TODO
     ;; 1. Pull all required information from bindings
     ;; 2. Create the bindings
     ;; 3. Execute the function body
     ~(make-bindings bindings body)))


(defsys test-system "somedoc" {a 3} (println a))

(macroexpand '(defsys a
                "doc"
                {c 3}
                (println c)))
; (clojure.repl/source fn)
; (clojure.repl/source defn)

