# Names Schema

This schema documents the fields in `names.csv` which contains alternative name aliases and translations for players, coaches, and teams. It is used to resolve entities when searched by non-canonical name variations.

## Column Reference

| Column Name | Type | Description | Example Value |
|-------------|------|-------------|---------------|
| `key_wikidata` | String | Wikidata QID representing the entity. Matches the `key_wikidata` column in `people.csv` or `teams.csv`. | `Q615` |
| `name` | String | Primary canonical English name | `Lionel Messi` |
| `alias` | String | Alternative name alias, nickname, or translation in other languages | `Leo Messi` |

## Missing Values

There are no missing values in this file. All three columns (`key_wikidata`, `name`, and `alias`) are required for name resolution.

## Name Resolution Mechanism

Alternate names are loaded into the database alongside primary names to populate the search index. The system normalizes search queries (lowercasing, diacritic stripping, and punctuation removal) and queries both primary names and aliases to return the best matching entities.
