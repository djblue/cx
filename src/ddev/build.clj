(ns ddev.build
  (:require [clojure.java.io :as io]
            [cljs.build.api :as api]
            [clojure.data.json :as json]
            [clojure.java.shell :refer [sh]]))

(defn clean! []
  (.delete (io/file "target"))
  (.mkdirs (io/file "target/deploy")))

(defn bundle [file]
  (:out (sh "node_modules/.bin/browserify" "--node" file)))

(defn write-package! []
  (->> (bundle "target/main.js")
       (str "#!/usr/bin/env node\n")
       (spit "target/deploy/bin.js"))
  (.setExecutable (io/file "target/deploy/bin.js") true)
  (->> {:name "cx" :version "1.0.0" :bin {:cx "./bin.js"}}
       json/write-str
       (spit "target/deploy/package.json")))

(def cljs-config
  {:target :nodejs
   :main 'ddev.main
   :output-to "target/main.js"
   :output-dir "target/build"
   :watch-fn write-package!
   :optimizations :simple})

(defn -main [& args]
  (clean!)
  (case (first args)
    "watch" (api/watch "src" cljs-config)
    (do (api/build "src" cljs-config)
        (write-package!)))
  (shutdown-agents))

