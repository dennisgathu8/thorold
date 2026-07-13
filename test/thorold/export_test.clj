(ns thorold.export-test
  "Tests for thorold.export — JSON Schema derivation, CSV row export, EDN round-trip."
  (:require [clojure.test :refer [deftest is testing]]
            [thorold.export :as export]
            [thorold.model :as model]
            [thorold.index :as index]))

(deftest person-json-schema-test
  (testing "produces a valid JSON Schema map"
    (let [schema (export/person-json-schema)]
      (is (map? schema))
      (is (= "object" (get schema :type)))
      (is (map? (get schema :properties)))))

  (testing "includes required fields"
    (let [schema (export/person-json-schema)
          props  (get schema :properties)]
      (is (some? (get props :reep/id)))
      (is (some? (get props :person/name)))
      (is (some? (get props :providers))))))

(deftest team-json-schema-test
  (testing "produces a valid JSON Schema map"
    (let [schema (export/team-json-schema)]
      (is (map? schema))
      (is (= "object" (get schema :type)))))

  (testing "includes team-specific fields"
    (let [schema (export/team-json-schema)
          props  (get schema :properties)]
      (is (some? (get props :team/name))))))

(deftest entity->csv-row-test
  (testing "person entity round-trips through CSV row"
    (let [entity {:reep/id "reep_p2804f5db"
                  :reep/type :person/player
                  :person/name "Cole Palmer"
                  :person/full-name "Cole Jermaine Palmer"
                  :person/dob "2002-05-06"
                  :person/nationality "England"
                  :person/position "Midfielder"
                  :person/position-detail "Attacking Midfielder"
                  :person/height-cm 189
                  :providers {:wikidata "Q99760796"
                              :transfermarkt "568177"
                              :fbref "dc7f8a28"}}
          row (export/entity->csv-row entity model/person-provider-keys)]
      (is (= "reep_p2804f5db" (get row "reep_id")))
      (is (= "Cole Palmer" (get row "name")))
      (is (= "player" (get row "type")))
      (is (= "568177" (get row "key_transfermarkt")))
      (is (= "dc7f8a28" (get row "key_fbref")))
      (is (= "Q99760796" (get row "key_wikidata")))))

  (testing "team entity produces correct row"
    (let [entity {:reep/id "reep_t12345678"
                  :reep/type :team
                  :team/name "Arsenal"
                  :team/country "England"
                  :providers {:transfermarkt "11"}}
          row (export/entity->csv-row entity model/team-provider-keys)]
      (is (= "Arsenal" (get row "name")))
      (is (= "England" (get row "country")))
      (is (= "11" (get row "key_transfermarkt")))))

  (testing "missing providers produce empty strings"
    (let [entity {:reep/id "reep_p99999999"
                  :reep/type :person/player
                  :person/name "No Providers"
                  :providers {}}
          row (export/entity->csv-row entity model/person-provider-keys)]
      (is (= "" (get row "key_transfermarkt")))
      (is (= "" (get row "key_fbref"))))))

(deftest db->edn-test
  (testing "produces lossless EDN export"
    (let [entities [{:reep/id "reep_p11111111"
                     :reep/type :person/player
                     :person/name "Test Player"
                     :providers {:transfermarkt "111"}}]
          indexes (index/build-indexes entities)
          db {:entities (:by-reep-id indexes)
              :indexes indexes
              :names [{:name/qid "Q1" :name/name "A" :name/alias "B"}]
              :meta {:people-count 1 :load-ms 42}}
          edn (export/db->edn db)]
      (is (= 1 (count (:entities edn))))
      (is (= 1 (count (:names edn))))
      (is (= 42 (get-in edn [:meta :load-ms])))
      ;; Entities are values, not keyed by reep-id
      (is (= "Test Player" (:person/name (first (:entities edn))))))))
