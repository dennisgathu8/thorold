(ns thorold.query
  "Search, resolve, translate, lookup — all pure functions.
   Every function takes db as the first argument."
  (:refer-clojure :exclude [resolve])
  (:require [clojure.string :as str]
            [thorold.index :as index]))

;; ---------------------------------------------------------------------------
;; Lookup — by Reep ID or QID
;; ---------------------------------------------------------------------------

(defn lookup
  "Looks up an entity by Reep ID or Wikidata QID.
   Returns the entity map or nil if not found.
   Never throws on not-found."
  [db id]
  (when-not (and (string? id) (seq id))
    (throw (ex-info "Lookup ID must be a non-empty string"
                    {:thorold/error-type :malformed-input
                     :id                 id})))
  (let [indexes (:indexes db)]
    (if (str/starts-with? id "reep_")
      ;; Direct Reep ID lookup
      (get (:by-reep-id indexes) id)
      ;; QID lookup — resolve via by-qid index
      (when-let [reep-id (get (:by-qid indexes) id)]
        (get (:by-reep-id indexes) reep-id)))))

;; ---------------------------------------------------------------------------
;; Resolve — provider + ID → entity
;; ---------------------------------------------------------------------------

(defn resolve
  "Resolves a provider ID to the full entity.
   provider: keyword (e.g. :transfermarkt, :fbref)
   id: string (the provider's ID for the entity)
   Returns the entity map or nil if not found."
  [db provider id]
  (when-not (keyword? provider)
    (throw (ex-info "Provider must be a keyword"
                    {:thorold/error-type :malformed-input
                     :provider           provider})))
  (when-not (and (string? id) (seq id))
    (throw (ex-info "Provider ID must be a non-empty string"
                    {:thorold/error-type :malformed-input
                     :id                 id})))
  (let [indexes (:indexes db)]
    (when-let [reep-id (get (:by-provider indexes) [provider id])]
      (get (:by-reep-id indexes) reep-id))))

;; ---------------------------------------------------------------------------
;; Translate — reep-id → provider ID string
;; ---------------------------------------------------------------------------

(defn translate
  "Translates a Reep ID to a specific provider's ID string.
   Returns the provider ID string or nil if not found."
  [db reep-id provider]
  (when-not (and (string? reep-id) (seq reep-id))
    (throw (ex-info "Reep ID must be a non-empty string"
                    {:thorold/error-type :malformed-input
                     :reep-id            reep-id})))
  (when-not (keyword? provider)
    (throw (ex-info "Provider must be a keyword"
                    {:thorold/error-type :malformed-input
                     :provider           provider})))
  (when-let [entity (get-in db [:indexes :by-reep-id reep-id])]
    (get (:providers entity) provider)))

;; ---------------------------------------------------------------------------
;; Search — fuzzy name search
;; ---------------------------------------------------------------------------

(defn- score-name-match
  "Scores how well a normalized query matches a normalized name.
   Higher is better. Returns 0 for no match."
  [query-normalized name-normalized]
  (cond
    ;; Exact match — highest score
    (= query-normalized name-normalized)
    1000

    ;; Starts with query — high score
    (str/starts-with? name-normalized query-normalized)
    (+ 500 (- 100 (Math/abs (- (count name-normalized) (count query-normalized)))))

    ;; Contains query — medium score
    (str/includes? name-normalized query-normalized)
    (+ 200 (- 100 (Math/abs (- (count name-normalized) (count query-normalized)))))

    ;; Word-level matching — check if all query words appear
    :else
    (let [query-words (str/split query-normalized #"\s+")
          name-words  (str/split name-normalized #"\s+")
          matches     (count (filter (fn [qw]
                                       (some #(str/starts-with? % qw) name-words))
                                     query-words))]
      (if (= matches (count query-words))
        (+ 100 (* 10 matches))
        0))))

(defn search
  "Fuzzy name search over all entities.
   opts:
     :type  — filter by entity type keyword (e.g. :person/player, :team)
     :limit — max results (default 25, max 100)
   Returns a sequence of entity maps, sorted by relevance."
  [db query opts]
  (when-not (and (string? query) (seq query))
    (throw (ex-info "Search query must be a non-empty string"
                    {:thorold/error-type :malformed-input
                     :query              query})))
  (let [normalized-query (index/normalize-name query)
        type-filter      (:type opts)
        limit            (min (or (:limit opts) 25) 100)
        by-name          (get-in db [:indexes :by-name])
        by-reep-id       (get-in db [:indexes :by-reep-id])

        ;; Find all matching names and their associated reep-ids
        candidates
        (for [[name-key reep-ids] by-name
              :let [score (score-name-match normalized-query name-key)]
              :when (pos? score)
              reep-id reep-ids]
          {:reep-id reep-id :score score})

        ;; Deduplicate by reep-id, keeping highest score
        best-scores
        (reduce
         (fn [acc {:keys [reep-id score]}]
           (update acc reep-id (fnil max 0) score))
         {}
         candidates)

        ;; Look up entities, apply type filter, sort by score
        results
        (->> best-scores
             (map (fn [[reep-id score]]
                    (when-let [entity (get by-reep-id reep-id)]
                      (when (or (nil? type-filter) (= (:reep/type entity) type-filter))
                        (assoc entity :search/score score)))))
             (filter some?)
             (sort-by :search/score >)
             (take limit)
             vec)]
    results))
