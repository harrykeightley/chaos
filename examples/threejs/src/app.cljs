(ns app
  (:require ["three" :as three]
            [goog.object]
            [snake :refer [add-snake-plugin]]
            [clojure.core.async :as async :refer [go]]
            [chaos.plugins.core :refer [add-core-plugins]]
            [chaos.plugins.timer :as timer]
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
    (set! (.. camera -position -z) 5)
    (println "Cam Z:" (.. camera -position -z))
    (println renderer)

    [[:add [:resources :camera] camera]
     [:add [:resources :scene] (three/Scene.)]
     [:add [:resources :renderer] renderer]]))

(defsys add-cube {:resources [:scene]}
  (let [scene (:scene resources)
        geometry (three/BoxGeometry. 1 1 1)
        material (three/MeshBasicMaterial. #js {:color 0x00ff00})
        cube (three/Mesh. geometry material)]
    (.add scene cube)
    []))

(defsys draw-scene! {:resources [:renderer :scene :camera]
                     :events :tick}
  (println "Drawing...")
  (let [{:keys [:renderer :scene :camera]} resources
        render-scene #(.render renderer scene camera)]
    (.. js/window (requestAnimationFrame render-scene))
    []))

(defsys shout! {}
  (println js/window))

(defsys capture-key-down {}
  (let [raw (atom [])
        add-event (fn [event]
                    (println "Keydown event!")
                    (swap! raw conj event))]
    (.addEventListener js/window "keydown" add-event)
    [[:add [:resources :key-down-events] raw]]))

(defsys handle-key-down {:resources [:key-down-events]}
  (println "KEYS" (:key-down-events resources)))

;; ---- Exit Timer ----
(defsys add-exit-timer {}
  (let [id (chaos/create-entity world)
        timer (timer/create-timer 5000 :exit true)]
    [[:add [:components :timer id] [timer]]]))

(timer/create-timer-system pass-time :timer)

(defsys exit-after-5 {:events :exit}
  (println "Exiting")
  [[:set [:metadata :exit] true]])

(defn ^:dev/after-load run []
  (-> (create-world)
      add-core-plugins
      add-snake-plugin
      (add-system :start-up setup-threejs)
      (add-system :start-up add-cube)
      (add-system-dependency add-cube setup-threejs)
      (add-system :render draw-scene!)

      ;; Exiting after 5 secs
      (add-system :start-up add-exit-timer)
      (add-system :pre-step pass-time)
      (add-system exit-after-5)

      ; (add-system shout!)
      ; (add-system handle-key-down)
      (add-system :start-up capture-key-down)

      (add-stage-dependency :render :update)
      chaos/play))

(defn init []
  (println "Refresh.")
  (run))
