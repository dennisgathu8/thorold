;; Thorold — REPL Demo
;; Named after Thorold Charles Reep (1904–2002), who recorded football's first
;; systematic notation with a pencil and miner's helmet. This is his successor.
;;
;; This file is a first-class deliverable — a literate walkthrough evaluable
;; top-to-bottom in a fresh REPL. Every block explains what it shows and why
;; it matters for the Clojure-as-data-engineering argument.
;;
;; Start a REPL with: clj -M:dev
;; Then evaluate each block in order.

(ns repl-demo
  (:require [thorold.db    :as db]
            [thorold.query :as q]
            [thorold.index :as idx]
            [thorold.model :as model]
            [thorold.id    :as id]
            [thorold.ingest :as ingest]
            [clojure.data  :as data]))

;; ============================================================================
;; BLOCK 1 — The entire database as one immutable value
;;
;; This is the central argument. The entire Thorold dataset — ~475k entities
;; across players, coaches, teams, competitions and seasons — is loaded into
;; a single Clojure map. It is an ordinary value: you can pass it to functions,
;; hold multiple versions simultaneously, and inspect it at the REPL instantly.
;; No ORM. No connection pool. No query language. Just a map.
;; ============================================================================

(def db (db/load-db "data/"))

;; The database is a plain map. Inspect its top-level keys:
(keys db)
;; => (:entities :indexes :names :meta)

;; How many entities are loaded?
(count (:entities db))
;; => ~475134

;; The :meta key carries load diagnostics:
(:meta db)
;; => {:people-count ... :teams-count ... :load-ms ...}

;; ============================================================================
;; BLOCK 2 — Queries are pure functions over that value
;;
;; search, resolve, translate, and lookup are all pure functions. They take
;; the db value and return results. No side effects, no network, no state.
;; You can run them in a test, in a pipeline, or at the REPL identically.
;; ============================================================================

;; Search by name — fuzzy, case-insensitive, diacritic-tolerant
(q/search db "Lionel Messi" {:limit 3})

;; Resolve a provider ID to a Thorold entity
(q/resolve db :transfermarkt "28003")

;; Translate a Reep ID to a specific provider's ID
(q/translate db "reep_pbd9a559b" :fbref)

;; Look up directly by Reep ID
(q/lookup db "reep_pbd9a559b")

;; Look up by Wikidata QID — same function, different input shape
(q/lookup db "Q615")

;; ============================================================================
;; BLOCK 3 — The providers map: 40+ providers, one clean structure
;;
;; In the original Reep system, provider IDs were 40+ flat string columns in
;; a SQL table. In Thorold they live under a single :providers map per entity.
;; Adding a new provider is adding a key. No schema migration required.
;; ============================================================================

(def messi (q/lookup db "Q615"))

;; All provider IDs for one entity, as a plain Clojure map:
(:providers messi)

;; Translate to any provider with a simple get:
(get-in messi [:providers :sofascore])
(get-in messi [:providers :wyscout])
(get-in messi [:providers :fbref])

;; ============================================================================
;; BLOCK 4 — Diffing two snapshots: structural comparison for free
;;
;; Because the database is an immutable value, diffing two snapshots is a
;; single function call. clojure.data/diff returns [only-in-a, only-in-b,
;; in-both]. This gives you a changelog between any two database versions
;; with zero infrastructure — no event sourcing, no audit tables, no triggers.
;; ============================================================================

;; Simulate a database update by merging one new entity
(def updated-db
  (first (ingest/merge-into-db
          db
          [{:reep/id    "reep_p_example"
            :reep/type  :person/player
            :person/name "New Player"
            :providers   {:transfermarkt "999999" :wikidata "Q9999999"}}])))

;; Diff the two versions — what changed?
(def diff-result (data/diff (:entities db) (:entities updated-db)))

;; Only in original (nothing removed):
(count (first diff-result))

