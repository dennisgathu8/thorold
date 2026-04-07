# Thorold — Step 0 Audit

## Environment

```
Working directory: /home/ugodzilla/thorold

Java:
  openjdk version "17.0.18" 2026-01-20
  OpenJDK Runtime Environment (build 17.0.18+8-Debian-1deb12u1)
  OpenJDK 64-Bit Server VM (build 17.0.18+8-Debian-1deb12u1, mixed mode, sharing)

Clojure CLI: version 1.12.4.1602

Babashka: not available

Git: version 2.39.5
```

**IDE config:** No `.antigravity/`, `.gemini/`, or `.vscode/` directories found. No
workspace manifest exists. Proceeding with fresh `deps.edn`.

---

## Original Reep — Architecture Summary

**Repository:** https://github.com/withqwerty/reep  
**Language breakdown:** Python 79.1%, TypeScript 10.6%, Astro 7.8%, CSS 2.5%  
**License:** CC0 1.0

### System Components

| Component | Technology | Location |
|-----------|-----------|----------|
| Data ingestion | Python scripts | `scripts/` |
| API server | TypeScript (Cloudflare Worker + D1) | `src/worker.ts` |
| CLI | Python | `cli/reep.py` |
| Website | Astro | `site/` |
| Data | CSV files | `data/` |

### Data Schema

**people.csv** — 429,785 rows, 50 columns:
- Core: `reep_id`, `key_wikidata`, `type`, `name`, `full_name`, `date_of_birth`, `nationality`, `position`, `position_detail`, `height_cm`
- Provider keys (40 columns): `key_transfermarkt`, `key_transfermarkt_manager`, `key_fbref`, `key_soccerway`, `key_sofascore`, `key_flashscore`, `key_opta`, `key_premier_league`, `key_11v11`, `key_espn`, `key_national_football_teams`, `key_worldfootball`, `key_soccerbase`, `key_kicker`, `key_uefa`, `key_lequipe`, `key_fff_fr`, `key_serie_a`, `key_besoccer`, `key_footballdatabase_eu`, `key_eu_football_info`, `key_hugman`, `key_german_fa`, `key_statmuse_pl`, `key_sofifa`, `key_soccerdonna`, `key_dongqiudi`, `key_understat`, `key_whoscored`, `key_fbref_verified`, `key_sportmonks`, `key_api_football`, `key_fotmob`, `key_fpl_code`, `key_thesportsdb`, `key_skillcorner`, `key_wyscout`, `key_impect`, `key_heimspiel`, `key_capology`

**teams.csv** — 45,349 rows, 28 columns:
- Core: `reep_id`, `key_wikidata`, `name`, `country`, `founded`, `stadium`
- Provider keys (22 columns): `key_transfermarkt`, `key_fbref`, `key_soccerway`, `key_opta`, `key_kicker`, `key_flashscore`, `key_sofascore`, `key_soccerbase`, `key_uefa`, `key_footballdatabase_eu`, `key_worldfootball`, `key_espn`, `key_playmakerstats`, `key_clubelo`, `key_sportmonks`, `key_api_football`, `key_sofifa`, `key_fotmob`, `key_thesportsdb`, `key_understat`, `key_opta_numeric`, `key_capology`

**names.csv** — Header only (0 aliases in current build):
- Columns: `key_wikidata`, `name`, `alias`

**meta.json:**
- data_version: 2026.15
- api_version: 2.3.0
- 429,785 people, 45,349 teams, 194 competitions, 1,200 seasons, 360,448 custom_ids

---

## Reep ID Format

```
reep_<type_prefix><8hex>
```

### Type Prefixes

| Type | Prefix | Example |
|------|--------|---------|
| player | `p` | `reep_p2804f5db` (Cole Palmer) |
| team | `t` | `reep_t0871097b` (Arsenal F.C.) |
| coach | `c` | `reep_c9103de59` |
| competition | `l` | `reep_lb3d230cb` (Premier League) |
| season | `s` | `reep_sa7f63ba6` |

### ID Generation Algorithm

**CRITICAL FINDING:** Reep IDs are **random**, NOT deterministic from a seed.

From `scripts/mint-reep-ids.py`:
```python
def generate_reep_id(entity_type: str) -> str:
    prefix = TYPE_PREFIXES.get(entity_type)
    hex8 = uuid.uuid4().hex[:8]
    return f"reep_{prefix}{hex8}"
```

The algorithm:
1. Look up type prefix (`p`, `t`, `c`, `l`, `s`)
2. Generate a random UUID4
3. Take the first 8 hex characters of the UUID
4. Concatenate: `reep_` + prefix + 8 hex chars

