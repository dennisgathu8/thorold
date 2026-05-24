# Teams Schema

This schema documents the fields in `teams.csv` which contains registration data for football clubs and national teams. It acts as a mapping table to link team identities across multiple external platforms.

## Column Reference

| Column Name | Type | Description | Example Value |
|-------------|------|-------------|---------------|
| `reep_id` | String | Canonical, stable Thorold identifier. Always starts with `reep_t`. | `reep_t0871097b` |
| `key_wikidata` | String | Wikidata QID | `Q9616` |
| `name` | String | Primary English team name | `Arsenal F.C.` |
| `country` | String | Country of origin or national association | `United Kingdom` |
| `founded` | String | Team founding date (YYYY-MM-DD or YYYY) | `1886-10-01` |
| `stadium` | String | Primary home stadium name | `Emirates Stadium` |
| `key_transfermarkt` | String | Transfermarkt team ID | `11` |
| `key_fbref` | String | FBref squad ID | `18bb7c10` |
| `key_soccerway` | String | Soccerway team ID | `6131` |
| `key_opta` | String | Opta team ID | `8737` |
| `key_kicker` | String | Kicker team ID | `12312` |
| `key_flashscore` | String | Flashscore team ID | `7876` |
| `key_sofascore` | String | Sofascore team ID | `13897` |
| `key_soccerbase` | String | Soccerbase team ID | `7454` |
| `key_uefa` | String | UEFA team ID | `7361` |
| `key_footballdatabase_eu` | String | FootballDatabase.eu team ID | `7351` |
| `key_worldfootball` | String | WorldFootball.net team ID | `7287` |
| `key_espn` | String | ESPN FC team ID | `12345` |
| `key_playmakerstats` | String | PlaymakerStats team ID | `7280` |
| `key_clubelo` | String | Club Elo team ID | `Arsenal` |
| `key_sportmonks` | String | SportMonks team ID | `12345` |
| `key_api_football` | String | API-Football team ID | `1100` |
| `key_sofifa` | String | SoFIFA team ID | `5678` |
| `key_fotmob` | String | FotMob team ID | `292462` |
| `key_thesportsdb` | String | TheSportsDB team ID | `34146086` |
| `key_understat` | String | Understat team ID | `123` |
| `key_opta_numeric` | String | Opta numeric team ID | `1234` |
| `key_capology` | String | Capology team ID | `arsenal` |

## Missing Values

Any optional or missing data is represented as an empty string (`""`) in the CSV file. When parsed, these values map to `nil` rather than empty strings.

## Reep ID Format

Every team is assigned a stable Reep ID of the format:
`reep_t<8hex>`

- **`t`**: Prefix for team.
- **`8hex`**: An 8-character hexadecimal string representing the first 8 characters of a randomly minted UUID4 or a deterministic SHA-256 hash.
