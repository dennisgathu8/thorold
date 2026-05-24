# People Schema

This schema documents the fields in `people.csv` which contains registration data for players and coaches. It acts as a mapping table to link individuals across more than 40 distinct football data providers.

## Column Reference

| Column Name | Type | Description | Example Value |
|-------------|------|-------------|---------------|
| `reep_id` | String | Canonical, stable Thorold identifier. Always starts with `reep_p` (player) or `reep_c` (coach). | `reep_p2804f5db` |
| `key_wikidata` | String | Wikidata QID | `Q99760796` |
| `type` | String | The role classification for this entity. Either `player` or `coach`. | `player` |
| `name` | String | Primary English name | `Cole Palmer` |
| `full_name` | String | Complete legal/birth name | `Cole Jermaine Palmer` |
| `date_of_birth` | String | ISO-8601 date of birth (YYYY-MM-DD) | `2002-05-06` |
| `nationality` | String | Citizenship or national association affiliation | `England` |
| `position` | String | General position on the pitch | `Midfielder` |
| `position_detail` | String | Detailed position role | `Attacking Midfield` |
| `height_cm` | Integer | Height in centimeters | `189` |
| `key_transfermarkt` | String | Transfermarkt player ID | `568177` |
| `key_transfermarkt_manager` | String | Transfermarkt manager ID | `5672` |
| `key_fbref` | String | FBref player ID | `dc7f8a28` |
| `key_soccerway` | String | Soccerway person ID | `525801` |
| `key_sofascore` | String | Sofascore player ID | `982780` |
| `key_flashscore` | String | Flashscore player ID | `palmer-cole/h8agbDt7` |
| `key_opta` | String | Opta player ID | `7cwgrmorsb42qaj5vrhp8fhzp` |
| `key_premier_league` | String | Premier League player ID | `49293` |
| `key_11v11` | String | 11v11 player ID | `12345` |
| `key_espn` | String | ESPN FC player ID | `23456` |
| `key_national_football_teams` | String | National Football Teams ID | `34567` |
| `key_worldfootball` | String | WorldFootball.net ID | `45678` |
| `key_soccerbase` | String | Soccerbase player ID | `56789` |
| `key_kicker` | String | Kicker player ID | `cole-palmer` |
| `key_uefa` | String | UEFA player ID | `67890` |
| `key_lequipe` | String | L'Equipe player ID | `78901` |
| `key_fff_fr` | String | FFF.fr player ID | `89012` |
| `key_serie_a` | String | Lega Serie A player ID | `90123` |
| `key_besoccer` | String | BeSoccer player ID | `01234` |
| `key_footballdatabase_eu` | String | FootballDatabase.eu person ID | `123456` |
| `key_eu_football_info` | String | EU-Football.info player ID | `234567` |
| `key_hugman` | String | Barry Hugman's Footballers ID | `345678` |
| `key_german_fa` | String | German FA person ID | `456789` |
| `key_statmuse_pl` | String | StatMuse PL player ID | `cole-palmer-36271` |
| `key_sofifa` | String | SoFIFA / EA FC player ID | `567890` |
| `key_soccerdonna` | String | Soccerdonna player/coach ID | `678901` |
| `key_dongqiudi` | String | Dongqiudi player ID | `789012` |
| `key_understat` | String | Understat player ID | `890123` |
| `key_whoscored` | String | WhoScored player ID | `901234` |
| `key_fbref_verified` | String | Verified FBref player ID | `dc7f8a28` |
| `key_sportmonks` | String | SportMonks player ID | `012345` |
| `key_api_football` | String | API-Football player ID | `1100` |
| `key_fotmob` | String | FotMob player ID | `292462` |
| `key_fpl_code` | String | Fantasy Premier League player code | `12345` |
| `key_thesportsdb` | String | TheSportsDB player ID | `34146086` |
| `key_skillcorner` | String | SkillCorner player ID | `23959` |
| `key_wyscout` | String | Wyscout player ID | `234966` |
| `key_impect` | String | Impect player ID | `52615` |
| `key_heimspiel` | String | heim:spiel player ID | `361032` |
| `key_capology` | String | Capology player ID | `cole-palmer-36271` |

## Missing Values

Any optional or missing data is represented as an empty string (`""`) in the CSV file. When parsed, these values map to `nil` rather than empty strings.

## Reep ID Format

Every player and coach is assigned a stable Reep ID of the format:
`reep_<prefix><8hex>`

- **`prefix`**: `p` for player, `c` for coach.
- **`8hex`**: An 8-character hexadecimal string representing the first 8 characters of a randomly minted UUID4 or a deterministic SHA-256 hash.
