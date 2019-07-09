(require '[lumo.build.api :as lumo])

(lumo/build "src"
  {:target :nodejs
   :main 'ddev.main
   :output-to "target/main.js"
   :output-dir "target/lumo"
   :optimizations :simple})
