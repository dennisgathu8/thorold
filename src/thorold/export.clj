(ns thorold.export
  "Export Thorold schemas and data for non-Clojure consumers. Pure — no I/O.

   JSON Schema is derived from the Malli schemas via malli.json-schema/transform,
   not hand-written. If the Malli schemas aren't rich enough to produce a useful
   JSON Schema, the caller can extend the Malli schemas rather than patching
   the output here."
  (:require [malli.json-schema :as json-schema]
            [thorold.model :as model]))

;; ---------------------------------------------------------------------------
;; JSON Schema derivation
;; ---------------------------------------------------------------------------

(defn person-json-schema
  "Derives a JSON Schema from the Malli PersonSchema.
   Returns a Clojure map representing the JSON Schema."
  []
  (json-schema/transform model/PersonSchema))

(defn team-json-schema
  "Derives a JSON Schema from the Malli TeamSchema.
   Returns a Clojure map representing the JSON Schema."
  []
  (json-schema/transform model/TeamSchema))

(defn competition-json-schema
  "Derives a JSON Schema from the Malli CompetitionSchema."
  []
  (json-schema/transform model/CompetitionSchema))

(defn season-json-schema
  "Derives a JSON Schema from the Malli SeasonSchema."
  []
  (json-schema/transform model/SeasonSchema))

;; ---------------------------------------------------------------------------
;; CSV row export — inverse of the row parser
;; ---------------------------------------------------------------------------

(defn entity->csv-row
  "Converts a Thorold entity map back to a CSV row map (string keys, string values).
   provider-key-map: the provider key registry map (e.g. model/person-provider-keys).
   This is the inverse of parse-person-row / parse-team-row."
  [entity provider-key-map]
  (let [providers (:providers entity)
        base (cond-> {"reep_id" (:reep/id entity)}
               ;; Person fields
               (:person/name entity)
               (assoc "name" (:person/name entity)
                      "full_name" (or (:person/full-name entity) "")
                      "date_of_birth" (or (:person/dob entity) "")
                      "nationality" (or (:person/nationality entity) "")
                      "position" (or (:person/position entity) "")
                      "position_detail" (or (:person/position-detail entity) "")
                      "height_cm" (if-let [h (:person/height-cm entity)]
                                    (str (double h))
                                    "")
                      "type" (case (:reep/type entity)
                               :person/player "player"
                               :person/coach "coach"
                               ""))
               ;; Team fields
               (:team/name entity)
               (assoc "name" (:team/name entity)
                      "country" (or (:team/country entity) "")
                      "founded" (or (:team/founded entity) "")
                      "stadium" (or (:team/stadium entity) ""))
               ;; Competition fields
               (:competition/name entity)
               (assoc "name" (:competition/name entity)
                      "country" (or (:competition/country entity) ""))
               ;; Season fields
               (:season/name entity)
               (assoc "name" (:season/name entity)
                      "competition_reep_id" (or (:season/competition-reep-id entity) "")))
        ;; Add wikidata
        base (if-let [qid (:wikidata providers)]
               (assoc base "key_wikidata" qid)
               (assoc base "key_wikidata" ""))
        ;; Add provider key columns
        base (reduce-kv
              (fn [acc csv-col kw]
                (assoc acc csv-col (or (get providers kw) "")))
              base
              provider-key-map)]
    base))

;; ---------------------------------------------------------------------------
;; EDN export — lossless round-trip
;; ---------------------------------------------------------------------------

(defn db->edn
  "Full database serialized as EDN — lossless round-trip for
   Clojure/ClojureScript consumers.
   Returns a map with :entities and :meta (excludes derived indexes
   which are rebuilt on load)."
  [db]
  {:entities (vals (:entities db))
   :meta     (:meta db)
   :names    (:names db)})
