(ns thorold.history-test
  "Tests for thorold.history using inline fixture data."
  (:require [clojure.test :refer [deftest is testing]]
            [thorold.history :as history]))

;; ---------------------------------------------------------------------------
;; Fixtures — minimal db-like maps with :entities
;; ---------------------------------------------------------------------------

(def db-v1
  {:entities {"reep_p11111111" {:reep/id "reep_p11111111"
                                :reep/type :person/player
                                :person/name "Player A"
                                :providers {:transfermarkt "100"
                                            :wikidata "Q100"}}
              "reep_p22222222" {:reep/id "reep_p22222222"
                                :reep/type :person/player
                                :person/name "Player B"
                                :providers {:transfermarkt "200"
                                            :fbref "abc123"
                                            :wikidata "Q200"}}
              "reep_t33333333" {:reep/id "reep_t33333333"
                                :reep/type :team
                                :team/name "Team C"
                                :providers {:transfermarkt "300"
                                            :wikidata "Q300"}}}})

(def db-v2
  {:entities {"reep_p11111111" {:reep/id "reep_p11111111"
                                :reep/type :person/player
                                :person/name "Player A"
                                :providers {:transfermarkt "100"
                                            :fbref "newid"
                                            :wikidata "Q100"}}
              ;; Player B removed
              "reep_t33333333" {:reep/id "reep_t33333333"
                                :reep/type :team
                                :team/name "Team C"
                                :providers {:transfermarkt "300"
                                            :wikidata "Q300"}}
              ;; New entity
              "reep_p44444444" {:reep/id "reep_p44444444"
                                :reep/type :person/player
                                :person/name "Player D"
                                :providers {:transfermarkt "400"
                                            :wikidata "Q400"}}}})

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest diff-snapshots-test
  (testing "detects added entities"
    (let [diff (history/diff-snapshots db-v1 db-v2)]
      (is (contains? (:added diff) "reep_p44444444"))
      (is (= 1 (count (:added diff))))))

  (testing "detects removed entities"
    (let [diff (history/diff-snapshots db-v1 db-v2)]
      (is (contains? (:removed diff) "reep_p22222222"))
      (is (= 1 (count (:removed diff))))))

  (testing "detects changed entities"
    (let [diff (history/diff-snapshots db-v1 db-v2)]
      (is (contains? (:changed diff) "reep_p11111111"))
      (is (= 1 (count (:changed diff))))))

  (testing "unchanged entities not in diff"
    (let [diff (history/diff-snapshots db-v1 db-v2)]
      (is (not (contains? (:added diff) "reep_t33333333")))
      (is (not (contains? (:removed diff) "reep_t33333333")))
      (is (not (contains? (:changed diff) "reep_t33333333")))))

  (testing "identical snapshots produce empty diff"
    (let [diff (history/diff-snapshots db-v1 db-v1)]
      (is (empty? (:added diff)))
      (is (empty? (:removed diff)))
      (is (empty? (:changed diff))))))

(deftest new-provider-ids-test
  (testing "Player A gained :fbref between v1 and v2"
    (let [result (history/new-provider-ids db-v1 db-v2 :fbref)]
      (is (= 1 (count result)))
      (is (= "reep_p11111111" (:reep/id (first result))))))

  (testing "Player D is new and has :transfermarkt — counts as gained"
    (let [result (history/new-provider-ids db-v1 db-v2 :transfermarkt)]
      (is (= 1 (count result)))
      (is (= "reep_p44444444" (:reep/id (first result)))))))

(deftest lost-provider-ids-test
  (testing "Player B lost all providers because entity was removed"
    (let [result (history/lost-provider-ids db-v1 db-v2 :fbref)]
      (is (= 1 (count result)))
      (is (= "reep_p22222222" (:reep/id (first result)))))))

(deftest entity-history-test
  (testing "tracks entity across snapshots"
    (let [snapshots [["2024-01-01" db-v1]
                     ["2024-06-01" db-v2]]
          result (history/entity-history snapshots "reep_p11111111")]
      (is (= 2 (count result)))
      (is (= "2024-01-01" (:timestamp (first result))))
      (is (some? (:entity (first result))))
      (is (= "2024-06-01" (:timestamp (second result))))))

  (testing "returns nil entity for missing reep-id"
    (let [snapshots [["2024-01-01" db-v1]]
          result (history/entity-history snapshots "reep_pNONEXIST")]
      (is (nil? (:entity (first result)))))))

(deftest coverage-delta-test
  (testing "reports per-provider before/after/delta"
    (let [delta (history/coverage-delta db-v1 db-v2)]
      ;; transfermarkt: was 3 entities, now 3 (lost B, gained D)
      (is (= 0 (:delta (:transfermarkt delta))))
      ;; fbref: was 1 (Player B), now 1 (Player A) — net 0
      (is (= 0 (:delta (:fbref delta))))
      ;; wikidata: was 3, now 3
      (is (= 0 (:delta (:wikidata delta))))))

  (testing "empty db produces all zeros"
    (let [delta (history/coverage-delta {:entities {}} {:entities {}})]
      (is (empty? delta)))))
