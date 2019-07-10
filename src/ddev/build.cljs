(ns ddev.build
  (:require browserify
            [fs :as fs]
            mkdirp
            rimraf
            [lumo.build.api :as lumo]))

(defn clean! []
  (rimraf/sync "target")
  (mkdirp/sync "target/deploy"))

(defn bundle [file done]
  (let [out (atom "")
        e (.bundle (browserify file #js {:node true}))]
    (.on e "data" #(swap! out str %))
    (.on e "end" #(done @out))))

(defn write-package! [js]
  (fs/writeFileSync "target/deploy/bin.js" (str "#!/usr/bin/env node\n" js))
  (fs/chmodSync "target/deploy/bin.js" 0755)
  (->> {:name "cx" :version "1.0.0" :bin {:cx "./bin.js"}}
       clj->js
       js/JSON.stringify
       (fs/writeFileSync "target/deploy/package.json")))

(defn -main []
  (clean!)
  (lumo/build
   "src"
   {:target :nodejs
    :main 'ddev.main
    :output-to "target/main.js"
    :output-dir "target/lumo"
    :optimizations :simple})
  (bundle "target/main.js" write-package!))


