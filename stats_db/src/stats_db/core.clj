(ns stats-db.core
  (:require #_[datalevin.core :as d]
            [datomic.api :as d]
            [clojure.instant :as inst]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]))

(def db-path "stats_db.datalevin")

(def attributes
  [[:project/name :string]
   [:project/group :string]
   [:project/full-name :string :identity]
   [:project/versions :ref :many]
   [:version/key :string :identity]
   [:version/number :string]
   [:version/stats :ref :many]
   [:stat/date :instant]
   [:stat/key :string :identity]
   [:stat/count :long]])

(def schema (for [[attr type & tags] attributes]
              (cond-> {:db/ident attr
                       :db/valueType (keyword "db.type" (name type))
                       :db/cardinality (if (some #{:many} tags)
                                         :db.cardinality/many
                                         :db.cardinality/one)}
                (some #{:identity} tags)
                (assoc :db/unique :db.unique/identity)
                )))

#_
(sh/sh "sh" "-c" (str "rm -rf " db-path))

(d/create-database "datomic:free://localhost:4334/clojars")
(def conn (d/connect "datomic:free://localhost:4334/clojars"))
#_
(d/close conn)

(d/transact conn schema)

(defn stats-tx [date downloads]
  (let [inst (inst/read-instant-date date)]
    (for [[[group project] versions]   downloads
          :let [full-name   (str/join "/" [group project])]]
      {:project/group     group
       :project/name      project
       :project/full-name full-name
       :project/versions
       (for [[version-number stats-count] versions
             :let [version-key (str/join "/" [group project version-number])
                   stat-key    (str/join "/" [group project version-number date])]]
         {:version/number version-number
          :version/key    version-key
          :version/stats  [{:stat/date  inst
                            :stat/count stats-count
                            :stat/key   stat-key
                            }]})})))

(defn file->date [f]
  (str/join "-" (next (re-find #"(\d{4})(\d{2})(\d{2})" (str f)))))

(defn read-edn-file [f]
  (with-open [r (java.io.PushbackReader. (io/reader f))]
    (read r)))

#_
(let [f (rand-nth (filter #(re-find #"downloads-\d+" (str %)) (file-seq (io/file ".."))))]
  (stats-tx
   (file->date f)
   (read-edn-file f)))
#_
(doseq [f (sort-by str (file-seq (io/file "..")))
        :when (re-find #"downloads-\d+" (str f))]
  (print ".")
  (try
    (d/transact! conn (stats-tx (file->date f) (read-edn-file f)))
    (catch Exception e
      (println)
      (println (file->date f))
      (println (.getMessage e)))))

(def cnt (volatile! 0))

(doseq [f (reverse (sort-by str (file-seq (io/file ".."))))
        :when (re-find #"downloads-\d+" (str f))
        :let [date (file->date f)]]
  (try
    (println date)
    (d/transact conn (stats-tx date (read-edn-file f)))
    (catch Exception e
      (println (.getMessage e)))))

(count
 (for [f (reverse (sort-by str (file-seq (io/file ".."))))
       :when (re-find #"downloads-\d+" (str f))
       e (try (stats-tx (file->date f) (read-edn-file f))
              (catch Exception e
                (println (file->date f))
                (println (.getMessage e))))
       :when e]
   e))

(first
 (for [f (sort (file-seq (io/file "..")))
       :when (re-find #"downloads-\d+" (str f))]
   (flatten (stats-tx (file->date f) (read-edn-file f)))))

(str f)



(d/q '{:find [(pull ?e [*])]
       :where [[?e :project/group "lambdaisland"]]}
     (d/db conn))

(d/q '{:find [(pull ?s [*])
              ]
       :where [[?e :version/stats ?s]]}
     @conn)

(def c
  (d/q '{:find [?proj (sum ?count)]
         :where [[?p :project/full-name ?proj]
                 [?p :project/versions ?v]
                 [?v :version/stats ?s]
                 [?s :stat/count ?count]
                 [?s :stat/date #inst "2020-05-01"]]}
       (d/db conn))
  )

(take 100 (reverse (sort-by second c)))


(take 100
      (sort-by (comp #(* -1 %) second)
               (d/q '{:find [?proj (sum ?count)]
                      :where [[?s :stat/date #inst "2020-05-01"]
                              [?p :project/full-name ?proj]
                              [?p :project/versions ?v]
                              [?v :version/stats ?s]
                              [?s :stat/count ?count]
                              ]}
                    (d/db conn))))
