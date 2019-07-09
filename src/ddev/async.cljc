(ns ddev.async
  (:refer-clojure :exclude [let])
  (:require [ddev.tui :as tui]))

(defmacro do [& body]
  (reduce
   (fn [chain form]
     `(.then ~chain
             (fn [] (js/Promise.resolve ~form))))
   `(js/Promise.resolve nil)
   body))

(defn prompt [body]
  (tui/prompt
    {:type :confirm :message (str "Continue: " (pr-str body))}))

(defmacro do-debug [& body]
  (reduce
   (fn [chain form]
     `(.then ~chain
             (fn [] (.then (prompt '~form)
                           (fn [] (js/Promise.resolve ~form))))))
   `(js/Promise.resolve nil)
   body))

(defmacro let [bindings & body]
  (->> (partition-all 2 bindings)
       reverse
       (reduce (fn [body [n v]]
                 `(.then (js/Promise.resolve ~v)
                         (fn [~n] ~body)))
               `(ddev.async/do ~@body))))

(defmacro if
  [test consequent alternative]
   `(ddev.async/let [result# ~test]
      (if result# ~consequent ~alternative)))

(comment

  (macroexpand '(ddev.async/let [a 1] a))

  (macroexpand '(ddev.async/do-debug 1 2 3))

  (ddev.async/do-debug 1 2 3)

  (macroexpand '(ddev.async/let [v 1]
                  (println v)))

  (macroexpand '(ddev.async/if true 1))

  (ddev.async/let [a (ddev.async/do 1 2 3)]
    (ddev.async/let [b (ddev.async/do 1 2 3)]
      (println a b))
    (println "done"))

  (require 'ddev.async :reload)
  (require-macros 'ddev.async :reload)

  )


