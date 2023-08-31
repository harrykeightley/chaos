(ns engine.core
  (:require [clojure.main])
  (:import [org.lwjgl.glfw Callbacks GLFW GLFWErrorCallback GLFWKeyCallbackI]
           [org.lwjgl Version]
           [org.lwjgl.opengl GL GL33]
           [org.lwjgl.system MemoryStack]))

; forward references
(declare create-window main-loop draw clean-up)

(defn -main [& args]
  (println (str "Hello LWJGL " (Version/getVersion) "!"))

  (let [window (create-window 300 300 "hi")]
    (main-loop window)
    (clean-up window)))

(defn clean-up [window]
  ; Free the window callbacks and destroy the window
  (Callbacks/glfwFreeCallbacks window)
  (GLFW/glfwDestroyWindow window)

  ; Terminate GLFW and free the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil) (.free)))

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

    ; Setup a key callback. It will be called every time a key is pressed, repeated or released.
    (GLFW/glfwSetKeyCallback window (reify GLFWKeyCallbackI
                                      (invoke [this window key scancode action mods]
                                        (when (and (= key GLFW/GLFW_KEY_ESCAPE)
                                                   (= action GLFW/GLFW_RELEASE))
                                        ; We will detect this in the rendering loop
                                          (GLFW/glfwSetWindowShouldClose window true))
                                        (println (str "Pressed: " key "-" action)))))

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

(defn main-loop [window]

  ; This line is critical for LWJGL's interoperation with GLFW's
  ; OpenGL context, or any context that is managed externally.
  ; LWJGL detects the context that is current in the current thread,
  ; creates the GLCapabilities instance and makes the OpenGL
  ; bindings available for use.
  (GL/createCapabilities)

  ; Run the rendering loop until the user has attempted to close
  ; the window or has pressed the ESCAPE key.
  (while (not (GLFW/glfwWindowShouldClose window))
    (draw window)))

(defn draw [window]
  (GL33/glClearColor 1.0 0.0 0.0 0.0)

  ; clear the framebuffer
  (GL33/glClear (bit-or GL33/GL_COLOR_BUFFER_BIT GL33/GL_DEPTH_BUFFER_BIT))

  ; swap the color buffers
  (GLFW/glfwSwapBuffers window)

  ; Poll for window events. The key callback above will only be
  ; invoked during this call.
  (GLFW/glfwPollEvents))
