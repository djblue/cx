(ns ddev.shell
  (:require [cross-spawn :as spawn]
            [ddev.fs :as fs]))

(defn -js->clj+
  "For cases when built-in js->clj doesn't work. Source: https://stackoverflow.com/a/32583549/4839573"
  [x]
  (into {} (for [k (js-keys x)] [k (aget x k)])))

(defn sh
  ([bin] (sh bin []))
  ([bin args] (sh bin args {}))
  ([bin args opts]
   (js/Promise.
    (fn [resolve reject]
      (let [stdio ["inherit" "inherit" "inherit"]
            cwd (or (:cwd opts) (fs/cwd))
            env (merge (-js->clj+ js/process.env) (:env opts))
            opts (merge opts {:stdio stdio :cwd cwd :env env})
            ps (spawn (clj->js bin) (clj->js args) (clj->js opts))]
        (.on ps "error" reject)
        (.on ps
             "close"
             #((if (zero? %) resolve reject))))))))

