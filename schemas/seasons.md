# Seasons Schema

This schema documents the fields in `seasons.csv` which contains registration data for individual competition seasons. It acts as a mapping table to link season identities and their parent competitions.

## Column Reference

| Column Name | Type | Description | Example Value |
|-------------|------|-------------|---------------|
| `reep_id` | String | Canonical, stable Thorold identifier. Always starts with `reep_s`. | `reep_sa7f63ba6` |
| `key_wikidata` | String | Wikidata QID | `Q105085448` |
| `name` | String | Season name/year range | `2023/2024` |
| `competition_reep_id` | String | Parent competition Reep ID (links to `competitions.csv`). | `reep_lb3d230cb` |

## Missing Values

Any optional or missing data is represented as an empty string (`""`) in the CSV file. When parsed, these values map to `nil` rather than empty strings.

## Reep ID Format

Every season is assigned a stable Reep ID of the format:
`reep_s<8hex>`

- **`s`**: Prefix for season.
- **`8hex`**: An 8-character hexadecimal string representing the first 8 characters of a deterministic SHA-256 hash or UUID.
