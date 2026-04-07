(ns thorold.db
  "Database assembly: load all sources, build all indexes.

   The database is one immutable value — a single Clojure map containing
   all entities and all indexes. No global vars. The caller holds and
   passes this value."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [thorold.parse :as parse]
            [thorold.index :as index]))

;; ---------------------------------------------------------------------------
;; Database loading
;; ---------------------------------------------------------------------------

(defn load-db
  "Loads all source files from data-dir.
   Returns a single immutable map: all entities plus all indexes.
   This value is the entire runtime state of Thorold.

   Shape:
     {:entities  {reep-id → entity-map}
      :indexes   {:by-reep-id  ...
                  :by-provider ...
                  :by-qid      ...
                  :by-name     ...}
      :names     [name-alias-maps ...]
      :meta      {:people-count N :teams-count N :errors-count N :load-ms N}}"
  [data-dir]
  (log/info "Loading Thorold database from" data-dir)
  (let [start-ms  (System/currentTimeMillis)
        _         (log/info "Parsing people.csv...")
        people    (parse/parse-people-file data-dir)
        _         (log/info (str "  " (:count people) " people parsed, "
                                 (count (:errors people)) " errors"))
        _         (log/info "Parsing teams.csv...")
        teams     (parse/parse-teams-file data-dir)
        _         (log/info (str "  " (:count teams) " teams parsed, "
                                 (count (:errors teams)) " errors"))
        _         (log/info "Parsing names.csv...")
        names     (parse/parse-names-file data-dir)
        _         (log/info (str "  " (:count names) " name aliases parsed"))

        ;; Combine all entities into a single sequence for indexing
        all-entities (concat (:entities people) (:entities teams))

        _         (log/info "Building indexes...")
        indexes   (index/build-indexes all-entities)
        _         (log/info (str "  " (index/index-stats indexes)))

        elapsed   (- (System/currentTimeMillis) start-ms)
        _         (log/info (str "Database loaded in " elapsed "ms"))]
    {:entities (:by-reep-id indexes)
     :indexes  indexes
     :names    (:names names)
     :meta     {:people-count  (:count people)
                :teams-count   (:count teams)
                :names-count   (:count names)
                :people-errors (count (:errors people))
                :teams-errors  (count (:errors teams))
                :load-ms       elapsed}}))

;; ---------------------------------------------------------------------------
;; Cached loading (dev-only convenience)
;; ---------------------------------------------------------------------------

(defonce ^:private db-cache (atom nil))

(defn load-db-cached
  "Caches the loaded DB in a defonce atom for REPL convenience.
   DEV-ONLY — never call this in production paths.
   Pass force? true to reload."
  ([data-dir] (load-db-cached data-dir false))
  ([data-dir force?]
   (if (or force? (nil? @db-cache))
     (let [db (load-db data-dir)]
       (reset! db-cache db)
       db)
     @db-cache)))

(defn reset-db-cache!
  "Clears the dev-only DB cache."
  []
  (reset! db-cache nil))

;; ---------------------------------------------------------------------------
;; Stats
;; ---------------------------------------------------------------------------

(defn db-stats
  "Returns the same shape as the /stats API response.
   Pure function over the DB value."
  [db]
  (let [entities (:entities db)
        by-type  (reduce-kv
                  (fn [acc _id entity]
                    (let [t (name (:reep/type entity))]
                      (update acc t (fnil inc 0))))
                  {}
                  entities)
        by-provider (reduce-kv
                     (fn [acc _id entity]
                       (reduce-kv
                        (fn [inner-acc provider _v]
                          (update inner-acc (name provider) (fnil inc 0)))
                        acc
                        (:providers entity)))
                     {}
                     entities)]
    {:total-entities  (count entities)
     :by-type         by-type
     :by-provider     (into (sorted-map-by
                             (fn [a b]
                               (let [ca (get by-provider a 0)
                                     cb (get by-provider b 0)]
                                 (if (= ca cb)
                                   (compare a b)
                                   (compare cb ca)))))
                            by-provider)
     :load-ms         (get-in db [:meta :load-ms])}))
