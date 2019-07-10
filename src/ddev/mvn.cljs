(ns ddev.mvn
  "Code related to building up a maven command"
  (:require-macros [ddev.async :as a])
  (:require [clojure.string :as s]
            [path :as p]
            [fs :as fs]
            [os :as os]
            [ddev.xml :as xml]
            [ddev.tui :as tui]))

(defn- camelize [kw]
  (s/replace kw #"-(\w)" #(s/upper-case (second %1))))

(defn opts->cmd
  "Convert maven options into a command line strucutre"
  [opts]
  (let [env (if-let [m (:memory opts)]
              {:env {:MAVEN_OPTS (str "-Xmx" m " -Xms" m)}})
        cmd (concat
             (:phases opts)
             (if-let [t (:threads opts)]
               ["--threads" t])
             (if-let [tpc (:threads-per-core opts)]
               ["--threads" (str  tpc "C")])
             (map #(str "--" (name %)) (:flags opts))
             (mapcat #(-> ["--define"
                           (str (camelize (name (first %))) "=" (second %))])
                     (:define opts))
             (if-let [profiles (:activate-profiles opts)]
               ["--activate-profiles" (s/join "," profiles)])
             (if-let [f (:log-file opts)]
               ["--logfile" f])
             (if-let [file (:settings opts)]
               ["--settings" file]))
        with-projects
        (concat
         cmd
         (let [exclude (map #(str "!" %) (:exclude-projects opts))
               projects (concat (:projects opts) exclude)]
           (if-not (empty? projects)
             ["--projects" (s/join "," projects)])))]
    (let [projects (concat (:projects opts)
                           (:exclude-projects opts))]
      (with-meta with-projects env))))

(defn build []
  {:define {}
   :memory "4G"
   :phases [:install]
   :threads-per-core nil
   :exclude-projects []
   :settings nil
   :activate-profiles ["!docker"]
   :flags [:no-snapshot-updates]})

(defn skip-tests [opts]
  (update opts :define conj [:skip-tests true]))

(defn skip-static [opts]
  (update opts :define conj [:skip-static true]))

(defn skip-docs [opts]
  (update opts :exclude-projects conj "distribution/docs"))

(defn skip-ui [opts]
  (update opts :exclude-projects conj "ui"))

(defn with-settings [opts file]
  (assoc opts :settings file))

(defn clean-build [opts]
  (assoc opts :phases [:clean :install]))

(defn isolated-m2 [opts]
  (update opts :define conj [:maven.repo.local (p/join (:project-root opts) "m2")]))

(defn threaded-build [opts]
  (assoc opts :threads-per-core 1))

(def mvn-options
  {:skip-tests skip-tests
   :skip-static skip-static
   :skip-ui skip-ui
   :skip-docs skip-docs
   :isolated-m2 isolated-m2
   :clean-build clean-build
   :threaded threaded-build
   :local-m2-mirror identity})

(defn merge-options [flags opts]
  (reduce
   (fn [opts f] (f opts))
   (merge (build) opts)
   (map mvn-options flags)))

(defn patch-settings-xml [settings-xml mirror-url]
  (assoc-in
   (or settings-xml {})
   [:settings :mirrors]
   [{:mirror
     (into
      [{:id ["m2-mirror"]
        :name "M2 Mirror"
        :url mirror-url
        :mirrorOf "*"}]
      (when-let [mirror
                 (get-in settings-xml [:settings :mirrors 0 :mirror])]
        (when-not (string? mirror) mirror)))}]))

(defn local-m2-handler [req res]
  (let [url (.-url req)
        file (p/join (os/homedir) ".m2/repository" url)
        location (str "https://artifacts.codice.org/content/groups/public" url)]
    (fs/exists
     file
     (fn [exists?]
       (if exists?
         (.pipe (fs/createReadStream file) res)
         (do (.writeHead res 302 #js {:location location})
             (.end res)))))))

(def prompt-options
  {:type :checkbox
   :message "Specify maven settings"
   :choices
   [{:value :skip-tests
     :name "Skip Tests"}
    {:value :skip-static
     :name "Skip Static"}
    {:value :skip-ui
     :name "Skip UI"}
    {:value :skip-docs
     :name "Skip Docs"}
    {:value :threaded
     :name "Threaded"}
    {:value :clean-build
     :name "Clean"}
    {:value :isolated-m2
     :name "Isolated M2"}
    {:value :local-m2-mirror
     :name "Local M2 Mirror"}]})

(defn prompt []
  (a/let [choices (tui/prompt prompt-options)]
    (mapv keyword choices)))

