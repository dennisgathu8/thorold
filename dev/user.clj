(ns user
  "REPL entry point: convenience requires and helpers.
   DEV-ONLY — never shipped to production uberjar."
  (:require [thorold.db :as db]
            [thorold.query :as q]
            [thorold.index :as idx]
            [thorold.model :as model]
            [thorold.id :as id]
            [thorold.parse :as parse]
            [thorold.ingest :as ingest]
            [clojure.data :as data]))

(defn reload-db
  "Reload the database from CSV files."
  ([] (reload-db "data/"))
  ([data-dir]
   (db/reset-db-cache!)
   (db/load-db-cached data-dir true)))

(defn quick-search
  "Convenience search function for the REPL."
  [name-str]
  (let [db (db/load-db-cached "data/")]
    (q/search db name-str {:limit 5})))

(defn quick-resolve
  "Convenience resolve function for the REPL."
  [provider id]
  (let [db (db/load-db-cached "data/")]
    (q/resolve db (keyword provider) id)))

(comment
  ;; Load the database
  (def db (reload-db))

  ;; Search
  (quick-search "Lionel Messi")
  (quick-search "Arsenal")

  ;; Resolve
  (quick-resolve :transfermarkt "568177")

  ;; Stats
  (db/db-stats db)
  )
