(ns thorold.index
  "Index builders — pure functions over data.

   All four indexes are built in a single reduce pass.
   The collection is never iterated more than once.

   Indexes:
     :by-reep-id  — reep-id string → entity map
     :by-provider — [provider-kw provider-id-string] → reep-id
     :by-qid      — qid-string → reep-id
     :by-name     — normalized-name-string → #{reep-id ...}"
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Name normalization — pure, standalone, exhaustively tested
;; ---------------------------------------------------------------------------

(defn normalize-name
  "Normalizes a name string for fuzzy matching.
   Steps:
   1. Lowercase
   2. Strip diacritics (NFD decomposition + removal of combining chars)
   3. Remove non-alphanumeric characters (except spaces)
   4. Collapse whitespace
   5. Trim

   Examples:
     \"Müller\"          → \"muller\"
     \"José Mourinho\"   → \"jose mourinho\"
     \"O'Brien\"         → \"obrien\"
     \"van der Sar\"     → \"van der sar\""
  [s]
  (when s
    (-> s
        str/lower-case
        (java.text.Normalizer/normalize java.text.Normalizer$Form/NFD)
        (str/replace #"[\p{InCombiningDiacriticalMarks}]" "")
        (str/replace #"[^a-z0-9\s]" "")
        (str/replace #"\s+" " ")
        str/trim)))

(defn name-trigrams
  "Generates trigrams from a normalized name for fuzzy matching.
   Pads with spaces for start/end trigrams.

   Example: \"messi\" → #{\"  m\" \" me\" \"mes\" \"ess\" \"ssi\" \"si \" \"i  \"}"
  [s]
  (when-let [normalized (normalize-name s)]
    (let [padded (str "  " normalized "  ")]
      (into #{} (map #(subs padded % (+ % 3)) (range (- (count padded) 2)))))))

;; ---------------------------------------------------------------------------
;; Entity name extraction — works across entity types
;; ---------------------------------------------------------------------------

(defn- entity-name
  "Extracts the primary name from an entity regardless of type."
  [entity]
  (or (:person/name entity)
      (:team/name entity)
      (:competition/name entity)
      (:season/name entity)))

(defn- entity-names
  "Returns all name strings for an entity (name + full-name if present)."
  [entity]
  (filterv some?
           [(entity-name entity)
            (:person/full-name entity)]))

;; ---------------------------------------------------------------------------
;; Single-pass index building
;; ---------------------------------------------------------------------------

;; PROOF: The entities collection is iterated exactly once.
;; `build-indexes` calls `reduce` over `entities` — a single sequential traversal.
;; Inside the reducing function, we build all four indexes simultaneously by
;; accumulating into a single map-of-maps. No intermediate collections are
;; created from the entities sequence. The `entity-names` call per entity is
;; O(1) — it extracts at most 2 fields from the current entity map.

(defn build-indexes
  "Pure function. Single reduce pass over entities — never iterates the
   collection twice. Returns all four indexes built simultaneously via
   structural sharing.

   entities: a sequence of entity maps (as produced by thorold.parse)

   Returns:
     {:by-reep-id  {reep-id → entity}
      :by-provider {[provider-kw id-string] → reep-id}
      :by-qid      {qid-string → reep-id}
      :by-name     {normalized-name → #{reep-id ...}}}"
  [entities]
  (reduce
   (fn [{:keys [by-reep-id by-provider by-qid by-name]} entity]
     (let [reep-id   (:reep/id entity)
           providers (:providers entity)
           qid       (:wikidata providers)]
       {:by-reep-id
        (assoc by-reep-id reep-id entity)

        :by-provider
        (reduce-kv
         (fn [idx provider-kw provider-id]
           (if provider-id
             (assoc idx [provider-kw provider-id] reep-id)
             idx))
         by-provider
         providers)

        :by-qid
        (if qid
          (assoc by-qid qid reep-id)
          by-qid)

        :by-name
        (reduce
         (fn [idx name-str]
           (let [normalized (normalize-name name-str)]
             (if (seq normalized)
               (update idx normalized (fnil conj #{}) reep-id)
               idx)))
         by-name
         (entity-names entity))}))
   {:by-reep-id  {}
    :by-provider {}
    :by-qid      {}
    :by-name     {}}
   entities))

(defn index-stats
  "Returns entity counts per index and approximate memory info."
  [indexes]
  {:by-reep-id-count  (count (:by-reep-id indexes))
   :by-provider-count (count (:by-provider indexes))
   :by-qid-count      (count (:by-qid indexes))
   :by-name-count     (count (:by-name indexes))})
