(ns core
  (:require [clojure.main]
            [snake :refer [add-snake-plugin]]
            [lwjgl :refer [add-lwgjl-plugin]]
            [chaos.plugins.core :refer [add-core-plugins]]
            [chaos.engine.world :as ew :refer [create-world add-system add-system-dependency defsys play]])
  (:import [org.lwjgl.glfw Callbacks GLFW GLFWErrorCallback GLFWKeyCallbackI]
           [org.lwjgl Version]
           [org.lwjgl.opengl GL GL11 GL33]
           [org.lwjgl.system MemoryStack]))

(def actions
  {GLFW/GLFW_KEY_DOWN :down
   GLFW/GLFW_KEY_UP :up
   GLFW/GLFW_KEY_LEFT :left
   GLFW/GLFW_KEY_RIGHT :right})

(defn percent->gl [x]
  (-> (* 2 x)
      dec
      float))

(defn pos->gl [dimensions position]
  (let [ps (map / dimensions position)]
    (mapv percent->gl ps)))

(defn pos->gl-bounds [dimensions position]
  (let [point-a (pos->gl dimensions position)
        point-b (pos->gl dimensions (mapv inc position))]
    (flatten [point-a point-b])))

; (defn draw-rect [dimensions position [r g b]]
;   (GL33/glColor3f r g b)
;   (apply GL33/gl (pos->gl-bounds dimensions position)))

(defn -main [& args]
  (-> (create-world)
      add-core-plugins
      add-lwgjl-plugin
      add-snake-plugin
      ;; Force the supplied stages run on 
      play))

