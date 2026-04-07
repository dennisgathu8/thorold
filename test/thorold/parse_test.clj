(ns thorold.parse-test
  "Tests for thorold.parse using inline string fixtures — no actual CSV files."
  (:require [clojure.test :refer [deftest is testing]]
            [thorold.parse :as parse]))

(def sample-people-rows
  "Inline fixture: simulated CSV row maps for people."
  [{"reep_id" "reep_p2804f5db"
    "key_wikidata" "Q99760796"
    "type" "player"
    "name" "Cole Palmer"
    "full_name" "Cole Jermaine Palmer"
    "date_of_birth" "2002-05-06"
    "nationality" "United Kingdom"
    "position" "attacking midfielder"
    "position_detail" "Attacking Midfield"
    "height_cm" "185.0"
    "key_transfermarkt" "568177"
    "key_fbref" "dc7f8a28"
    "key_sofascore" "982780"}
   {"reep_id" "reep_pbd9a559b"
    "key_wikidata" "Q615"
    "type" "player"
    "name" "Lionel Messi"
    "full_name" ""
    "date_of_birth" "1987-06-24"
    "nationality" "Argentina"
    "position" "forward"
    "position_detail" "Right Winger"
    "height_cm" "169.0"
    "key_transfermarkt" "28003"
    "key_fbref" "d70ce98e"}
   {"reep_id" "reep_c12345678"
    "key_wikidata" "Q55001"
    "type" "coach"
    "name" "Pep Guardiola"
    "full_name" ""
    "date_of_birth" "1971-01-18"
    "nationality" "Spain"
    "position" ""
    "position_detail" ""
    "height_cm" ""
    "key_transfermarkt" "5672"}])

(def sample-teams-rows
  [{"reep_id" "reep_t0871097b"
    "key_wikidata" "Q9616"
    "name" "Arsenal F.C."
    "country" "United Kingdom"
    "founded" "1886-10-01"
    "stadium" "Emirates Stadium"
    "key_transfermarkt" "11"
    "key_fbref" "18bb7c10"}])

(deftest parse-people-basic
  (testing "parses valid player rows"
    (let [entities (vec (parse/parse-people sample-people-rows))]
      (is (= 3 (count entities)))

      ;; First entity: Cole Palmer
      (let [palmer (first entities)]
        (is (= "reep_p2804f5db" (:reep/id palmer)))
        (is (= :person/player (:reep/type palmer)))
        (is (= "Cole Palmer" (:person/name palmer)))
        (is (= "Cole Jermaine Palmer" (:person/full-name palmer)))
        (is (= "2002-05-06" (:person/dob palmer)))
        (is (= 185 (:person/height-cm palmer)))
        (is (= "568177" (get-in palmer [:providers :transfermarkt])))
        (is (= "dc7f8a28" (get-in palmer [:providers :fbref])))
        (is (= "Q99760796" (get-in palmer [:providers :wikidata])))))))

(deftest parse-people-empty-strings-become-nil
  (testing "empty strings become nil, not empty strings"
    (let [entities (vec (parse/parse-people sample-people-rows))
          messi    (second entities)]
      (is (nil? (:person/full-name messi)))))) ;; was ""

(deftest parse-people-coach-type
  (testing "coach type is correctly parsed"
    (let [entities (vec (parse/parse-people sample-people-rows))
          guardiola (nth entities 2)]
      (is (= :person/coach (:reep/type guardiola)))
      (is (nil? (:person/height-cm guardiola))))))

(deftest parse-people-providers-nested
  (testing "all key_* columns go into :providers map"
    (let [entities (vec (parse/parse-people sample-people-rows))
          palmer   (first entities)]
      (is (map? (:providers palmer)))
      (is (= "568177" (:transfermarkt (:providers palmer))))
      (is (= "982780" (:sofascore (:providers palmer)))))))

(deftest parse-teams-basic
  (testing "parses valid team rows"
    (let [entities (vec (parse/parse-teams sample-teams-rows))
          arsenal  (first entities)]
      (is (= "reep_t0871097b" (:reep/id arsenal)))
      (is (= :team (:reep/type arsenal)))
      (is (= "Arsenal F.C." (:team/name arsenal)))
      (is (= "United Kingdom" (:team/country arsenal)))
      (is (= "Emirates Stadium" (:team/stadium arsenal)))
      (is (= "11" (get-in arsenal [:providers :transfermarkt])))
      (is (= "Q9616" (get-in arsenal [:providers :wikidata]))))))

(deftest parse-malformed-rows
  (testing "rows missing required fields are dropped"
    (let [rows [{"reep_id" "" "type" "player" "name" "No ID"}
                {"reep_id" "reep_p11111111" "type" "player" "name" ""}
                {"reep_id" "reep_p22222222" "type" "" "name" "No Type"}]
          entities (vec (parse/parse-people rows))]
      (is (= 0 (count entities)))))

  (testing "error collection works"
    (let [rows [{"reep_id" "" "type" "player" "name" "Bad"}
                {"reep_id" "reep_p33333333" "type" "player" "name" "Good"
                 "key_wikidata" "Q999"}]
          result (parse/parse-people-with-errors rows)]
      (is (= 1 (count (:entities result))))
      (is (= 1 (count (:errors result)))))))

(deftest parse-height-handling
  (testing "integer heights"
    (let [rows [{"reep_id" "reep_p00000001" "type" "player" "name" "A"
                 "height_cm" "185.0" "key_wikidata" "Q1"}]
          entity (first (parse/parse-people rows))]
      (is (= 185 (:person/height-cm entity)))))

  (testing "missing height is nil"
    (let [rows [{"reep_id" "reep_p00000002" "type" "player" "name" "B"
                 "height_cm" "" "key_wikidata" "Q2"}]
          entity (first (parse/parse-people rows))]
      (is (nil? (:person/height-cm entity))))))
