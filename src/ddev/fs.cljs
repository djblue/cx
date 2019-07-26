(ns ddev.fs
  (:require [ddev.async :as a]
            [path :as p]
            [fs :as fs]
            rimraf
            mkdirp))

(defn cwd []
  (or js/process.env.CWD (js/process.cwd)))

(defn stat->type [stat]
  (cond
    (.isFile stat) :file
    (.isDirectory stat) :directory
    :else :other))

(defn path->entry [path]
  (let [stat (fs/statSync path)]
    {:path path
     :name (p/basename path)
     :type (stat->type stat)}))

(defn file-seq [root]
  (tree-seq
   (fn [entry]
     (and (= (:type entry) :directory)
          (not (#{"node_modules" ".git"} (:name entry)))))
   (fn [entry]
     (let [path (:path entry)]
       (map #(path->entry (p/join path %))
            (fs/readdirSync path))))
   (path->entry root)))

(defn stat [path]
  (a/promise
   [resolve]
   (fs/stat path #(resolve %2))))

(defn file? [path]
  (a/let [s (stat path)]
    (and (some? s) (.isFile s))))

(defn not-file? [path]
  (a/let [v (file? path)] (not v)))

(defn directory? [path]
  (a/let [s (stat path)]
    (and (some? s) (.isDirectory s))))

(defn not-directory? [path]
  (a/let [v (directory? path)] (not v)))

(defn spit [file-name data]
  (a/promise
   [resolve reject]
   (fs/writeFile
    (if (p/isAbsolute file-name)
      file-name
      (p/join (cwd) file-name))
    data
    "utf8"
    (fn [err]
      (if err (reject err) (resolve))))))

(defn slurp [file-name]
  (a/promise
   [resolve reject]
   (fs/readFile
    (if (p/isAbsolute file-name)
      file-name
      (p/join (cwd) file-name))
    "utf8"
    (fn [err data]
      (if err (reject err) (resolve data))))))

(defn ls [path]
  (a/promise
   [resolve]
   (fs/readdir
    path
    #js {}
    (fn [err ls]
      (if err (resolve []) (resolve (js->clj ls)))))))

(defn mkdir [path]
  (a/promise
   [resolve reject]
   (mkdirp
    path
    (fn [err]
      (if err (reject err) (resolve))))))

(defn rm [path]
  (a/promise
   [resolve reject]
   (rimraf
    path
    #(if % (reject %) (resolve)))))

