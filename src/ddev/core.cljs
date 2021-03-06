(ns ddev.core
  (:require [ddev.async :as a]
            [clojure.string :as s]
            [path :as p]
            [os :as os]
            [ddev.fs :as fs]
            [ddev.mvn :as mvn]
            [ddev.tui :as tui]
            [ddev.shell :refer [sh]]
            [ddev.xml :as xml]
            find-up
            node-fetch
            node-notifier
            [extract-zip :as extract]))

(defn notify
  ([message] (notify "cx" message))
  ([title message]
   (.notify node-notifier #js {:title title :message message})))

(def links
  [{:title "DDF Documentation"
    :description "Documentation for recent DDF releases."
    :href "http://codice.org/ddf/Documentation-versions.html"}
   {:title "Codice Foundation"
    :description "Codice Foundation home page."
    :href "https://codice.org/index.html"}
   {:title "Code Reviewer's Cheat Sheet"
    :description "Helpful tips to apply when doing code reviews."
    :href "https://codice.atlassian.net/wiki/spaces/DDF/pages/55771138/Reviewer+s+Cheat+Sheet"}
   {:title "UI Readme"
    :description "Everything you wanted to know about UI but were too afraid to ask."
    :href "https://github.com/codice/ddf/blob/master/ui/README.md"}
   {:title "How to Debug Integration Tests"
    :description "Tips on debugging itests."
    :href "https://github.com/codice/ddf/blob/master/distribution/test/itests/README.md"}
   {:title "Catalog UI Search"
    :description "The main DDF UI"
    :href "https://localhost:8993/search/catalog/"}
   {:title "Admin UI"
    :description "The DDF Admin UI"
    :href "https://localhost:8993/admin"}
   {:title "DDF Docs"
    :description "The DDF documentation site"
    :href "https://localhost:8993/admin/documentation/documentation.html"}])

(defn link->choice [link]
  {:name (str (.padEnd (:title link) 30)
              (:description link))
   :short (:title link)
   :value (:href link)})

(defn prompt-link []
  (tui/prompt
   {:type :autocomplete
    :message "Select a link"
    :choices (map link->choice links)}))

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
          (a/all (map github-fetch-repo ["codice" "connexta"]))
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
  (a/promise
   [resolve]
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
                   (xml/format (mvn/patch-settings-xml settings-xml url))]))))))

(defn run-maven-build
  ([opts] (run-maven-build opts (fs/cwd)))
  ([opts cwd]
   (a/let [mirror-m2? (some #(= % :local-m2-mirror) opts)
           project-root (.then (find-up ".git" #js {:cwd cwd}) p/dirname)
           settings-xml (p/join project-root "settings.xml")
           options (-> (mvn/merge-options opts {:project-root project-root})
                       (merge (when mirror-m2? {:settings settings-xml}))
                       mvn/opts->cmd)
           sh-opts {:cwd cwd
                    :env {:MAVEN_OPTS (str "-Xmx" "4G" " -Xms" "4g")}}]
     (if mirror-m2?
       (a/let [[on-close! settings] (start-maven-server)]
         (fs/spit settings-xml settings)
         (sh :mvn options sh-opts)
         (on-close!))
       (sh :mvn options sh-opts))
     (notify "Maven Build Complete"))))

(defn get-distribution [entry]
  (when-let [[_ project version]
             (re-matches #"([^-]+)-(\d[^\/-]*(-SNAPSHOT)?)\.zip" (:name entry))]
    (merge entry {:project project :version version :folder (str project "-" version)})))

(defn find-distribution [root]
  (some #(get-distribution %) (fs/file-seq (str root "/distribution"))))

(defn unzip [src dest]
  (a/promise
   [resolve reject]
   (extract
    src
    #js {:dir dest
         :onEntry #(println (p/join dest (.-fileName %)))}
    (fn [err]
      (if err (reject err) (resolve))))))

(defn sleep [time]
  (a/promise [resolve] (js/setTimeout resolve time)))

(defn prompt-dist-options []
  (a/let [dot-git (find-up ".git")
          project-root (p/dirname dot-git)
          flags
          (tui/prompt
           {:type :checkbox
            :message "Specify deploy settings"
            :default [:disable-security-manager?]
            :choices
            [{:value :disable-security-manager?
              :name "Disable Security Manager"}]})
          port
          (tui/prompt
           {:type :number
            :message "Https port"
            :default 8993})]
    {:port port
     :project-root project-root
     :flags (->> flags (map keyword) (into #{}))}))

(defn comment-security-properties [contents]
  (reduce
   (fn [contents match]
     (s/replace contents match #(str "# " %)))
   contents
   [#"(?m)^policy\.provider=.*$"
    #"(?m)^java\.security\.manager=.*$"
    #"(?m)^java\.security\.policy==.*$"
    #"(?m)^proGrade\.getPermissions\.override=.*$"]))

(defn disable-security-manager [root]
  (a/let [properties (p/join root "etc/custom.system.properties")
          contents (fs/slurp properties)
          new-contents (comment-security-properties contents)]
    (fs/spit properties new-contents)))

(defn replace-port [root port]
  (a/let [properties (p/join root "etc/custom.system.properties")
          contents (fs/slurp properties)
          new-contents (s/replace
                        contents
                        #"(?m)^org\.codice\.ddf\.system\.httpsPort=.*$"
                        #(str "org.codice.ddf.system.httpsPort=" port))]
    (fs/spit properties new-contents)))

(defn deploy [opts]
  (println opts)
  (a/let [{:keys [project-root flags port]} opts
          {:keys [path folder]} (find-distribution project-root)
          bin (p/join project-root folder "/bin")
          sh-opts {:cwd bin
                   :env {:KARAF_DEBUG true
                         :JAVA_HOME "/usr/lib/jvm/default"}}]
    (a/if (fs/not-directory? (p/join project-root folder))
      (unzip path project-root))
    (when (flags :disable-security-manager?)
      (disable-security-manager
       (p/join project-root folder)))
    (when-not (= port 8993)
      (replace-port
       (p/join project-root folder)
       port))
    (when-let [shell js/process.env.SHELL]
      (sh shell [] sh-opts))))

(defn hero-pull-request [opts]
  (let [{:keys [pull-request mvn-options]} opts
        info (pull-request-info pull-request)]
    (a/do
      (checkout pull-request info)
      (if-not (find-distribution (:pull-request-source info))
        (run-maven-build mvn-options (:pull-request-source info)))
      (deploy {:project-root (:pull-request-source info)}))))

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

