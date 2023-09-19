(ns snake
  (:require [engine.world :as ew :refer [defsys]]
            [engine.utils :refer [millis!]]))

;;----- Helpers
(defrecord Stopwatch [ms last-time event loops?])

(defn create-timer
  ([ms event loops?] (->Stopwatch ms (millis!) event loops?)))

(defn generate-ids [world n]
  (take n (iterate (partial ew/create-entity world) 0)))

(defn clear-term []
  (print (str (char 27) "[2J")))

(defn replace-cursor []
  (print (str (char 27) "[;H")))

;; ----- Begin game logic
(defrecord Position [x y])
(def directions {:up [-1 0] :down [1 0] :left [0 -1] :right [0 1]})

(defsys add-timer "" {}
  [[:add [:resources :timer] (create-timer 1000 :tick true)]])

;; Note: I should add something like this to the library
(defsys tick! "Updates the timer"
  {:resources [:timer]}
  (let [timer (:timer resources)
        now (millis!)
        ms-diff (abs (- now (:last-time timer)))
        next-timer (assoc timer :last-time now)
        event-command [:add [:events (:event timer)] [nil]]
        set-command [:set [:resources :timer] next-timer]]
    (when (> ms-diff (:ms timer))
      [set-command event-command])))

(defsys shout "Shouts every tick"
  {:events :tick}
  (println "TICK"))

(defsys reset-events "Resets events after a step" {}
  [[:set [:events] {}]])

(defsys add-snake "Adds the initial snake components" {}
  (let [length 2
        ids (generate-ids world length)]
    [[:add [:resources :length] 2]
     [:add [:resources :head] [2 1]]
     [:add [:resources :direction] :down]
     [:add [:components :position] (map vector ids [[1 1] [2 1]])]
     ;; Reversing ids so that tail positions have higher indicies
     [:add [:components :body] (map vector (reverse ids) (range 1 (+ 1 length)))]]))

(defsys move-head "Moves the snake head"
  {:resources [:head :direction]
   :events :tick}
  (let [{:keys [head direction]} resources
        head (map + (directions direction) head)
        head-id (ew/create-entity world)]
    [[:set [:resources :head] head]
     [:set [:components :position head-id] [head]]
     [:update [:components :body] dec]]))

(defsys move-tail "Moves the snake tail"
  {:resources [:length]
   :components [:id :body]
   :events :tick}
  (let [length (:length resources)
        to-remove (->> (filter #(<= length (second %)) components)
                       (map first))]
    [[:delete [:components :body] to-remove]
     [:delete [:components :position] to-remove]
     [:delete [:components :id] to-remove]])) ;; TODO This is ridiculous.

(defsys display-game ""
  {:resources [:length]
   :events :tick
   :components [:position]}
  (let [length (get resources :length 2)
        body-store (get-in world [:components :body])
        display-char (fn [position]
                       (if ((set components) position)
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
      (ew/add-system :start-up add-timer)
      (ew/add-system :start-up add-snake)
      (ew/add-system :update tick!)
      (ew/add-system :update shout)
      (ew/add-system-dependency shout tick!)
      (ew/add-system :post-step reset-events)
      (ew/add-systems [move-head move-tail])
      (ew/add-system-dependency move-tail move-head)
      (ew/add-system :display display-game)
      (ew/play)))


