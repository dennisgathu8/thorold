# Thorold
[![CI](https://github.com/dennisgathu8/thorold/actions/workflows/ci.yml/badge.svg?branch=rewrite/thorold)](https://github.com/dennisgathu8/thorold/actions/workflows/ci.yml)

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
- `data/competitions.csv` — Competitions mapping (not yet included — loaded automatically once present)
- `data/seasons.csv` — Seasons mapping (not yet included — loaded automatically once present)
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

#### `POST /batch/lookup`
Allows batch lookup of up to 100 entities in a single request by Reep ID or Wikidata QID.
```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"ids": ["reep_p2804f5db", "Q19080", "invalid"]}' \
  "http://localhost:8080/batch/lookup"
```
```json
{
  "results": [...],
  "count": 2,
  "not_found": 1
}
```

#### `POST /batch/resolve`
Allows batch resolution of up to 100 provider/ID pairs in a single request.
```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"items": [{"provider": "transfermarkt", "id": "568177"}, {"provider": "wikidata", "id": "Q19080"}]}' \
  "http://localhost:8080/batch/resolve"
```

#### `GET /schema/person`
Returns the Malli-derived JSON Schema for validating Person entities.
```bash
curl "http://localhost:8080/schema/person"
```

#### `GET /schema/team`
Returns the Malli-derived JSON Schema for validating Team entities.
```bash
curl "http://localhost:8080/schema/team"
```

### Content Negotiation
The API supports content negotiation via the standard `Accept` header. By default, responses are served as JSON. To request raw, lossless EDN for Clojure/ClojureScript consumers:
```bash
curl -H "Accept: application/edn" "http://localhost:8080/stats"
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

## Building a Native Binary

Thorold can be compiled into a standalone, self-contained native executable using GraalVM. This eliminates JVM startup overhead and provides a zero-dependency binary suitable for CLI tools and serverless environments. This is a local-only build process — CI runs on a standard JDK without GraalVM installed.

### Requirements
- **GraalVM 21+** (Java 21)
- The `native-image` tool installed via GraalVM (`gu install native-image` if not bundled)

### Compile
Run the build script:
```bash
./scripts/build-native.sh
```

This clean builds the project, generates an AOT-compiled uberjar, and compiles it via `native-image` using the configurations stored in `native-image-config/`.

### Expected Performance
*Note: Because GraalVM is not installed on the current host, these metrics are unverified projections:*
- **Build Time**: Expected ~1–2 minutes on standard hardware.
- **Binary Size**: Expected ~30 MB (fully standalone).
- **Startup Time**: Expected ~5–10 ms (vs ~1.5 seconds on JVM).

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

## Usage with Python, R, and SQL

The CSV files work with any data tool. No Clojure required.

### Python

```python
import csv

people = {}
with open("data/people.csv") as f:
    for row in csv.DictReader(f):
        tm_id = row["key_transfermarkt"]
        if tm_id:
            people[tm_id] = row

# Cole Palmer's FBref ID from his Transfermarkt ID
palmer = people["568177"]
print(palmer["key_fbref"])  # "dc7f8a28"
```

### R

```r
library(readr)
people <- read_csv("data/people.csv")

# All Premier League-registered players
pl_players <- people |> filter(key_premier_league != "")

# Cross-reference: Transfermarkt -> FBref
people |>
  filter(key_transfermarkt == "568177") |>
  select(name, key_fbref, key_sofascore)
```

### SQL (SQLite)

```bash
sqlite3 thorold.db <<EOF
.mode csv
.import data/people.csv people
.import data/teams.csv teams
.import data/names.csv names
EOF
```

```sql
SELECT * FROM people WHERE name LIKE '%Salah%';
SELECT * FROM people WHERE key_fbref = 'e342ad68';
```

## Provider Coverage

| Provider | Coverage | Source | Notes |
| --- | --- | --- | --- |
| Transfermarkt | Best | Wikidata | Highest coverage across all entities |
| FBref | Good | Wikidata | Strong for recent players |
| Soccerway | Good | Wikidata | Broad international coverage |
| Sofascore | Good | Wikidata | Modern players well covered |
| Opta | Sparse | Wikidata | Few entries in Wikidata |
| Premier League | Decent | Wikidata | PL players only |
| Understat | ~2.3K | Custom | Matched via Transfermarkt bridge |
| WhoScored | ~2.3K | Custom | Matched via Transfermarkt bridge |
| SportMonks | ~600 | Custom | Players and teams via TM bridge |
| API-Football | Growing | Custom | Name and DOB matching |
| Club Elo | ~176 teams | Custom | Manual team mapping |
| FotMob | ~4.6K | Custom | DOB and name matching, top 6 leagues |

## Wikidata Properties

All provider IDs are sourced from these Wikidata properties:

| Property | Provider |
| --- | --- |
| [P2446](https://www.wikidata.org/wiki/Property:P2446) | Transfermarkt player ID |
| [P2447](https://www.wikidata.org/wiki/Property:P2447) | Transfermarkt manager ID |
| [P7223](https://www.wikidata.org/wiki/Property:P7223) | Transfermarkt team ID |
| [P5750](https://www.wikidata.org/wiki/Property:P5750) | FBref player ID |
| [P8642](https://www.wikidata.org/wiki/Property:P8642) | FBref squad ID |
| [P2369](https://www.wikidata.org/wiki/Property:P2369) | Soccerway person ID |
| [P6131](https://www.wikidata.org/wiki/Property:P6131) | Soccerway team ID |
| [P12302](https://www.wikidata.org/wiki/Property:P12302) | Sofascore player ID |
| [P8259](https://www.wikidata.org/wiki/Property:P8259) | Flashscore player ID |
| [P8736](https://www.wikidata.org/wiki/Property:P8736) | Opta player ID |
| [P8737](https://www.wikidata.org/wiki/Property:P8737) | Opta team ID |
| [P12539](https://www.wikidata.org/wiki/Property:P12539) | Premier League player ID |
| [P12551](https://www.wikidata.org/wiki/Property:P12551) | 11v11 player ID |
| [P3681](https://www.wikidata.org/wiki/Property:P3681) | ESPN FC player ID |
| [P2574](https://www.wikidata.org/wiki/Property:P2574) | National Football Teams ID |
| [P2020](https://www.wikidata.org/wiki/Property:P2020) | WorldFootball.net ID |
| [P2193](https://www.wikidata.org/wiki/Property:P2193) | Soccerbase player ID |
| [P2276](https://www.wikidata.org/wiki/Property:P2276) | UEFA player ID |
| [P7361](https://www.wikidata.org/wiki/Property:P7361) | UEFA team ID |
| [P3665](https://www.wikidata.org/wiki/Property:P3665) | L'Equipe player ID |
| [P9264](https://www.wikidata.org/wiki/Property:P9264) | FFF.fr player ID |
| [P13064](https://www.wikidata.org/wiki/Property:P13064) | Lega Serie A player ID |
| [P12577](https://www.wikidata.org/wiki/Property:P12577) | BeSoccer player ID |
| [P3537](https://www.wikidata.org/wiki/Property:P3537) | FootballDatabase.eu person ID |
| [P7351](https://www.wikidata.org/wiki/Property:P7351) | FootballDatabase.eu team ID |
| [P3726](https://www.wikidata.org/wiki/Property:P3726) | EU-Football.info player ID |
| [P12606](https://www.wikidata.org/wiki/Property:P12606) | Barry Hugman's Footballers ID |
| [P4023](https://www.wikidata.org/wiki/Property:P4023) | German FA person ID |
| [P12567](https://www.wikidata.org/wiki/Property:P12567) | StatMuse PL player ID |
| [P12312](https://www.wikidata.org/wiki/Property:P12312) | Kicker team ID |
| [P7876](https://www.wikidata.org/wiki/Property:P7876) | Flashscore team ID |
| [P13897](https://www.wikidata.org/wiki/Property:P13897) | Sofascore team ID |
| [P7454](https://www.wikidata.org/wiki/Property:P7454) | Soccerbase team ID |
| [P7287](https://www.wikidata.org/wiki/Property:P7287) | WorldFootball.net team ID |
| [P1469](https://www.wikidata.org/wiki/Property:P1469) | SoFIFA / EA FC player ID |
| [P4381](https://www.wikidata.org/wiki/Property:P4381) | Soccerdonna player ID |
| [P8134](https://www.wikidata.org/wiki/Property:P8134) | Soccerdonna coach ID |
| [P11379](https://www.wikidata.org/wiki/Property:P11379) | Dongqiudi player ID |
| [P7280](https://www.wikidata.org/wiki/Property:P7280) | PlaymakerStats team ID |

The best way to improve coverage is to add missing provider IDs directly to Wikidata. The weekly data refresh picks them up automatically.

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
