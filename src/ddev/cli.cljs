(ns ddev.cli
  "Public API for CLI"
  (:require [ddev.async :as a]
            [ddev.mvn :as mvn]
            [ddev.core :as ddev]
            [ddev.api :as api]
            [ddev.tui :as tui]
            open))

(defn redo-last-command
  "Perform the last command with no menu interactions"
  []
  (api/dispatch {:type :redo-last-action}))

(defn checkout-pull-request
  "Checkout a pull request in a temp directory"
  []
  (a/let [project (ddev/prompt-project)
          pull-request (ddev/prompt-prs project)]
    (api/dispatch
      {:type :checkout
       :opts pull-request})))

(defn run-maven-build
  "Run a maven build"
  []
  (a/let [maven-options (mvn/prompt)]
    (api/dispatch
      {:type :run-maven-build :opts maven-options})))

(defn hero-pull-request
  "Checkout / build / and run pull request"
  []
  (a/let [project (ddev/prompt-project)
          pull-request (ddev/prompt-prs project)
          mvn-options (mvn/prompt)]
    (api/dispatch
      {:type :hero-pull-request
       :opts {:pull-request pull-request :mvn-options mvn-options}})))

(defn deploy-zip-local
  "Deploy a distribution on your local machine"
  []
  (a/let [opts (ddev/prompt-dist-options)]
    (api/dispatch {:type :deploy :opts opts})))

(defn open-bookmarks
  "Open a commonly used link"
  []
  (a/let [link (ddev/prompt-link)]
    (open link)))

(defn clean-workspace
  "Delete items in your workspace directory ($HOME/.ddev)"
  []
  (a/let [choices (ddev/prompt-clean)]
    (api/dispatch
      {:type :clean-workspace :opts choices})))

(defn- get-fns []
  (->> (ns-publics 'ddev.cli)
       (filter #(let [v (deref (second %))]
                  (and (fn? v) (not= v get-fns))))
       (map #(let [doc (or (:doc (meta (second %)))
                           "No help.")]
               {:short (str (first %))
                :name (str (.padEnd (str (first %)) 30) doc)
                :value (deref (second %))}))))

(defn -main []
  (a/let [f (tui/prompt
             {:type "autocomplete"
              :message "Select task to run"
              :choices (get-fns)})]
    (f)))

