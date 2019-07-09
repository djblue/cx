(ns ddev.core
  (:require-macros [ddev.async :as a])
  (:require [clojure.string :as s]
            [child_process :as cp]
            [path :as p]
            [os :as os]
            [ddev.fs :as fs]
            [ddev.mvn :as mvn]
            [ddev.tui :as tui]
            [ddev.shell :refer [sh]]
            [ddev.xml :as xml]
            node-fetch
            [extract-zip :as extract]
            ddev.async))

(defn fetch
  ([url] (fetch url {}))
  ([url opts]
   (node-fetch url (clj->js (merge {:user-agent :request} opts)))))

(defn pull-request->choice [pull-request]
  (let [title (:title pull-request)
        number (:number pull-request)
        user (get-in pull-request [:user :login])
        url (:html_url pull-request)]
    {:value pull-request
     :short (str "#" number " - " url)
     :name (str "#" number " " user " - " title)}))

(defn pull-request-info [pull-request]
  (let [repo-name (get-in pull-request [:base :repo :name])
        git-clone-url (get-in pull-request [:base :repo :ssh_url])
        workspace (p/join (os/homedir) ".ddev")
        git-cache (p/join workspace (str repo-name ".git"))
        pull-request-root (p/join workspace
                                  (str "pull-request-" repo-name "-" (:number pull-request)))
        pull-request-source (p/join pull-request-root "src")]
    {:repo-name repo-name
     :git-clone-url git-clone-url
     :workspace workspace
     :git-cache git-cache
     :pull-request-root pull-request-root
     :pull-request-source pull-request-source}))

(defn checkout
  ([pull-request]
   (checkout pull-request (pull-request-info pull-request)))
  ([pull-request info]
   (let [number (:number pull-request)
         sha (get-in pull-request [:head :sha])
         branch (get-in pull-request [:head :ref])
         {:keys [git-clone-url git-cache pull-request-root pull-request-source]} info
         sh-opts {:cwd pull-request-source}]
     (a/do
       (a/if (fs/not-directory? git-cache)
         (sh :git [:clone git-clone-url "--bare" git-cache]))
       (sh :git [(str "--git-dir=" git-cache) :fetch :origin (str "refs/pull/" number "/head")])
       (a/if (fs/not-directory? pull-request-source)
         (a/do
           (sh :git [:clone git-cache pull-request-source])
           (sh :git [:checkout sha "-b" branch] sh-opts)))
       (when-let [shell js/process.env.SHELL]
         (sh shell [] sh-opts))))))

(defn github-fetch [url]
  (a/let [res (fetch url
                     (when-let [token js/process.env.GITHUB_TOKEN]
                       {:headers {:authorization (str "token " token)}}))
          json (.json res)]
    (js->clj json :keywordize-keys true)))

(defn github-fetch-repo [org]
  (github-fetch
   (str "https://api.github.com/orgs/" org "/repos?per_page=1000")))

(defn repo->choice [repo]
  {:name (str (.padEnd (:name repo) 30)
              (:html_url repo))
   :short (str (:name repo) " - " (:html_url repo))
   :value repo})

(defn prompt-project []
  (a/let [[codice connexta]
          (js/Promise.all (map github-fetch-repo ["codice" "connexta"]))
          repos (sort-by :open_issues > (concat codice connexta))]
    (tui/prompt
     {:type :autocomplete
      :message "Select a repo"
      :choices (map repo->choice repos)})))

(defn prompt-prs [project]
  (a/let [url (:url project)
          prs (github-fetch (str url "/pulls"))]
    (tui/prompt
     {:type :autocomplete
      :message "Select a pull request"
      :choices (map pull-request->choice prs)})))

(defn start-maven-server []
  (js/Promise.
   (fn [resolve]
     (let [http (js/require "http")
           srv (http.createServer mvn/local-m2-handler)]
       (.listen srv 0
                #(a/let [port (.-port (.address srv))
                         url (str "http://localhost:" port)
                         path (p/join (os/homedir) ".m2" "settings.xml")
                         settings-xml (a/if (fs/file? path)
                                        (a/let [contents (fs/slurp path)]
                                          (xml/parse contents))
                                        {:settings {:mirrors []}})]
                   (resolve
                    [(fn [] (.close srv))
                     (xml/format (mvn/patch-settings-xml settings-xml url))])))))))

(defn run-maven-build
  ([opts] (run-maven-build opts (fs/cwd)))
  ([opts cwd]
   (let [mirror-m2? (some #(= % :local-m2-mirror) opts)
         settings-xml (p/join cwd "settings.xml")
         options (-> (mvn/merge-options opts {:cwd cwd})
                     (merge (when mirror-m2? {:settings settings-xml}))
                     mvn/opts->cmd)
         sh-opts {:cwd cwd
                  :env {:MAVEN_OPTS (str "-Xmx" "4G" " -Xms" "4g")}}]
     (if mirror-m2?
       (a/let [[on-close! settings] (start-maven-server)]
         (fs/spit settings-xml settings)
         (sh :mvn options sh-opts)
         (on-close!))
       (sh :mvn options sh-opts)))))

(defn get-distribution [entry]
  (when-let [[_ project version]
             (re-matches #"([^-]+)-(\d[^\/-]*(-SNAPSHOT)?)\.zip" (:name entry))]
    (merge entry {:project project :version version :folder (str project "-" version)})))

(defn find-distribution [root]
  (some #(get-distribution %) (fs/file-seq (str root "/distribution"))))

(defn unzip [src dest]
  (js/Promise.
   (fn [resolve reject]
     (extract
      src
      #js {:dir dest
           :onEntry #(println (p/join dest (.-fileName %)))}
      (fn [err]
        (if err (reject err) (resolve)))))))

(defn sleep [time]
  (js/Promise.
   (fn [resolve reject]
     (js/setTimeout resolve time))))

(defn deploy
  ([] (deploy (fs/cwd) (p/join (os/homedir) ".ddev")))
  ([project-root to]
   (a/let [{:keys [path folder]} (find-distribution project-root)
           bin (p/join to folder "/bin")
           sh-opts {:cwd bin
                    :env {:KARAF_DEBUG true
                          :JAVA_HOME "/usr/lib/jvm/default"}}]
     (a/if (fs/not-directory? (p/join to folder)) (unzip path to))
     (when-let [shell js/process.env.SHELL]
       (sh shell [] sh-opts)))))

(defn hero-pull-request [opts]
  (let [{:keys [pull-request mvn-options]} opts
        info (pull-request-info pull-request)]
    (a/do
      (checkout pull-request info)
      (if-not (find-distribution (:pull-request-source info))
        (run-maven-build mvn-options (:pull-request-source info)))
      (deploy (:pull-request-source info)
              (p/join (:pull-request-root info) "dist")))))

(defn prompt-clean []
  (a/let [home (p/join (os/homedir) ".ddev")
          dir (fs/ls home)]
    (tui/prompt
     {:message "What should be removed?"
      :type :checkbox
      :choices
      (map #(-> {:name %
                 :value (p/join (os/homedir) ".ddev" %)}) dir)})))

(defn clean-workspace [opts]
  (doall (map fs/rm opts)))

