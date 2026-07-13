# Competitions Schema

This schema documents the fields in `competitions.csv` which contains registration data for football leagues, tournaments, and cups. It acts as a mapping table to link competition identities across multiple external platforms.

## Column Reference

| Column Name | Type | Description | Example Value |
|-------------|------|-------------|---------------|
| `reep_id` | String | Canonical, stable Thorold identifier. Always starts with `reep_l`. | `reep_lb3d230cb` |
| `key_wikidata` | String | Wikidata QID | `Q9448` |
| `name` | String | Primary English competition name | `Premier League` |
| `country` | String | Country of origin or region (e.g. `England`, `Europe`) | `England` |
| `key_transfermarkt` | String | Transfermarkt competition ID | `GB1` |
| `key_fbref` | String | FBref competition/league ID | `9` |
| `key_opta` | String | Opta competition alphanumeric/hash ID | `7cwgrmorsb42qaj5vrhp8fhzp` |
| `key_opta_numeric` | String | Opta numeric competition ID | `8` |
| `key_optacore` | String | Opta Core competition ID | `ENG_PL` |
| `key_fotmob` | String | FotMob competition ID | `47` |
| `key_whoscored` | String | WhoScored competition ID | `2` |

## Missing Values

Any optional or missing data is represented as an empty string (`""`) in the CSV file. When parsed, these values map to `nil` rather than empty strings.

## Reep ID Format

Every competition is assigned a stable Reep ID of the format:
`reep_l<8hex>`

- **`l`**: Prefix for league / competition.
- **`8hex`**: An 8-character hexadecimal string representing the first 8 characters of a deterministic SHA-256 hash or UUID.
