(ns ddev.async
  (:refer-clojure :exclude [let promise]))

(defmacro promise [bindings & body]
  `(js/Promise. (fn ~bindings ~@body)))

(defmacro do [& body]
  (reduce
   (fn [chain form]
     `(.then ~chain
             (fn [] (js/Promise.resolve ~form))))
   `(js/Promise.resolve nil)
   body))

(defmacro let [bindings & body]
  (->> (partition-all 2 bindings)
       reverse
       (reduce (fn [body [n v]]
                 `(.then (js/Promise.resolve ~v)
                         (fn [~n] ~body)))
               `(ddev.async/do ~@body))))

(defmacro if [test & body]
  `(ddev.async/let [result# ~test] (if result# ~@body)))
