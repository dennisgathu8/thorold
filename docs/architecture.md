# Architecture

## Central Thesis

> Football data is maps and sequences. Clojure is the language of maps and sequences.
> The architecture should make that identity obvious.

## Design Decisions

### 1. Every entity is a plain Clojure map

No classes, no records, no protocols for data. A player is a map:

```clojure
{:reep/id      "reep_p2804f5db"
 :reep/type    :person/player
 :person/name  "Cole Palmer"
 :person/dob   "2002-05-06"
 :providers    {:transfermarkt "568177"
                :fbref         "dc7f8a28"
                :sofascore     "982780"}}
```

### 2. The database is one immutable value

No global atoms, no connection pools, no ORM. The entire loaded database is a single
Clojure map:

```clojure
{:entities  {reep-id → entity-map, ...}
 :indexes   {:by-reep-id  {id → entity}
             :by-provider {[provider id] → reep-id}
             :by-qid      {qid → reep-id}
             :by-name     {normalized → #{reep-id ...}}}}
```

The caller holds this value and passes it to query functions. Two snapshots
can be compared with `clojure.data/diff`.

### 3. Indexes are derived, not stored

All four indexes are built in a single `reduce` pass over the entity collection.
They share structure with the entity maps via Clojure's persistent data structures.

### 4. I/O at the boundary only

CSV parsing (`thorold.parse`) reads files and produces sequences of maps.
Everything downstream is pure. Query functions take the DB value and return
results — no I/O, no side effects.

### 5. Transducer-based ingestion

The Wikidata pipeline is a composed chain of transducers. Each stage
(parse → normalize → validate → deduplicate → assign ID) is independently
testable. The composition never materializes intermediate collections.

### 6. Provider IDs as a nested map

Instead of 40+ flat `key_*` columns, all provider IDs are collected under a single
`:providers` key. This makes provider operations uniform and schema-driven.

## Tradeoffs

| Decision | Benefit | Cost |
|----------|---------|------|
| In-memory DB | Fast queries, simple code | ~2GB RAM for full dataset |
| No SQL | No schema migrations, easy diffing | No ad-hoc queries |
| Random IDs | Matches original Reep | Can't regenerate from entity data |
| Malli schemas | Runtime validation, generative testing | Extra dependency |
| Single-pass indexing | Performance | More complex reduce function |
