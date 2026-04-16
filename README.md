# Thorold

**The football entity register, in Clojure.**

Named after **Thorold Charles Reep (1904–2002)**, the founding father of football analytics.
An RAF wing commander who sat in stadium stands with a miner's helmet illuminating his
notebook, Reep hand-recorded every action in over 2,200 football matches starting in the 1950s.
Decades before expected goals or tracking data, he was tallying passes, shots, and sequences
with pen and paper — pioneering the idea that football could be understood through data.

Thorold is his modern successor: a canonical identity register that brings order to football's
fragmented data landscape. Every player, coach, team, competition, and season gets a stable
**Reep ID** (`reep_<type><8hex>`), linked to their IDs on 40+ data providers including
Transfermarkt, FBref, Sofascore, Opta, WhoScored, and Wikidata.

This is a pure Clojure re-implementation of [Reep](https://github.com/withqwerty/reep).
Same data. Same IDs. Better architecture.

## Why Clojure?

> Football data is maps and sequences. Clojure is the language of maps and sequences.

- Every entity is a plain Clojure map with namespaced keywords
- The entire database is a single immutable value — one map of maps
- All indexes are derived via pure functions
- Two database snapshots can be diffed with `clojure.data/diff`
- The ingestion pipeline is a composed transducer chain
- Queries are pure functions — no hidden state

## Prerequisites

- **Java 17+** (OpenJDK recommended)
- **Clojure CLI** 1.11+ (`clojure` / `clj`)

## Installation

```bash
git clone https://github.com/dennisgathu8/thorold.git
cd thorold
```

The CSV data files are included in `data/`:
- `data/people.csv` — 429,785 players and coaches
- `data/teams.csv` — 45,349 clubs
- `data/names.csv` — Aliases/alternate names
- `data/meta.json` — Build metadata

## CLI Usage

The CLI binary name is `thorold`. All commands operate on the local CSV data.

### Search by name
```bash
thorold search "Lionel Messi"
thorold search "Arsenal" --type team
thorold search "Guardiola" --type coach --limit 5
```

### Resolve a provider ID to all IDs
```bash
thorold resolve transfermarkt 28003
thorold resolve fbref dc7f8a28
```

### Translate between providers (pipe-friendly)
```bash
thorold translate transfermarkt 568177 fbref
# → dc7f8a28
```

### Look up by Reep ID or Wikidata QID
```bash
thorold lookup reep_p2804f5db
thorold lookup Q615
```

### Download latest CSVs
```bash
thorold download
```

### Database statistics
```bash
thorold stats
```

### Output formats
All commands support `--format`:
```bash
thorold search "Salah" --format edn
thorold resolve transfermarkt 28003 --format json
thorold stats --format human  # default
```

## API Usage

Start the API server:
```bash
clj -M:api
```

### Endpoints

#### `GET /search`
```bash
curl "http://localhost:8080/search?name=Cole+Palmer&type=player"
```
```json
{
  "results": [{
    "reep_id": "reep_p2804f5db",
    "qid": "Q99760796",
    "type": "player",
    "name": "Cole Palmer",
    "providers": {
      "transfermarkt": "568177",
      "fbref": "dc7f8a28",
      "sofascore": "982780"
    }
  }],
  "count": 1
}
```

#### `GET /resolve`
```bash
curl "http://localhost:8080/resolve?provider=transfermarkt&id=568177"
```

#### `GET /lookup`
```bash
curl "http://localhost:8080/lookup?id=reep_p2804f5db"
curl "http://localhost:8080/lookup?id=Q615"
```

#### `GET /stats`
```bash
curl "http://localhost:8080/stats"
```

## REPL Quickstart

```bash
clj -M:dev
```

Then evaluate `dev/repl_demo.clj` top-to-bottom. See that file for a literate
walkthrough of the entire system.

```clojure
(require '[thorold.db :as db])
(require '[thorold.query :as q])

(def db (db/load-db "data/"))

;; Search
(q/search db "Erling Haaland" {:type :player})

;; Resolve: Transfermarkt ID → full entity
(q/resolve db :transfermarkt "418560")

;; Translate: Reep ID → FBref ID
(q/translate db "reep_p2804f5db" :fbref)

;; Diff two snapshots
(clojure.data/diff db-v1 db-v2)
```

## Testing & Performance

Thorold features a comprehensive test suite covering all modules and API endpoints, alongside an immutable model benchmark suite powered by Criterium.

**Run tests:**
```bash
clj -M:test
```

**Run benchmarks:**
```bash
clj -M:bench
```
On typical hardware, fuzzy name searches resolve in `<2µs`, while direct ID lookups and provider resolutions complete in `<100ns`. Continuous integration via GitHub Actions automatically caches Clojure dependencies and verifies all builds.

## Provider List

Thorold maps IDs across 40+ football data providers:

| Provider | Key | Example ID |
|----------|-----|------------|
| Transfermarkt | `transfermarkt` | `568177` |
| FBref | `fbref` | `dc7f8a28` |
| Sofascore | `sofascore` | `982780` |
| Opta / Stats Perform | `opta` | `7cwgrmorsb42qaj5vrhp8fhzp` |
| WhoScored | `whoscored` | `456789` |
| Understat | `understat` | `1234` |
| FotMob | `fotmob` | `292462` |
| Wyscout | `wyscout` | `234966` |
| SkillCorner | `skillcorner` | `23959` |
| SoccerWay | `soccerway` | `525801` |
| Flashscore | `flashscore` | `palmer-cole/h8agbDt7` |
| UEFA | `uefa` | (numeric) |
| Premier League | `premier_league` | `49293` |
| ESPN | `espn` | (numeric) |
| Kicker | `kicker` | `cole-palmer` |
| Capology | `capology` | `cole-palmer-36271` |
| Club Elo | `clubelo` | `Arsenal` |
| SportMonks | `sportmonks` | `12345` |
| API-Football | `api_football` | `1100` |
| SoFIFA | `sofifa` | (numeric) |
| TheSportsDB | `thesportsdb` | `34146086` |
| Impect | `impect` | `52615` |
| heim:spiel | `heimspiel` | `361032` |
| ... and 20+ more | | |

## Reep ID Format

Every entity has a self-minted Reep ID as its canonical identifier:

```
reep_<type_prefix><8hex>
```

| Type | Prefix | Example |
|------|--------|---------|
| Player | `p` | `reep_p2804f5db` |
| Team | `t` | `reep_t0871097b` |
| Coach | `c` | `reep_c9103de59` |
| Competition | `l` | `reep_lb3d230cb` |
| Season | `s` | `reep_sa7f63ba6` |

Reep IDs are **stable** — they never change, even if a player's Wikidata QID is merged
or deleted. The format is preserved exactly from the original Reep project for full
backward compatibility.

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `THOROLD_DATA_DIR` | No | `data/` | Path to the directory containing CSV data files |
| `PORT` | No | `8080` | Port for the REST API server |

All non-secret configuration is in `resources/config.edn`.

## Project Structure

```
thorold/
├── src/thorold/       ← Source modules
│   ├── core.clj       ← Entry point
│   ├── model.clj      ← Malli schemas
│   ├── id.clj         ← Reep ID generation
│   ├── parse.clj      ← CSV parsers
│   ├── index.clj      ← Index builders
│   ├── db.clj         ← Database assembly
│   ├── query.clj      ← Search, resolve, translate, lookup
│   ├── ingest.clj     ← Wikidata SPARQL pipeline
│   ├── cli.clj        ← CLI commands
│   └── api.clj        ← Ring/Reitit REST API
├── test/thorold/      ← Tests for every module
├── bench/thorold/     ← Performance benchmarks (Criterium)
├── dev/               ← REPL utilities (never in production jar)
├── data/              ← Read-only source CSVs
├── output/            ← Generated files (gitignored)
├── resources/         ← config.edn
└── docs/              ← Architecture docs
```

## Contributing

1. Fork the repo
2. Create a feature branch
3. Write tests for any new functionality
4. Run `clj-kondo --lint src test bench` to ensure 0 errors and warnings
5. Ensure `clj -M:test` passes
6. Validate performance overhead with `clj -M:bench`
7. Submit a PR

**Data contributions:** The CSV data files are regenerated weekly from Wikidata.
Do not submit PRs modifying data files directly. If you have ID mappings to contribute,
open an issue with your CSV attached.

**Editing Wikidata:** The best way to add missing provider IDs is to edit the entity's
[Wikidata](https://www.wikidata.org/) page directly — the next weekly build picks it up
automatically.

## License

Data derived from [Wikidata](https://www.wikidata.org/) under
[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
