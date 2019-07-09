(ns ddev.xml
  (:require [clojure.string :as s]
            [xml2js :as xml]))

(defn format [xml-obj]
  (.buildObject (xml/Builder.) (clj->js xml-obj)))

(defn parse [xml-string]
  (js/Promise.
   (fn [resolve reject]
     (xml/parseString
      xml-string
      #js {:trim true}
      (fn [err result]
        (if err
          (reject err)
          (resolve (js->clj result :keywordize-keys true))))))))

