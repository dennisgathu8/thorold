(ns thorold.ingest-test
  "Tests for thorold.ingest using EDN fixtures — no live network calls."
  (:require [clojure.test :refer [deftest is testing]]
            [thorold.ingest :as ingest]
            [thorold.index :as index]))

(def sparql-fixture
  "EDN fixture simulating SPARQL response bindings."
  [{"item"       {"value" "http://www.wikidata.org/entity/Q615"}
    "itemLabel"  {"value" "Lionel Messi"}
    "type"       {"value" "player"}
    "dob"        {"value" "1987-06-24"}
    "nationality" {"value" "Argentina"}
    "id_transfermarkt" {"value" "28003"}
    "id_fbref"   {"value" "d70ce98e"}}
   {"item"       {"value" "http://www.wikidata.org/entity/Q5682"}
    "itemLabel"  {"value" "David Beckham"}
    "type"       {"value" "player"}
    "dob"        {"value" "1975-05-02"}
    "nationality" {"value" "United Kingdom"}
    "id_transfermarkt" {"value" "3352"}}
   ;; Duplicate of Messi — should be deduplicated
   {"item"       {"value" "http://www.wikidata.org/entity/Q615"}
    "itemLabel"  {"value" "Lionel Messi"}
    "type"       {"value" "player"}
    "dob"        {"value" "1987-06-24"}
    "nationality" {"value" "Argentina"}
    "id_transfermarkt" {"value" "28003"}}])

(deftest xf-parse-sparql-test
  (testing "extracts QID, name, and provider IDs"
    (let [results (into [] (ingest/xf-parse-sparql) sparql-fixture)
          messi   (first results)]
      (is (= "Q615" (:qid messi)))
      (is (= "Lionel Messi" (:name messi)))
      (is (= "28003" (get-in messi [:providers :transfermarkt])))
      (is (= "d70ce98e" (get-in messi [:providers :fbref]))))))

(deftest xf-normalize-ids-test
  (testing "trims whitespace from provider IDs"
    (let [input  [{:providers {:transfermarkt "  28003  " :fbref " abc "}}]
          result (first (into [] (ingest/xf-normalize-ids) input))]
      (is (= "28003" (get-in result [:providers :transfermarkt])))
      (is (= "abc" (get-in result [:providers :fbref]))))))

(deftest xf-deduplicate-test
  (testing "removes duplicate QIDs"
    (let [input  [{:qid "Q1" :name "A"} {:qid "Q2" :name "B"} {:qid "Q1" :name "A"}]
          result (into [] (ingest/xf-deduplicate) input)]
      (is (= 2 (count result))))))

(deftest full-pipeline-test
  (testing "full pipeline produces valid entity maps"
    (let [results (into [] (ingest/ingest-pipeline) sparql-fixture)]
      ;; 3 inputs, but Q615 is duplicated → 2 results
      (is (= 2 (count results)))

      ;; Check entity shape
      (let [messi (first results)]
        (is (some? (:reep/id messi)))
        (is (= :person/player (:reep/type messi)))
        (is (= "Lionel Messi" (:person/name messi)))
        (is (= "Q615" (get-in messi [:providers :wikidata])))))))

(deftest merge-into-db-test
  (let [entity1 {:reep/id "reep_p11111111"
                 :reep/type :person/player
                 :person/name "Test Player"
                 :providers {:transfermarkt "111"}}
        entity2 {:reep/id "reep_p22222222"
                 :reep/type :person/player
                 :person/name "New Player"
                 :providers {:transfermarkt "222"}}
        indexes (index/build-indexes [entity1])
        db      {:entities (:by-reep-id indexes) :indexes indexes}]

    (testing "adds new entities"
      (let [[updated-db manifest] (ingest/merge-into-db db [entity2])]
        (is (= 2 (count (:entities updated-db))))
        (is (= 1 (:added manifest)))
        (is (= 0 (:updated manifest)))))

    (testing "skips identical entities"
      (let [[_updated-db manifest] (ingest/merge-into-db db [entity1])]
        (is (= 1 (:skipped manifest)))
        (is (= 0 (:added manifest)))))

    (testing "updates entities with new providers"
      (let [entity1-updated (assoc-in entity1 [:providers :fbref] "abc123")
            [updated-db manifest] (ingest/merge-into-db db [entity1-updated])]
        (is (= 1 (:updated manifest)))
        (is (= "abc123" (get-in (:entities updated-db)
                                ["reep_p11111111" :providers :fbref])))))))

;; ---------------------------------------------------------------------------
;; Changelog transducer tests
;; ---------------------------------------------------------------------------

(deftest xf-changelog-test
  (let [entity-a {:reep/id "reep_p11111111"
                  :reep/type :person/player
                  :person/name "Player A"
                  :providers {:transfermarkt "111"}}
        entity-b {:reep/id "reep_p22222222"
                  :reep/type :person/player
                  :person/name "Player B"
                  :providers {:transfermarkt "222"}}
        indexes  (index/build-indexes [entity-a])
        db       {:entities (:by-reep-id indexes) :indexes indexes}]

    (testing "detects new entities"
      (let [events (ingest/generate-changelog db [entity-b])]
        (is (= 1 (count events)))
        (is (= :entity/added (:type (first events))))
        (is (= "reep_p22222222" (:reep-id (first events))))))

    (testing "detects unchanged entities"
      (let [events (ingest/generate-changelog db [entity-a])]
        (is (= 1 (count events)))
        (is (= :entity/unchanged (:type (first events))))))

    (testing "detects provider updates"
      (let [entity-a-updated (assoc-in entity-a [:providers :fbref] "abc")
            events (ingest/generate-changelog db [entity-a-updated])]
        (is (= 1 (count events)))
        (is (= :provider/updated (:type (first events))))
        (is (= {:fbref "abc"} (:added-ids (first events))))))

    (testing "handles mixed events"
      (let [entity-a-updated (assoc-in entity-a [:providers :fbref] "abc")
            events (ingest/generate-changelog db [entity-a-updated entity-b])]
        (is (= 2 (count events)))
        (is (= :provider/updated (:type (first events))))
        (is (= :entity/added (:type (second events))))))))
