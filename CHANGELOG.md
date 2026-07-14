# Changelog

All notable changes to Thorold are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Infrastructure
- Consolidated `.github/workflows/ci.yml` and `test.yml` into a single workflow —
  tests previously ran 2–3× per push due to overlapping triggers
- Aligned Clojure CLI version to `1.12.4.1602` across the matrix (was `1.11.1.1413` in `test.yml`)
- Added Java 11/17/21 matrix to CI (previously only Java 17 in `ci.yml`)
- Documented that native image builds are intentionally local-only (CI comment + README note)
- Updated `SECURITY.md` scope with batch endpoint 100-item cap and EDN output-only safety note

### Known Gaps
- CI does not run `clj-kondo` linting — neither workflow has ever included it

## [1.2.0] — 2026-07-13

### Added
- **Reep Schema Sync (Part A)**:
  - Added new `key_opta_numeric` to person provider keys in `model.clj`.
  - Added `competition-provider-keys` mapping for competitions.csv (7 provider mappings).
  - Added `season-provider-keys` map (empty per verified seasons.csv layout).
  - Added streaming CSV parsers for competitions and seasons in `parse.clj`.
  - Added graceful loading of competitions/seasons in `db.clj` with file fallback.
  - Added `/batch/lookup` and `/batch/resolve` POST endpoints in `api.clj` (max 100 limit, pure logic in `query.clj`).
- **Clojure-Native Enhancements (Part B)**:
  - Added `thorold.history` namespace for pure time-travel comparison across database snapshots.
  - Added `xf-changelog` stateful transducer and `generate-changelog` pipeline in `ingest.clj`.
  - Added `thorold.export` namespace for Malli-derived JSON Schema and EDN serialization exports.
  - Added `/schema/person` and `/schema/team` endpoints to expose derived JSON schemas.
  - Added `:native-image` alias in `deps.edn`, GraalVM configs, and standalone build script `scripts/build-native.sh`.
  - Added `wrap-content-negotiation` middleware to support lossless EDN responses via the `Accept` header.

### Removed
- Removed deprecated `key_fpl_code` from person provider keys.

### Known Gaps (Out of Scope for this Session)
- First-class `match` entity type (`reep_m` prefix).
- Type-aware `/resolve` and `/batch/resolve` to handle provider ID collisions across entity types.
- Lack of benchmark suite coverage (`bench/thorold/`) for new history and export namespaces.

---

## [1.1.0] — 2026-05-24

### Added
- `LICENSE` file with full CC0-1.0 legal text
- GitHub Actions CI pipeline (`.github/workflows/ci.yml`) with
  automated test execution and SLF4J warning verification on every push
- CI badge in README
- `openapi.yaml` — OpenAPI 3.1.0 specification covering all four API
  endpoints with full request/response schemas
- `schemas/people.md` — standalone column reference for `data/people.csv`
- `schemas/teams.md` — standalone column reference for `data/teams.csv`
- `schemas/names.md` — standalone column reference for `data/names.csv`
- `scripts/refresh.clj` — runnable Babashka ingestion/refresh script
- `scripts/README.md` — usage documentation for refresh script
- `test/thorold/api_test.clj` — Ring handler unit tests (20+ assertions,
  no HTTP server required)
- `data/meta.json` — database generation metadata
- README: Python, R, and SQL usage examples for CSV-direct access
- README: provider coverage table with source and notes per provider
- README: Wikidata property reference table (39 properties)
- Repo topics, description, and website set via GitHub settings
- `bench/thorold/` benchmark suite for load time and query performance

### Fixed
- `src/thorold/parse.clj`: replaced materialising `parse-people-with-errors`
  and `parse-teams-with-errors` with transducer-based streaming
  implementation — 488k rows now processed without holding the full
  collection in memory
- `deps.edn`: moved `slf4j-nop` to top-level `:deps` so API server and
  all execution paths are silenced, not only the test runner
- Removed committed runtime artifacts (`api.log`, `search.json`,
  `resolve.json`, `stats.json`) and editor cache dirs (`.clj-kondo/`,
  `.lsp/`) from version control
- Updated `.gitignore` to prevent artifact and tooling cache recurrence

### Changed
- Commit history rewritten with descriptive scoped messages per module

---

## [1.0.1] — 2026-04-07

### Fixed
- `src/thorold/id.clj`: added `COMPATIBILITY WARNING` to `reep-id`
  docstring — deterministic SHA-256 IDs will not match existing CSV IDs
  which were randomly minted via UUID4
- `src/thorold/api.clj`: replaced hand-rolled `wrap-query-params` with
  `ring.middleware.params/wrap-params` — fixes edge cases with repeated
  params and URL-encoded values
- `src/thorold/ingest.clj`: added `CAUTION` docstring to `xf-deduplicate`
  documenting its stateful `volatile!` internals
- `src/thorold/query.clj`: added `(:refer-clojure :exclude [resolve])`
  to eliminate namespace collision warning
- `deps.edn`: added `slf4j-nop` to `:test` alias to silence SLF4J during
  test runs
- `dev/repl_demo.clj`: completed full 8-block literate REPL walkthrough

---

## [1.0.0] — 2026-04-01

### Added
- Complete rewrite of Reep in pure Clojure
- Named after Thorold Charles Reep (1904–2002), founding father of
  football analytics
- 10 source modules: `model`, `id`, `parse`, `index`, `db`, `query`,
  `ingest`, `cli`, `api`, `core`
- Malli schemas with namespaced keywords for all five entity types:
  player, coach, team, competition, season
- Single-pass index builder across all four indexes in one `reduce` pass
- Transducer-based Wikidata SPARQL ingestion pipeline — six composable
  stages, no intermediate collections
- Pure function query layer: `search`, `resolve`, `translate`, `lookup`
- Ring/Reitit REST API with `db` value injected via closure — no global
  state
- CLI with `--format human/edn/json` output modes and correct exit codes
- 47 tests, 175 assertions including property-based specs via `test.check`
- `dev/repl_demo.clj` as literate REPL walkthrough
- `resources/config.edn` for all non-secret runtime configuration
- `SECURITY.md`, `CHANGELOG.md`, `README.md` from day one
