(ns snake
  (:require [chaos.engine.world :as ew :refer [defsys generate-ids]]
            [chaos.plugins.core :refer [add-core-plugins]]
            [chaos.plugins.timer :as timer]
            [chaos.engine.components :as ec]
            [clojure.string :as str]))

;; ----- Helpers
(defn clear-term []
  (print (str (char 27) "[2J")))

(defn replace-cursor []
  (print (str (char 27) "[;H")))

;; ----- Begin game logic
(def bounds [10 10])
(def directions {:up [-1 0] :down [1 0] :left [0 -1] :right [0 1]})

(defsys add-timer "" {}
  [[:add [:resources :timer] (timer/create-timer 1000 :tick nil true)]])

(timer/create-timer-resource-system tick! :timer)

(defsys add-bounds {}
  [[:add [:resources :bounds] bounds]])

(defsys add-snake "Adds the initial snake components" {}
  (let [length 2
        ids (generate-ids world length)]
    [[:add [:resources :length] 2]
     [:add [:resources :head] [2 1]]
     [:add [:resources :direction] :down]
     [:add [:components :position] (map vector ids [[1 1] [2 1]])]
     ;; Reversing ids so that tail positions have higher indicies
     [:add [:components :body] (map vector (reverse ids) (range 1 (+ length 1)))]]))

(defsys add-food {}
  (let [id (ew/create-entity world)]
    [[:add [:components :food id] [:food]]
     [:add [:components :position id] [[8 1]]]]))

(defsys move-head "Moves the snake head"
  {:resources [:head :direction :bounds]
   :events :tick}
  (let [{:keys [head direction bounds]} resources
        head (mapv + (directions direction) head)
        head (mapv mod head bounds)
        head-id (ew/create-entity world)]
    [[:set [:resources :head] head]
     [:set [:components :position head-id] [head]]
     [:set [:components :body head-id] [0]]
     [:update [:components :body] inc]]))

(defsys log-positions
  {:resources [:head]
   :components [:id :position]
   :events :tick}
  (println "Positions:" components "Head:" (:head resources)))

(defsys log-body
  {:resources [:length]
   :components [:body]
   :events :tick}
  (println "Length:" resources "Body:" components))

(defsys log-food
  {:components [:position :food]
   :events :tick}
  (println "Food Components:" components))

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

(defsys food-collision "Detects if the head comes into contact with food."
  {:resources [:head]
   :components [:id :position :food]
   :events :tick}
  (let [head (:head resources)
        collisions (filter #(= (second %) head) components)
        payloads (map first collisions)]
    (when (not-empty payloads)
      [[:add [:events :dinner] payloads]])))

(defsys eat
  {:resources [:length]
   :events :dinner}
  (println "Eating")
  (let [ids events]
    [[:update [:resources :length] (partial + (count ids))]
     [:delete [:components :position] ids]
     [:delete [:components :food] ids]
     [:delete [:components :id] ids]]))

(defsys spawn-new-food
  {:resources [:bounds]
   :components [:position :body]
   :events :dinner}
  (let [[rows cols] (:bounds resources)
        snake-positions (set (map first components))
        empty-positions (for [row (range rows)
                              col (range cols)
                              :let [position [row col]]
                              :when (not (snake-positions position))]
                          position)
        food-position (rand-nth empty-positions)
        id (ew/create-entity world)]
    [[:add [:components :food id] [:food]]
     [:add [:components :position id] [food-position]]]))

(defsys accelerate {:events :tick}
  [[:update [:resource :timer] #(update % :ms dec)]])

(defsys display-game
  {:resources [:length :bounds]
   :events :tick
   :components [:position :id]}
  (let [[rows cols] (:bounds resources)
        ids (into {} (map vec components))
        body-store (get-in world [:component-stores :body])
        food-store (get-in world [:component-stores :food])
        display-char (fn [position]
                       (let [id (get ids position)]
                         (cond (nil? id) \space
                               (ec/has-id? body-store id) \@
                               (ec/has-id? food-store id) \.
                               :else \space)))]
    (replace-cursor)
    (println (str/join (repeat (+ 2 cols) \#)))
    (doseq [row (range rows)]
      (print \#)
      (doseq [col (range cols)]
        (print (display-char [row col])))
      (print \#)
      (println))
    (println (str/join (repeat (+ 2 cols) \#)))
    (flush)))

;; Just grouping this into a plugin to be used in other examples.
(defn add-snake-plugin [world]
  (-> world
      (ew/add-system :start-up add-timer)
      (ew/add-system :start-up add-snake)
      (ew/add-system :start-up add-bounds)
      (ew/add-system :start-up add-food)
      (ew/add-system-dependency add-food add-snake)
      (ew/add-system :pre-step tick!)
      (ew/add-system eat)
      (ew/add-system accelerate)
      (ew/add-system food-collision)
      (ew/add-system spawn-new-food)
      (ew/add-system-dependency spawn-new-food eat)
      (ew/add-systems [move-head move-tail])
      ; (ew/add-system-dependency move-head tick!)
      (ew/add-system-dependency food-collision move-head)
      (ew/add-system-dependency eat food-collision)
      (ew/add-system-dependency move-tail eat)))

(defn -main [& args]
  (-> (ew/create-world)
      add-core-plugins
      add-snake-plugin
      (ew/add-system :display display-game)
      (ew/add-system :log log-food)
      (ew/add-system :log log-body)
      (ew/add-system :log log-positions)
      (ew/add-stage-dependency :log :display)
      (ew/add-system-dependency log-body log-food)
      (ew/add-system-dependency log-positions log-body)
      ew/play))


