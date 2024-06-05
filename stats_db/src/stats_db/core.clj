(ns stats-db.core
  (:require
   [clj-pgcopy.core :as pgcopy]
   [clojure.instant :as inst]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [clojure.string :as str]
   [honey.sql :as sql]
   [next.jdbc :as jdbc]))

;; docker compose up
;; ./launchpad

(def URL "jdbc:postgres://localhost:5432/postgres?user=postgres&password=postgres")
(def DS (jdbc/get-datasource URL))
(def CONN (jdbc/get-connection CONN))

(defn format-qry [qry] (cond-> qry (string? qry) vector (map? qry) sql/format))
(defn exec! [qry] (jdbc/execute! DS (format-qry qry)))

(def columns [[:groupname :text]
              [:name :text]
              [:full_name :text]
              [:version :text]
              [:date :date]
              [:downloads :bigint]])

(defn create-table! []
  (exec! {:create-table :stats
          :with-columns columns}))

(defn rand-file []
  (rand-nth (filter #(re-find #"downloads-\d+" (str %)) (file-seq (io/file "..")))))

(defn read-edn-file [f]
  (with-open [r (java.io.PushbackReader. (io/reader f))]
    (read r)))

(def files
  (for [f (sort-by str (file-seq (io/file "..")))
        :when (re-find #"downloads-\d+" (str f))]
    f))

(defn file->tuples [f]
  (try
    (let [[_ y m d] (re-find #"-(.{4})(.{2})(.{2})\.edn" (.getName f))
          date (java.sql.Date. (- (parse-long y) 1900) (dec (parse-long m)) (parse-long d))]
      (doall
       (for [[[groupname name] versions] (read-edn-file f)
             :let [fname (str groupname "/" name)]
             [version cnt] versions]
         [groupname name fname version date cnt])))
    (catch Exception e
      (println "Error in" f ", " (.getMessage e)))))

(create-table!)


;; takes about a minute on a fast-ish machine
(time
 (pgcopy/copy-into! CONN
                    :stats
                    (map first columns)
                    (sequence cat (pmap file->tuples files))))


(def this-year
  (into {}
        (map
         (juxt :stats/full_name :sum))
        (exec! {:select [[:full_name] [[:sum :downloads]]]
                :from [:stats]
                :where [[:raw "date between '2024-02-01' and '2024-05-01'"]]
                :group-by [:full_name]
                :order-by [[:sum :desc]]})))

(def last-year
  (into {}
        (map
         (juxt :stats/full_name :sum))
        (exec! {:select [[:full_name] [[:sum :downloads]]]
                :from [:stats]
                :where [[:raw "date between '2023-02-01' and '2023-05-01'"]]
                :group-by [:full_name]
                :order-by [[:sum :desc]]})))

(sort-by #(- (val %))
         (into {}
               (remove (comp last-year key) this-year)))

(sort-by #(- (second %))
         (map
          (fn [[k v]]
            [k (double (/ (long v) (long (get last-year k)))) (long v) (long (get last-year k))])
          (remove #(< (get last-year (key %) 0) 100) this-year)))

(sort-by #(- (val %))
         (into {}
               (map
                (fn [[k v]]
                  [k (- (long v) (long (get last-year k 0)))]))
               this-year))

(get this-year "riddley/riddley")

(- (count this-year)
   (count last-year))


(- (apply + (vals this-year))
   (apply + (vals last-year)))
