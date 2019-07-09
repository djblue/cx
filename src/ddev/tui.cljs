(ns ddev.tui
  "Common text user interface functions"
  (:require [fuzzy :as f]
            [inquirer :as i]
            [inquirer-autocomplete-prompt :as auto]))

(defn- filter-choices [input choices]
  (if-not (or (nil? input) (empty? input))
    (filter (fn [choice]
              (f/test input (:name choice)))
            choices)
    choices))

(defn prompt
  ([opts] (prompt opts identity))
  ([opts done]
   (let [{:keys [type message choices]} opts
         p (i/createPromptModule #js {:input js/process.stdin
                                      :output js/process.stderr})]
     (.registerPrompt p "autocomplete" auto)
     (-> {:name "value"
          :type type
          :message message
          :choices choices
          :source #(js/Promise.
                    (fn [res]
                      (res (clj->js
                            (filter-choices %2 choices)))))}
         clj->js
         p
         (.then #(js->clj (.-value %) :keywordize-keys true))
         (.then done)))))

