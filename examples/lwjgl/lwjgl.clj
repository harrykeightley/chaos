(ns lwjgl
  (:require [clojure.main]
            [snake :refer [add-snake-plugin]]
            [chaos.engine.utils :as utils]
            [chaos.engine.world :as ew :refer [create-world add-system add-system-dependency defsys play]])
  (:import [org.lwjgl.glfw Callbacks GLFW GLFWErrorCallback GLFWKeyCallbackI]
           [org.lwjgl Version]
           [org.lwjgl.opengl GL GL33]
           [org.lwjgl.system MemoryStack]))

(defsys hello-world {}
  (println (str "Hello LWJGL " (Version/getVersion) "!")))

(defsys create-GL-capabilities {}
  ; This line is critical for LWJGL's interoperation with GLFW's
  ; OpenGL context, or any context that is managed externally.
  ; LWJGL detects the context that is current in the current thread,
  ; creates the GLCapabilities instance and makes the OpenGL
  ; bindings available for use.
  (GL/createCapabilities)
  nil)

(defsys clean-up {:resources [:window]}
  (let [window (:window resources)]
    ; Free the window callbacks and destroy the window
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwDestroyWindow window)

    ; Terminate GLFW and free the error callback
    (GLFW/glfwTerminate)
    (-> (GLFW/glfwSetErrorCallback nil) (.free))))

(defn create-window [width height title]
  ; Setup an error callback. The default implementation
  ; will print the error message in System.err.
  (-> (GLFWErrorCallback/createPrint System/err) (.set))

  ; Initialize GLFW. Most GLFW functions will not work before doing this.
  (when (not (GLFW/glfwInit))
    (throw (IllegalStateException. "Unable to initialize GLFW")))

  ; Configure GLFW
  (GLFW/glfwDefaultWindowHints)                             ; optional, the current window hints are already the default
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)   ; the window will stay hidden after creation
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)  ; the window will be resizable

  ; Create the window
  (let [window (GLFW/glfwCreateWindow width height title 0 0)]
    (when (zero? window)
      (throw (RuntimeException. "Failed to create the GLFW window")))

; Get the thread stack and push a new frame
    (let [stack (MemoryStack/stackPush)
          p-width (.mallocInt stack 1)
          p-height (.mallocInt stack 1)]

      ; Get the window size passed to glfwCreateWindow
      (GLFW/glfwGetWindowSize ^long window p-width p-height)
      (let [vidmode (-> (GLFW/glfwGetPrimaryMonitor)          ; Get the resolution of the primary monitor
                        (GLFW/glfwGetVideoMode))
            xpos (/ (- (.width vidmode)
                       (.get p-width 0))
                    2)
            ypos (/ (- (.height vidmode)
                       (.get p-height 0))
                    2)]
        (GLFW/glfwSetWindowPos window xpos ypos))             ; Center the window
      (MemoryStack/stackPop)                                  ; pop stack frame
      )

    (GLFW/glfwMakeContextCurrent window)
    ; Enable v-sync
    (GLFW/glfwSwapInterval 1)
    (GLFW/glfwShowWindow window)
    window))

(defsys init-window {}
  (let [width 300
        height 300
        window (create-window width height "Snake")]
    [[:add [:resources :window] window]
     [:add [:resources :window-size [width height]]]]))

(defsys detect-exit {:resources [:window]}
  (let [window (:window resources)]
    (when (GLFW/glfwWindowShouldClose window)
      [[:set [:metadata :exit] true]])))

(defsys create-input-manager {}
  [[:add [:resources :raw-key-events] (atom [])]])

(defsys bind-keys {:resources [:window :raw-key-events]}
  (let [window (:window resources)
        events (:raw-key-events resources)]
    ; Setup a key callback. It will be called every time a key is pressed, repeated or released.
    (GLFW/glfwSetKeyCallback
     window
     (reify GLFWKeyCallbackI
       (invoke [this window key scancode action mods]
         ;; Save the key press event to be processed later.
         (swap! events conj (utils/mapper key action))
         (when (and (= key GLFW/GLFW_KEY_ESCAPE)
                    (= action GLFW/GLFW_RELEASE))
           ; We will detect this in the rendering loop
           (GLFW/glfwSetWindowShouldClose window true))
         (println (str "Pressed: " key "-" action)))))))

(defsys clear-screen {}
  (GL33/glClearColor 1.0 0.0 0.0 0.0)

  ; clear the framebuffer
  (GL33/glClear (bit-or GL33/GL_COLOR_BUFFER_BIT GL33/GL_DEPTH_BUFFER_BIT)))

(defsys draw {:resources [:window]}
  ; swap the color buffers
  (GLFW/glfwSwapBuffers (:window resources))

  ; Poll for window events. The key callback above will only be
  ; invoked during this call.
  (GLFW/glfwPollEvents))

(defn add-lwgjl-plugin [world]
  (-> world
      (assoc-in [:metadata :mc-stages] #{:start-up :pre-render :render :post-render :tear-down})
      (add-system :start-up hello-world)
      (add-system :start-up init-window)
      (add-system :start-up create-GL-capabilities)
      (add-system-dependency create-GL-capabilities init-window)
      (add-system :start-up create-input-manager)
      (add-system :start-up bind-keys)
      (add-system-dependency bind-keys init-window)
      (add-system-dependency bind-keys create-input-manager)
      (add-system :pre-render clear-screen)
      (add-system :post-render draw)
      (add-system :post-step detect-exit)
      (add-system :tear-down clean-up)))

(defn -main [& args]
  (-> (create-world)
      ;; Force the supplied stages run on the main thread
      add-lwgjl-plugin
      play))

