(ns ddev.api
  "Programatic API for DDEV.
  Code here should not require any user interaction."
  (:require-macros [ddev.async :as a])
  (:require [ddev.core :as ddev]
            [cljs.reader :refer [read-string]]
            [os :as os]
            [path :as p]
            [ddev.fs :as fs :refer [slurp spit]]))

(defn dispatch [action]
  (let [{:keys [type opts]} action
        workspace (p/join (os/homedir) ".ddev")
        last-action (p/join workspace "last-action")]
    (if (= type :redo-last-action)
      (a/if (fs/file? last-action)
        (a/let [action (slurp last-action)]
          (dispatch (read-string action)))
        (println "no previous action found"))
      (a/do
        (fs/mkdir workspace)
        (spit last-action (pr-str action))
        (case type
          :checkout (ddev/checkout opts)
          :deploy (ddev/deploy)
          :hero-pull-request (ddev/hero-pull-request opts)
          :run-maven-build (ddev/run-maven-build opts)
          :clean-workspace (ddev/clean-workspace opts))))))