;; Only in updated (our new entity):
(keys (second diff-result))

;; In both (everything else unchanged — structural sharing at work):
(= (count (nth diff-result 2)) (count (:entities db)))

;; ============================================================================
;; BLOCK 5 — The ingestion pipeline over a fixture
;;
;; The Wikidata ingestion pipeline is a composed transducer chain. Each stage
;; is a named, independently testable transducer. The pipeline processes
;; SPARQL result bindings without ever materializing the full intermediate
;; collection. Here we run it over an inline fixture.
;; ============================================================================

(def sparql-fixture
  [{"item"      {"value" "http://www.wikidata.org/entity/Q123456"}
    "itemLabel" {"value" "Example Player"}
    "type"      {"value" "player"}
    "id_transfermarkt" {"value" "999888"}
    "id_fbref"  {"value" "abc123def"}}
   {"item"      {"value" "http://www.wikidata.org/entity/Q123456"} ;; duplicate
    "itemLabel" {"value" "Example Player Duplicate"}
    "type"      {"value" "player"}}])

;; Run the pipeline — deduplication drops the second row
(def new-entities (into [] (ingest/ingest-pipeline) sparql-fixture))
(count new-entities)
;; => 1

;; The entity is a plain Clojure map
(first new-entities)

;; Merge into the database — returns [updated-db manifest]
(def [db-v2 manifest] (ingest/merge-into-db db new-entities))
manifest
;; => {:added 1 :updated 0 :skipped 0 :conflicts []}

;; ============================================================================
;; BLOCK 6 — Index construction: visible, inspectable, pure
;;
;; All four indexes are built in a single reduce pass over the entities
;; sequence. The function is pure — give it the same entities, get the same
;; indexes. You can build them manually at the REPL to inspect the structure.
;; ============================================================================

;; Build indexes over a small hand-crafted dataset
(def sample-entities
  [{:reep/id "reep_p1" :reep/type :person/player
    :person/name "Test Player"
    :providers {:transfermarkt "111" :wikidata "Q111"}}
   {:reep/id "reep_t1" :reep/type :team
    :team/name "Test FC"
    :providers {:transfermarkt "222" :wikidata "Q222"}}])

(def sample-indexes (idx/build-indexes sample-entities))

;; Inspect each index directly:
(:by-reep-id sample-indexes)
(:by-provider sample-indexes)
(:by-qid sample-indexes)
(:by-name sample-indexes)

;; Stats: how many entries in each index?
(idx/index-stats sample-indexes)

;; ============================================================================
;; BLOCK 7 — ID generation: deterministic vs random
;;
;; The original Reep system minted IDs randomly using UUID4. Those IDs are
;; preserved in the CSV files as the source of truth. Thorold adds a
;; deterministic generator using SHA-256 for new entity IDs going forward.
;;
;; IMPORTANT: reep-id (deterministic) will NOT match existing CSV IDs.
;; See the COMPATIBILITY WARNING in thorold.id/reep-id docstring.
;; ============================================================================

;; Deterministic: same seed always produces same ID (for new entities)
(id/reep-id :player "Q_new_entity_123")
(id/reep-id :player "Q_new_entity_123") ;; identical

;; Random: matches original minting behavior for backward compatibility
(id/mint-reep-id :player) ;; different each call

;; Validate a known ID from the CSV:
(id/valid-reep-id? "reep_pbd9a559b") ;; => true
(id/reep-id-type   "reep_pbd9a559b") ;; => :player

;; ============================================================================
;; BLOCK 8 — Database statistics
;;
;; db-stats returns a plain map summarising the loaded database.
;; This is the same data shape returned by the GET /stats API endpoint.
;; ============================================================================

(db/db-stats db)
;; => {:total-entities 475134
;;     :by-type {"person/player" 387284 "team" 43202 ...}
;;     :by-provider {"wikidata" 458552 "transfermarkt" 441833 ...}
;;     :load-ms ...}
