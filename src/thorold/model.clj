(ns thorold.model
  "Malli schemas for all Thorold entity types.

   Every entity (player, coach, team) is a plain Clojure map. Provider IDs
   live under a single :providers map — never as flat top-level keys.

   The Reep ID format `reep_<prefix><8hex>` is preserved exactly for
   backward compatibility with the original Reep project."
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg]))

;; ---------------------------------------------------------------------------
;; Provider schema
;; ---------------------------------------------------------------------------

(def ProviderMap
  "Map of provider keyword to provider ID string. All 40+ providers follow
   the same pattern: key_transfermarkt → :transfermarkt, key_fbref → :fbref, etc."
  [:map-of :keyword [:maybe :string]])

;; ---------------------------------------------------------------------------
;; Entity types
;; ---------------------------------------------------------------------------

(def PersonSchema
  "Schema for a person entity (player or coach).
   People who are both players and coaches have separate records with
   distinct Reep IDs — reep_p* for player, reep_c* for coach."
  [:map
   [:reep/id      [:string {:min 10 :max 16}]]
   [:reep/type    [:enum :person/player :person/coach]]
   [:person/name  :string]
   [:person/full-name {:optional true} [:maybe :string]]
   [:person/dob   {:optional true} [:maybe :string]]
   [:person/nationality {:optional true} [:maybe :string]]
   [:person/position {:optional true} [:maybe :string]]
   [:person/position-detail {:optional true} [:maybe :string]]
   [:person/height-cm {:optional true} [:maybe [:or :int :double]]]
   [:providers    ProviderMap]])

(def TeamSchema
  "Schema for a team/club entity."
  [:map
   [:reep/id      [:string {:min 10 :max 16}]]
   [:reep/type    [:= :team]]
   [:team/name    :string]
   [:team/country {:optional true} [:maybe :string]]
   [:team/founded {:optional true} [:maybe :string]]
   [:team/stadium {:optional true} [:maybe :string]]
   [:providers    ProviderMap]])

(def CompetitionSchema
  "Schema for a competition entity."
  [:map
   [:reep/id      [:string {:min 10 :max 16}]]
   [:reep/type    [:= :competition]]
   [:competition/name :string]
   [:competition/country {:optional true} [:maybe :string]]
   [:providers    ProviderMap]])

(def SeasonSchema
  "Schema for a season entity."
  [:map
   [:reep/id      [:string {:min 10 :max 16}]]
   [:reep/type    [:= :season]]
   [:season/name  :string]
   [:season/competition-reep-id {:optional true} [:maybe :string]]
   [:providers    ProviderMap]])

(def EntitySchema
  "Discriminated union of all entity types."
  [:or PersonSchema TeamSchema CompetitionSchema SeasonSchema])

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn validate
  "Validates an entity map against the appropriate schema.
   Returns the entity if valid, nil if invalid."
  [entity]
  (when (m/validate EntitySchema entity)
    entity))

(defn validate!
  "Validates an entity map against the appropriate schema.
   Returns the entity if valid.
   Throws ex-info with structured error data on failure — never a bare string."
  [entity]
  (if (m/validate EntitySchema entity)
    entity
    (throw (ex-info "Entity validation failed"
                    {:thorold/error-type :validation-error
                     :entity             entity
                     :errors             (me/humanize (m/explain EntitySchema entity))}))))

(defn valid?
  "Returns true if the entity conforms to its schema."
  [entity]
  (m/validate EntitySchema entity))

;; ---------------------------------------------------------------------------
;; Schema utilities
;; ---------------------------------------------------------------------------

(defn generate-person
  "Generate a random person entity for property-based testing."
  ([] (mg/generate PersonSchema))
  ([opts] (mg/generate PersonSchema opts)))

(defn generate-team
  "Generate a random team entity for property-based testing."
  ([] (mg/generate TeamSchema))
  ([opts] (mg/generate TeamSchema opts)))

;; ---------------------------------------------------------------------------
;; Provider key registry
;; ---------------------------------------------------------------------------

(def person-provider-keys
  "All provider key columns in people.csv, in CSV column order.
   Maps CSV column name (string) to Clojure keyword."
  {"key_transfermarkt"         :transfermarkt
   "key_transfermarkt_manager" :transfermarkt_manager
   "key_fbref"                 :fbref
   "key_soccerway"             :soccerway
   "key_sofascore"             :sofascore
   "key_flashscore"            :flashscore
   "key_opta"                  :opta
   "key_premier_league"        :premier_league
   "key_11v11"                 :11v11
   "key_espn"                  :espn
   "key_national_football_teams" :national_football_teams
   "key_worldfootball"         :worldfootball
   "key_soccerbase"            :soccerbase
   "key_kicker"                :kicker
   "key_uefa"                  :uefa
   "key_lequipe"               :lequipe
   "key_fff_fr"                :fff_fr
   "key_serie_a"               :serie_a
   "key_besoccer"              :besoccer
   "key_footballdatabase_eu"   :footballdatabase_eu
   "key_eu_football_info"      :eu_football_info
   "key_hugman"                :hugman
   "key_german_fa"             :german_fa
   "key_statmuse_pl"           :statmuse_pl
   "key_sofifa"                :sofifa
   "key_soccerdonna"           :soccerdonna
   "key_dongqiudi"             :dongqiudi
   "key_understat"             :understat
   "key_whoscored"             :whoscored
   "key_fbref_verified"        :fbref_verified
   "key_sportmonks"            :sportmonks
   "key_api_football"          :api_football
   "key_fotmob"                :fotmob
   "key_opta_numeric"          :opta_numeric
   "key_thesportsdb"           :thesportsdb
   "key_skillcorner"           :skillcorner
   "key_wyscout"               :wyscout
   "key_impect"                :impect
   "key_heimspiel"             :heimspiel
   "key_capology"              :capology})

(def team-provider-keys
  "All provider key columns in teams.csv."
  {"key_transfermarkt"         :transfermarkt
   "key_fbref"                 :fbref
   "key_soccerway"             :soccerway
   "key_opta"                  :opta
   "key_kicker"                :kicker
   "key_flashscore"            :flashscore
   "key_sofascore"             :sofascore
   "key_soccerbase"            :soccerbase
   "key_uefa"                  :uefa
   "key_footballdatabase_eu"   :footballdatabase_eu
   "key_worldfootball"         :worldfootball
   "key_espn"                  :espn
   "key_playmakerstats"        :playmakerstats
   "key_clubelo"               :clubelo
   "key_sportmonks"            :sportmonks
   "key_api_football"          :api_football
   "key_sofifa"                :sofifa
   "key_fotmob"                :fotmob
   "key_thesportsdb"           :thesportsdb
   "key_understat"             :understat
   "key_opta_numeric"          :opta_numeric
   "key_capology"              :capology})

(def competition-provider-keys
  "All provider key columns in competitions.csv.
   Verified against Reep's live data/competitions.csv header."
  {"key_transfermarkt" :transfermarkt
   "key_fbref"         :fbref
   "key_opta"          :opta
   "key_opta_numeric"  :opta_numeric
   "key_optacore"      :optacore
   "key_fotmob"        :fotmob
   "key_whoscored"     :whoscored})

(def season-provider-keys
  "Provider key columns in seasons.csv.
   Verified against Reep's live data/seasons.csv header — seasons have
   no provider key columns beyond key_wikidata (handled separately)."
  {})
