(ns thorold.parse
  "CSV/EDN parsers — I/O boundary only.

   Core functions take a sequence of rows (header + data), not a filename.
   I/O (opening files, reading bytes) happens at the boundary in the
   public `parse-*-file` functions. Everything else is pure.

   All key_* columns are collected into a :providers map.
   Missing values become nil, not empty strings.
   Malformed rows are collected into a :parse/errors log — the pipeline
   never stops on bad data."
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [thorold.model :as model]))

;; ---------------------------------------------------------------------------
;; Internal helpers — all pure
;; ---------------------------------------------------------------------------

(defn- blank->nil
  "Converts empty strings to nil."
  [s]
  (when-not (str/blank? s)
    s))

(defn- parse-height
  "Parses height string to a number. Returns nil on failure."
  [s]
  (when-let [s (blank->nil s)]
    (try
      (let [v (Double/parseDouble s)]
        (if (== v (Math/floor v))
          (long v)
          v))
      (catch Exception _ nil))))

(defn- collect-providers
  "Collects all key_* columns into a {:provider-kw value} map.
   Removes nil values. Uses the given provider-key-map to translate
   CSV column names to keywords."
  [row provider-key-map]
  (persistent!
   (reduce-kv
    (fn [acc csv-col kw]
      (if-let [v (blank->nil (get row csv-col))]
        (assoc! acc kw v)
        acc))
    (transient {})
    provider-key-map)))

(defn- add-wikidata-to-providers
  "Adds the :wikidata key to the providers map if key_wikidata is present."
  [providers row]
  (if-let [qid (blank->nil (get row "key_wikidata"))]
    (assoc providers :wikidata qid)
    providers))

;; ---------------------------------------------------------------------------
;; Person parsing
;; ---------------------------------------------------------------------------

(defn- parse-person-row
  "Parses a single CSV row map into a person entity map.
   Returns the entity map or nil if the row is invalid."
  [row]
  (try
    (let [reep-id (blank->nil (get row "reep_id"))
          name    (blank->nil (get row "name"))
          type-str (blank->nil (get row "type"))]
      (when (and reep-id name type-str)
        (let [entity-type (case type-str
                            "player" :person/player
                            "coach"  :person/coach
                            nil)]
          (when entity-type
            {:reep/id                reep-id
             :reep/type              entity-type
             :person/name            name
             :person/full-name       (blank->nil (get row "full_name"))
             :person/dob             (blank->nil (get row "date_of_birth"))
             :person/nationality     (blank->nil (get row "nationality"))
             :person/position        (blank->nil (get row "position"))
             :person/position-detail (blank->nil (get row "position_detail"))
             :person/height-cm       (parse-height (get row "height_cm"))
             :providers              (-> (collect-providers row model/person-provider-keys)
                                         (add-wikidata-to-providers row))}))))
    (catch Exception _
      nil)))

(defn parse-people
  "Parses a sequence of CSV row maps into a sequence of person entity maps.
   Rows that fail parsing are silently dropped (collected via parse-people-with-errors).
   Never materializes the full collection into memory — returns a lazy sequence."
  [row-maps]
  (keep parse-person-row row-maps))

(defn parse-people-with-errors
  "Parses a sequence of CSV row maps, returning {:entities [...] :errors [...]}.
   Uses transduce to run in a single pass without materialising intermediate sequences."
  [row-maps]
  (transduce
   (map (fn [row]
          (if-let [e (parse-person-row row)]
            {:ok true  :entity e}
            {:ok false :row row})))
   (fn
     ([] {:entities (transient []) :errors (transient [])})
     ([acc item]
      (if (:ok item)
        (update acc :entities conj! (:entity item))
        (update acc :errors  conj! (:row item))))
     ([acc]
      {:entities (persistent! (:entities acc))
       :errors   (persistent! (:errors acc))}))
   row-maps))

;; ---------------------------------------------------------------------------
;; Team parsing
;; ---------------------------------------------------------------------------

(defn- parse-team-row
  "Parses a single CSV row map into a team entity map."
  [row]
  (try
    (let [reep-id (blank->nil (get row "reep_id"))
          name    (blank->nil (get row "name"))]
      (when (and reep-id name)
        {:reep/id       reep-id
         :reep/type     :team
         :team/name     name
         :team/country  (blank->nil (get row "country"))
         :team/founded  (blank->nil (get row "founded"))
         :team/stadium  (blank->nil (get row "stadium"))
         :providers     (-> (collect-providers row model/team-provider-keys)
                            (add-wikidata-to-providers row))}))
    (catch Exception _
      nil)))

(defn parse-teams
  "Parses a sequence of CSV row maps into a sequence of team entity maps."
  [row-maps]
  (keep parse-team-row row-maps))

(defn parse-teams-with-errors
  "Parses a sequence of CSV row maps, returning {:entities [...] :errors [...]}.
   Uses transduce to run in a single pass without materialising intermediate sequences."
  [row-maps]
  (transduce
   (map (fn [row]
          (if-let [e (parse-team-row row)]
            {:ok true  :entity e}
            {:ok false :row row})))
   (fn
     ([] {:entities (transient []) :errors (transient [])})
     ([acc item]
      (if (:ok item)
        (update acc :entities conj! (:entity item))
        (update acc :errors  conj! (:row item))))
     ([acc]
      {:entities (persistent! (:entities acc))
       :errors   (persistent! (:errors acc))}))
   row-maps))

;; ---------------------------------------------------------------------------
;; Names parsing
;; ---------------------------------------------------------------------------

(defn- parse-name-row
  "Parses a single CSV row map into a name alias map."
  [row]
  (try
    (let [qid   (blank->nil (get row "key_wikidata"))
          name  (blank->nil (get row "name"))
          alias (blank->nil (get row "alias"))]
      (when (and qid name alias)
        {:name/qid   qid
         :name/name  name
         :name/alias alias}))
    (catch Exception _
      nil)))

(defn parse-names
  "Parses a sequence of CSV row maps into a sequence of name alias maps."
  [row-maps]
  (keep parse-name-row row-maps))

;; ---------------------------------------------------------------------------
;; CSV I/O boundary — these are the only functions that touch the filesystem
;; ---------------------------------------------------------------------------

(defn- csv->row-maps-seq
  "Returns a lazy seq of row maps from an open reader.
   The caller is responsible for keeping the reader open during consumption."
  [reader]
  (let [data   (csv/read-csv reader)
        header (first data)
        rows   (rest data)]
    (map #(zipmap header %) rows)))

(defn parse-people-file
  "Streams people.csv through the person transducer.
   Never materialises the full 488k collection.
   Returns {:entities [...] :errors [...] :count N}"
  [data-dir]
  (with-open [reader (io/reader (str data-dir "people.csv"))]
    (let [result (parse-people-with-errors (csv->row-maps-seq reader))]
      (assoc result :count (count (:entities result))))))

(defn parse-teams-file
  "Streams teams.csv through the team transducer.
   Never materialises the full collection."
  [data-dir]
  (with-open [reader (io/reader (str data-dir "teams.csv"))]
    (let [result (parse-teams-with-errors (csv->row-maps-seq reader))]
      (assoc result :count (count (:entities result))))))

(defn parse-names-file
  "I/O boundary. Opens data-dir/names.csv and returns parsed names."
  [data-dir]
  (with-open [reader (io/reader (str data-dir "names.csv"))]
    (let [rows  (csv->row-maps-seq reader)
          names (vec (parse-names rows))]
      {:names names :count (count names)})))
