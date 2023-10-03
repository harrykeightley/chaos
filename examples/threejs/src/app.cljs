(ns app
  (:require ["three" :as three]
            [snake :refer [add-snake-plugin]]
            [chaos.plugins.core :refer [add-core-plugins]]
            [chaos.plugins.timer :as timer]
            [chaos.engine.utils :as utils]
            [chaos.engine.world :as chaos :refer [create-world
                                                  defsys
                                                  add-system
                                                  add-system-dependency
                                                  add-stage-dependency]]))

(defsys setup-threejs {}
  (let [w (.-innerWidth js/window)
        h (.-innerHeight js/window)
        aspect (/ w h)
        camera (three/PerspectiveCamera. 75 aspect 0.1 1000)
        renderer (three/WebGLRenderer.)]

    ;; Setup renderer and dom elements.
    (.setSize renderer w h)
    (.. js/document -body (appendChild (.-domElement renderer)))
    ;; Move camera back
    (set! (.. camera -position -z) 16)
    (println renderer)

    [[:add [:resources :camera] camera]
     [:add [:resources :scene] (three/Scene.)]
     [:add [:resources :renderer] renderer]]))

(defn create-cube [size colour]
  (let [geometry (three/BoxGeometry. size size size)
        material (three/MeshBasicMaterial. #js {:color colour})]
    (three/Mesh. geometry material)))

(defsys add-body-cubes
  {:components [:id :position :body]
   :events :tick}
  (let [ids (map first components)
        cubes (map vector ids (repeatedly (partial create-cube 1 0x00ff00)))]
    [[:add [:components :cube] cubes]]))

(defsys add-food-cubes
  {:components [:id :position :food]
   :events :tick}
  (let [ids (map first components)
        cubes (map vector ids (repeatedly (partial create-cube 0.5 0x00ffff)))]
    [[:add [:components :cube] cubes]]))

(defsys align-cubes
  {:resources [:bounds]
   :components [:cube :position]
   :events :tick}
  (let [[rows cols] (:bounds resources)]
    (doseq [[cube [row col]] components]
      (set! (.. cube -position -x) col)
      (set! (.. cube -position -y) (- rows row)))))

(defsys reset-cubes
  {:events :tick
   :components [:id :cube]}
  [[:delete [:components :cube] (mapv first components)]])

(defsys draw-scene!
  {:resources [:renderer :scene :camera]
   :components [:cube]
   :events :tick}
  (let [{:keys [:renderer :scene :camera]} resources]
    (.clear scene)
    (doseq [cube components]
      (.add scene (first cube)))
    (.render renderer scene camera)
    []))

(defsys capture-key-down {}
  (let [raw (atom [])
        add-event (fn [event]
                    (println "Keydown event!")
                    (swap! raw conj event))]
    (.addEventListener js/window "keydown" add-event)
    [[:add [:resources :key-down-events] raw]]))

(def actions {"ArrowDown" [:move :down]
              "ArrowLeft" [:move :left]
              "ArrowRight" [:move :right]
              "ArrowUp" [:move :up]
              "Escape" [:exit nil]})

(defn get-action [event]
  (actions (.-code event)))

(defsys handle-key-down {:resources [:key-down-events]}
  (let [key-down-events (:key-down-events resources)
        valid (filter get-action @key-down-events)
        raw-actions (map get-action valid)
        actions (utils/map-keys (group-by first raw-actions) last)
        events (mapv (fn [[event payload]]
                       [:add [:events event] payload])
                     actions)
        key-down-events (reset! (:key-down-events resources) [])]
    events))

(defsys handle-move {:events :move}
  [[:set [:resources :direction] (last events)]])

(defsys handle-exit {:events :exit}
  [[:set [:metadata :exit] true]])

(defn run-world [world]
  (let [world (chaos/step world)]
    (if (chaos/finished? world)
      (chaos/apply-stage world :tear-down)
      (js/requestAnimationFrame #(run-world world)))))

(defn ^:dev/after-load run []
  (-> (create-world)
      add-core-plugins
      add-snake-plugin
      (add-system :start-up setup-threejs)
      (add-system :pre-render reset-cubes)
      (add-system :pre-render add-body-cubes)
      (add-system :pre-render add-food-cubes)
      (add-system :pre-render align-cubes)
      (add-system-dependency add-body-cubes reset-cubes)
      (add-system-dependency add-food-cubes reset-cubes)
      (add-system-dependency align-cubes add-body-cubes)
      (add-system-dependency align-cubes add-food-cubes)
      (add-system :render draw-scene!)

      ;; Input handling
      (add-system :start-up capture-key-down)
      (add-system :pre-step handle-key-down)
      (add-system handle-move)
      (add-system handle-exit)

      (add-stage-dependency :render :update)
      (add-stage-dependency :render :pre-render)
      (chaos/apply-stage :start-up)
      run-world))

(defn init []
  (println "Refresh.")
  (run))
