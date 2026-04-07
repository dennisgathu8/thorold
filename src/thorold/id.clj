(ns thorold.id
  "Reep ID generation — pure, deterministic.

   Reep ID format: reep_<type_prefix><8hex>

   Type prefixes:
     :player      → p
     :coach       → c
     :team        → t
     :competition → l
     :season      → s

   COMPATIBILITY NOTE: The original Reep project mints IDs using random UUID4
   (uuid.uuid4().hex[:8]). Existing IDs in the CSV files are the source of truth
   and cannot be regenerated. This module provides:

   1. `mint-reep-id` — random minting (matches original behavior)
   2. `reep-id`      — deterministic generation from a type + seed string
                        using SHA-256. Use for reproducible ID generation.

   Both produce the same format: reep_<prefix><8hex>."
  (:import [java.security MessageDigest]
           [java.util UUID]))

;; ---------------------------------------------------------------------------
;; Type prefix mapping
;; ---------------------------------------------------------------------------

(def ^:private type-prefixes
  "Maps entity type keywords to their single-character prefix."
  {:player      "p"
   :coach       "c"
   :team        "t"
   :competition "l"
   :season      "s"})

(def ^:private valid-types
  "Set of valid entity type keywords."
  (set (keys type-prefixes)))

;; ---------------------------------------------------------------------------
;; ID validation
;; ---------------------------------------------------------------------------

(def ^:private reep-id-pattern
  "Regex pattern for a valid Reep ID."
  #"^reep_[pctls][0-9a-f]{8}$")

(defn valid-reep-id?
  "Returns true if the string is a valid Reep ID format."
  [s]
  (boolean (and (string? s) (re-matches reep-id-pattern s))))

(defn reep-id-type
  "Extracts the entity type keyword from a Reep ID string.
   Returns nil if the ID is not valid."
  [reep-id]
  (when (valid-reep-id? reep-id)
    (case (subs reep-id 5 6)
      "p" :player
      "c" :coach
      "t" :team
      "l" :competition
      "s" :season
      nil)))

;; ---------------------------------------------------------------------------
;; Deterministic ID generation
;; ---------------------------------------------------------------------------

(defn reep-id
  "Generates a stable Reep ID for the given entity type and canonical seed.

   type: :player, :coach, :team, :competition, or :season
   seed: Wikidata QID or canonical source string (e.g. \"Q615\")

   Output format: reep_<prefix><8hex> — the first 8 hex chars of SHA-256(seed).

   This function is pure and deterministic: same inputs always produce the
   same output. No side effects, no randomness, no state.

   COMPATIBILITY WARNING: The original Reep project uses random UUID4 for ID
   generation. IDs produced by this function will NOT match the existing CSV
   data. Use this for new reproducible ID schemes only. For existing entities,
   always use the ID from the CSV file."
  [type seed]
  (when-not (valid-types type)
    (throw (ex-info "Invalid entity type for Reep ID generation"
                    {:thorold/error-type :invalid-type
                     :type               type
                     :valid-types        valid-types})))
  (when-not (and (string? seed) (seq seed))
    (throw (ex-info "Seed must be a non-empty string"
                    {:thorold/error-type :invalid-seed
                     :seed               seed})))
  (let [prefix  (type-prefixes type)
        digest  (MessageDigest/getInstance "SHA-256")
        hash    (.digest digest (.getBytes (str seed) "UTF-8"))
        hex8    (apply str (map #(format "%02x" (bit-and % 0xff)) (take 4 hash)))]
    (str "reep_" prefix hex8)))

;; ---------------------------------------------------------------------------
;; Random ID minting (matches original Reep behavior)
;; ---------------------------------------------------------------------------

(defn mint-reep-id
  "Mints a new random Reep ID for the given entity type.

   Uses UUID4 random generation, matching the original Reep implementation:
     hex8 = uuid.uuid4().hex[:8]
     return f'reep_{prefix}{hex8}'

   This is NOT deterministic — each call produces a different ID.
   Use for minting new entity IDs only."
  [type]
  (when-not (valid-types type)
    (throw (ex-info "Invalid entity type for Reep ID minting"
                    {:thorold/error-type :invalid-type
                     :type               type
                     :valid-types        valid-types})))
  (let [prefix (type-prefixes type)
        hex8   (subs (.replace (str (UUID/randomUUID)) "-" "") 0 8)]
    (str "reep_" prefix hex8)))

(defn mint-unique-reep-id
  "Mints a new random Reep ID that is guaranteed not to collide with the
   given set of existing IDs. Retries up to 10 times (matching original).
   Throws on collision exhaustion."
  [type existing-ids]
  (loop [attempts 0]
    (if (>= attempts 10)
      (throw (ex-info "Failed to generate unique Reep ID after 10 attempts"
                      {:thorold/error-type :id-collision
                       :type               type}))
      (let [id (mint-reep-id type)]
        (if (contains? existing-ids id)
          (recur (inc attempts))
          id)))))
