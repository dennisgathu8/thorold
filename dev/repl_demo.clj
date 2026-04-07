;; Thorold — REPL Demo
;; Named after Thorold Charles Reep (1904–2002), who recorded football's first
;; systematic notation with a pencil and miner's helmet. This is his successor.
;;
;; This file is a first-class deliverable — a literate walkthrough evaluable
;; top-to-bottom in a fresh REPL. Every block is commented explaining what it
;; shows and why it matters for the Clojure-as-data-engineering argument.

(ns repl-demo
  (:require [thorold.db :as db]
            [thorold.query :as q]
            [thorold.index :as idx]
            [thorold.model :as model]
            [thorold.id :as id]
            [thorold.ingest :as ingest]
            [clojure.data :as data]))

;; ============================================================================
;; 1. The entire database as one immutable value
;; ============================================================================
;;
;; Football data is maps and sequences. The entire Thorold database loads into
;; a single Clojure map. No ORM, no connection pool, no global state. Just data.

(def db (db/load-db "data/"))

;; What does the database look like?
(keys db)
;; => (:entities :indexes :names :meta)

;; How many entities?
(count (:entities db))
;; => ~475,000

;; What's in :meta?
(:meta db)
;; => {:people-count 429785, :teams-count 45349, :names-count 0, :load-ms ...}

;; ============================================================================
;; 2. Search, resolve, translate — pure functions over that value
;; ============================================================================
;;
;; Every query function is pure: it takes the DB value and returns a result.
;; No hidden state, no side effects, no network calls. You can call these
;; functions in tests, in the REPL, or in production — they behave identically.

;; Search by name — fuzzy matching with relevance scoring
(q/search db "Erling Haaland" {:type :person/player})

;; Resolve: "I have a Transfermarkt ID, who is this?"
(q/resolve db :transfermarkt "28003")

;; Translate: Transfermarkt ID → FBref ID (pipe-friendly, returns just the string)
(q/translate db "reep_p2804f5db" :fbref)
;; => "dc7f8a28"

;; Lookup by Reep ID
(q/lookup db "reep_p2804f5db")

;; Lookup by Wikidata QID
(q/lookup db "Q615")

;; ============================================================================
;; 3. Two snapshots, one diff — structural comparison for free
;; ============================================================================
;;
;; Because the database is immutable data, you can compare two versions
;; with a single function call. This is Clojure's killer feature for
;; data engineering — no custom diff logic needed.

(def db-v1 db)

;; Simulate a change: add a new provider ID for Messi
(def db-v2
  (let [messi-id "reep_pbd9a559b"
        updated-entity (assoc-in (get (:entities db) messi-id)
                                 [:providers :whoscored] "11119")]
    (assoc-in db [:entities messi-id] updated-entity)))

;; What changed?
(let [[only-in-v1 only-in-v2 _both] (data/diff (:entities db-v1) (:entities db-v2))]
  {:removed only-in-v1
   :added   only-in-v2})
;; Shows exactly which entity changed and what the old/new values are

;; ============================================================================
;; 4. The ingestion pipeline over a fixture
;; ============================================================================
;;
;; The Wikidata SPARQL pipeline is a composed transducer chain.
;; Each stage is a separate transducer — composable and independently testable.
;; No intermediate collections are ever materialized.

(def sparql-fixture
  [{"item"       {"value" "http://www.wikidata.org/entity/Q615"}
    "itemLabel"  {"value" "Lionel Messi"}
    "type"       {"value" "player"}
    "dob"        {"value" "1987-06-24"}
    "id_transfermarkt" {"value" "28003"}
    "id_fbref"   {"value" "d70ce98e"}}])

;; Run the pipeline — each stage is visible and inspectable
(def new-entities (into [] (ingest/ingest-pipeline) sparql-fixture))

;; Merge into the existing DB — pure function, returns [updated-db manifest]
(let [[updated-db manifest] (ingest/merge-into-db db new-entities)]
  (println "Manifest:" manifest)
  ;; => {:added 0 :updated 1 :skipped 0 :conflicts []}
  )

;; ============================================================================
;; 5. Index construction — visible, inspectable, pure
;; ============================================================================
;;
;; All four indexes are built in a single reduce pass. No magic, no hidden
;; schema, no query language. Just Clojure maps.

(def indexes (idx/build-indexes (vals (:entities db))))
(idx/index-stats indexes)
;; => {:by-reep-id-count 475134, :by-provider-count ..., :by-qid-count ..., :by-name-count ...}

;; The name index uses normalization for diacritics-insensitive matching
(idx/normalize-name "José Mourinho")
;; => "jose mourinho"

(idx/normalize-name "Müller")
;; => "muller"

;; ============================================================================
;; 6. Entity shape — just data, all the way down
;; ============================================================================
;;
;; An entity is a plain Clojure map. No classes, no protocols.
;; Provider IDs are nested under :providers — not 40+ flat columns.

(q/lookup db "reep_p2804f5db")
;; => {:reep/id "reep_p2804f5db"
;;     :reep/type :person/player
;;     :person/name "Cole Palmer"
;;     :person/dob "2002-05-06"
;;     :providers {:transfermarkt "568177"
;;                 :fbref "dc7f8a28"
;;                 :sofascore "982780"
;;                 :wikidata "Q99760796"
;;                 ...}}

;; Validate against the schema
(model/valid? (q/lookup db "reep_p2804f5db"))
;; => true

;; ============================================================================
;; 7. Reep IDs — the backbone
;; ============================================================================

;; Format: reep_<type_prefix><8hex>
(id/valid-reep-id? "reep_p2804f5db")
;; => true

(id/reep-id-type "reep_p2804f5db")
;; => :player

;; Deterministic generation from a seed
(id/reep-id :player "Q615")
;; => "reep_p<8hex>" — same every time

;; Random minting (matches original Reep behavior)
(id/mint-reep-id :player)
;; => "reep_p<random8hex>" — different every time

;; Database statistics
(db/db-stats db)
