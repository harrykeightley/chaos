(ns snake
  (:require [chaos.engine.world :as ew :refer [defsys generate-ids]]
            [chaos.plugins.core :refer [add-core-plugins]]
            [chaos.plugins.timer :as timer]
            [chaos.engine.components :refer [get-components]]
            [chaos.engine.components :as ec]))

;;----- Helpers
(defn clear-term []
  (print (str (char 27) "[2J")))

(defn replace-cursor []
  (print (str (char 27) "[;H")))

;; ----- Begin game logic
(def directions {:up [-1 0] :down [1 0] :left [0 -1] :right [0 1]})

(defsys add-timer "" {}
  [[:add [:resources :timer] (timer/create-timer 1000 :tick nil true)]])

(timer/create-timer-resource-system tick! :timer)

(defsys shout {:events :tick}
  (println "TICK"))

(defsys add-snake "Adds the initial snake components" {}
  (let [length 2
        ids (generate-ids world length)]
    [[:add [:resources :length] 2]
     [:add [:resources :head] [2 1]]
     [:add [:resources :direction] :down]
     [:add [:components :position] (map vector ids [[1 1] [2 1]])]
     ;; Reversing ids so that tail positions have higher indicies
     [:add [:components :body] (map vector (reverse ids) (range 1 (+ length 1)))]]))

(defsys move-head "Moves the snake head"
  {:resources [:head :direction]
   :events :tick}
  (let [{:keys [head direction]} resources
        head (map + (directions direction) head)
        head-id (ew/create-entity world)]
    [[:set [:resources :head] head]
     [:set [:components :position head-id] [head]]
     [:set [:components :body head-id] [0]]
     [:update [:components :body] inc]]))

(defsys log-body
  {:resources [:length]
   :components [:body]
   :events :tick}
  (println "Length:" resources "Body:" components))

(defsys move-tail "Moves the snake tail"
  {:resources [:length]
   :components [:id :body]
   :events :tick}
  (let [length (:length resources)
        to-remove (->> (filter #(< length (second %)) components)
                       (map first))]
    [[:delete [:components :body] to-remove]
     [:delete [:components :position] to-remove]
     [:delete [:components :id] to-remove]])) ;; TODO This is ridiculous.

(defsys display-game ""
  {:resources [:length]
   :events :tick
   :components [:position]}
  (let [positions (set (map first components))
        display-char (fn [position]
                       (if (positions position)
                         \#
                         \space))]
    (replace-cursor)
    (doseq [row (range 10)]
      (doseq [col (range 10)]
        (print (display-char [row col])))
      (println))
    (flush)))

(defn -main [& args]
  (-> (ew/create-world)
      add-core-plugins
      (ew/add-system :start-up add-timer)
      (ew/add-system :start-up add-snake)
      (ew/add-system :update tick!) ;; explicitly add tick! to :update stage.
      (ew/add-system shout) ;; implicitly add shout to :update stage.
      (ew/add-system-dependency shout tick!)
      (ew/add-systems [move-head move-tail])
      (ew/add-system-dependency move-head tick!)
      (ew/add-system-dependency move-tail move-head)
      (ew/add-system :display display-game)
      ; (ew/add-system :display log-body)
      ; (ew/add-system-dependency log-body display-game)
      ew/play))