**Implications for Thorold:**
- IDs are random — they cannot be re-derived from entity data
- The existing CSVs ARE the source of truth for existing IDs
- New entities need random ID minting (same algorithm)
- Collision checking: retry up to 10 times on collision
- IDs are stable: once minted, they never change

### COMPATIBILITY WARNING

The mission spec says "pure, deterministic" ID generation from a seed. However, the original
implementation uses `uuid.uuid4()` (random). The spec example `reep-id [type seed]` implies
deterministic generation from a canonical seed string.

**Resolution:** For Thorold, we implement BOTH:
1. `mint-reep-id` — random minting (for new entities, matching original behavior)
2. For existing entities, IDs come from the CSV files (the source of truth)
3. The `reep-id` function in `thorold.id` will use deterministic hashing from a seed for
   reproducibility, but we document that original IDs were randomly minted

---

## CLI Commands

From `cli/reep.py`:

| Command | Signature | Description |
|---------|-----------|-------------|
| `search` | `reep search <name> [--type TYPE] [--limit N] [-v]` | Search by name (online API) |
| `resolve` | `reep resolve <provider> <id>` | Resolve provider ID to all IDs |
| `lookup` | `reep lookup <qid> [--type TYPE]` | Look up by Wikidata QID or Reep ID |
| `translate` | `reep translate <source> <id> <target>` | Translate between providers (pipe-friendly) |
| `download` | `reep download` | Download latest CSVs from GitHub |
| `local` | `reep local <name> [--type TYPE] [--limit N] [-v]` | Search local CSV files (offline) |
| `stats` | `reep stats` | Show database statistics |

---

## API Endpoints

From `openapi.yaml` (v2.3.0):

| Method | Endpoint | Parameters | Description |
|--------|----------|-----------|-------------|
| GET | `/search` | `name` (required), `type`, `limit` | Full-text search with BM25 ranking |
| GET | `/resolve` | `provider` (required), `id` (required) | Provider ID → entity with all IDs |
| GET | `/lookup` | `id` (required), `type` | Reep ID or QID → entity |
| GET | `/stats` | — | Entity counts by type and provider |
| POST | `/batch/lookup` | body: `{ids: [...]}` | Batch lookup (max 100) |
| POST | `/batch/resolve` | body: `{items: [{provider, id}, ...]}` | Batch resolve (max 100) |
| GET | `/health` | — | Public health check (no auth) |

### Response Format

All entity responses follow this shape:
```json
{
  "results": [
    {
      "reep_id": "reep_p2804f5db",
      "qid": "Q99760796",
      "type": "player",
      "name_en": "Cole Palmer",
      "aliases_en": "Cole Jermaine Palmer",
      "date_of_birth": "2002-05-06",
      "nationality": "United Kingdom",
      "position": "attacking midfielder",
      "position_detail": "Attacking Midfield",
      "height_cm": 185,
      "external_ids": {
        "wikidata": "Q99760796",
        "transfermarkt": "568177",
        "fbref": "dc7f8a28",
        "sofascore": "982780"
      }
    }
  ],
  "count": 1
}
```

### Error Format
```json
{"error": "Required: ?name=Cole Palmer"}
```

---

## Provider List (Complete)

Total 43 providers in the API's `VALID_PROVIDERS` set:

wikidata, transfermarkt, transfermarkt_manager, fbref, fbref_verified,
soccerway, sofascore, flashscore, opta, opta_numeric, premier_league,
11v11, espn, national_football_teams, worldfootball, soccerbase, kicker,
uefa, lequipe, fff_fr, serie_a, besoccer, footballdatabase_eu,
eu_football_info, hugman, german_fa, statmuse_pl, sofifa, soccerdonna,
dongqiudi, playmakerstats, understat, whoscored, clubelo, sportmonks,
api_football, fotmob, fpl_code, thesportsdb, impect, wyscout,
skillcorner, heimspiel, capology

---

## Verified ID Pairs (for compatibility testing)

| Entity | Reep ID | QID | Transfermarkt | FBref |
|--------|---------|-----|---------------|-------|
| Cole Palmer | reep_p2804f5db | Q99760796 | 568177 | dc7f8a28 |
| Lionel Messi | reep_pbd9a559b | Q615 | 28003 | d70ce98e |

---

## Thorold Design Decisions

1. **IDs:** Thorold preserves the `reep_<prefix><8hex>` format exactly. For new entities,
   IDs are minted randomly (matching original). For the `thorold.id/reep-id` function, we
   provide a deterministic hash-based variant using SHA-256 truncated to 8 hex chars for
   use cases requiring reproducibility.

2. **Data model:** All `key_*` columns collapse into a single `:providers` map.

3. **Architecture:** The DB is a single immutable Clojure map. No Cloudflare D1, no SQL.
   Just CSVs → maps → indexes.

4. **Babashka:** Not available in this environment. Skipping `bb.edn`.
