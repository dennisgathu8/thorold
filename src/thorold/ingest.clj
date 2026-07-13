(ns thorold.ingest
  "Wikidata SPARQL ingestion pipeline using transducers.

   Each pipeline stage is a separate, named, independently testable transducer.
   The full pipeline is their composition.

   merge-into-db is a pure function: existing-db + new-entities → updated-db.
   Deterministic — same inputs always produce same output."
  (:require [clojure.string :as str]
            [thorold.id :as id]
            [thorold.index :as index]))

;; ---------------------------------------------------------------------------
;; Pipeline stages — each is a named, testable transducer
;; ---------------------------------------------------------------------------

(defn xf-parse-sparql
  "Transducer: parses raw SPARQL response bindings into normalized maps.
   Extracts QID, name, type, date of birth, and provider IDs from
   the SPARQL JSON result format."
  []
  (map (fn [binding]
         (let [get-val (fn [k] (get-in binding [k "value"]))]
           {:qid         (some-> (get-val "item")
                                 (str/replace #"http://www.wikidata.org/entity/" ""))
            :name        (get-val "itemLabel")
            :type        (get-val "type")
            :dob         (get-val "dob")
            :nationality (get-val "nationality")
            :providers   (reduce-kv
                          (fn [acc k v]
                            (if (and (str/starts-with? (name k) "id_")
                                     (get v "value"))
                              (assoc acc
                                     (keyword (subs (name k) 3))
                                     (get v "value"))
                              acc))
                          {}
                          binding)}))))

(defn xf-normalize-ids
  "Transducer: normalizes provider IDs (trim whitespace, etc.)."
  []
  (map (fn [entity]
         (update entity :providers
                 (fn [providers]
                   (reduce-kv
                    (fn [acc k v]
                      (if (and v (not (str/blank? v)))
                        (assoc acc k (str/trim v))
                        acc))
                    {}
                    providers))))))

(defn xf-validate-schema
  "Transducer: filters out entities that fail schema validation.
   Silently drops invalid entities."
  []
  (filter (fn [entity]
            (and (:qid entity)
                 (:name entity)
                 (:type entity)))))

(defn xf-deduplicate
  "Stateful deduplication transducer. Drops any entity whose QID has already
   been seen in the current pipeline run.

   CAUTION: This transducer is stateful. It uses a volatile set internally to
   track seen QIDs across the sequence. Consequences:
   - A single pipeline instance cannot be safely shared across threads.
   - It cannot be split or used in parallel transduction.
   - Each call to (ingest-pipeline) creates a fresh independent instance,
     which is the correct usage pattern.
   - Do not reuse a pipeline instance across multiple sequences."
  []
  (fn [rf]
    (let [seen (volatile! #{})]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (let [qid (:qid input)]
           (if (@seen qid)
             result
             (do
               (vswap! seen conj qid)
               (rf result input)))))))))

(defn xf-assign-reep-id
  "Transducer: assigns Reep IDs to entities that don't have one.
   Uses deterministic ID generation from QID as seed."
  []
  (map (fn [entity]
         (let [entity-type (case (:type entity)
                             "player" :player
                             "coach"  :coach
                             "team"   :team
                             :player)
               reep-id     (id/reep-id entity-type (:qid entity))]
           (-> entity
               (assoc :reep-id reep-id)
               (assoc :entity-type entity-type))))))

(defn xf-to-entity-map
  "Transducer: converts ingestion maps to Thorold entity maps
   matching the schema in thorold.model."
  []
  (map (fn [{:keys [reep-id entity-type qid name dob nationality providers]}]
         (let [thorold-type (case entity-type
                              :player :person/player
                              :coach  :person/coach
                              :team   :team
                              :person/player)]
           (cond-> {:reep/id    reep-id
                    :reep/type  thorold-type
                    :providers  (assoc providers :wikidata qid)}
             (#{:person/player :person/coach} thorold-type)
             (assoc :person/name        name
                    :person/dob         dob
                    :person/nationality nationality)

             (= :team thorold-type)
             (assoc :team/name name))))))

;; ---------------------------------------------------------------------------
;; Composed pipeline
;; ---------------------------------------------------------------------------

(defn ingest-pipeline
  "Returns a composed transducer. Stages:
   parse SPARQL response → normalize IDs → validate schema →
   deduplicate → assign Reep ID → convert to entity map.

   Each stage is independently testable."
  []
  (comp
   (xf-parse-sparql)
   (xf-normalize-ids)
   (xf-validate-schema)
   (xf-deduplicate)
   (xf-assign-reep-id)
   (xf-to-entity-map)))

;; ---------------------------------------------------------------------------
;; Database merging — pure function
;; ---------------------------------------------------------------------------

(defn merge-into-db
  "Pure function. existing-db + new-entities → updated-db.
   Deterministic: same inputs always produce same output.

   Returns [updated-db manifest] where manifest is:
   {:added N :updated N :skipped N :conflicts [...]}"
  [db new-entities]
  (let [existing-entities (:entities db)
        result
        (reduce
         (fn [{:keys [entities added updated skipped conflicts]} entity]
           (let [reep-id   (:reep/id entity)
                 existing  (get entities reep-id)]
             (cond
               ;; New entity — add it
               (nil? existing)
               {:entities  (assoc entities reep-id entity)
                :added     (inc added)
                :updated   updated
                :skipped   skipped
                :conflicts conflicts}

               ;; Same entity, same data — skip
               (= existing entity)
               {:entities  entities
                :added     added
                :updated   updated
                :skipped   (inc skipped)
                :conflicts conflicts}

               ;; Same ID, different data — update (merge providers)
               :else
               (let [merged (update existing :providers merge (:providers entity))]
                 {:entities  (assoc entities reep-id merged)
                  :added     added
                  :updated   (inc updated)
                  :skipped   skipped
                  :conflicts conflicts}))))
         {:entities  (or existing-entities {})
          :added     0
          :updated   0
          :skipped   0
          :conflicts []}
         new-entities)

        updated-entities (:entities result)
        new-indexes      (index/build-indexes (vals updated-entities))
        manifest         (select-keys result [:added :updated :skipped :conflicts])]

    [(assoc db
            :entities updated-entities
            :indexes  new-indexes)
     manifest]))

;; ---------------------------------------------------------------------------
;; Changelog transducer — compare incoming entities against existing db
;; ---------------------------------------------------------------------------

(defn xf-changelog
  "Stateful transducer comparing incoming entities against existing-db.
   Emits typed events — plain maps, EDN/JSON-serializable:

   :entity/added     — entity not present in existing-db
     {:type :entity/added :reep-id id :entity entity}

   :provider/updated — entity exists but providers differ
     {:type :provider/updated :reep-id id
      :added-ids   {provider-kw id-string ...}
      :removed-ids {provider-kw id-string ...}}

   :entity/unchanged — entity exists and is identical
     {:type :entity/unchanged :reep-id id}

   CAUTION: Stateful — do not reuse across sequences or share across threads."
  [existing-db]
  (let [existing-entities (:entities existing-db)]
    (map (fn [entity]
           (let [reep-id  (:reep/id entity)
                 existing (get existing-entities reep-id)]
             (cond
               ;; New entity
               (nil? existing)
               {:type    :entity/added
                :reep-id reep-id
                :entity  entity}

               ;; Identical entity
               (= existing entity)
               {:type    :entity/unchanged
                :reep-id reep-id}

               ;; Providers changed
               :else
               (let [old-providers (:providers existing)
                     new-providers (:providers entity)
                     all-keys      (into (set (keys old-providers))
                                         (keys new-providers))
                     added-ids     (reduce (fn [acc k]
                                            (if (and (get new-providers k)
                                                     (not (get old-providers k)))
                                              (assoc acc k (get new-providers k))
                                              acc))
                                          {}
                                          all-keys)
                     removed-ids   (reduce (fn [acc k]
                                             (if (and (get old-providers k)
                                                      (not (get new-providers k)))
                                               (assoc acc k (get old-providers k))
                                               acc))
                                           {}
                                           all-keys)]
                 {:type        :provider/updated
                  :reep-id     reep-id
                  :added-ids   added-ids
                  :removed-ids removed-ids})))))))

(defn generate-changelog
  "Runs the changelog transducer over new-entities, comparing against
   existing-db. Returns the seq of changelog events.
   Pure given its inputs — no I/O."
  [existing-db new-entities]
  (into [] (xf-changelog existing-db) new-entities))
