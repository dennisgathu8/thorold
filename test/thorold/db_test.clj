(ns thorold.db-test
  (:require [clojure.test :refer [deftest is testing]]
            [thorold.db :as db]
            [thorold.index :as index]))

(def mini-db
  "A minimal db for testing db-stats without loading from files."
  (let [entities [{:reep/id     "reep_p11111111"
                   :reep/type   :person/player
                   :person/name "Test Player"
                   :providers   {:transfermarkt "111" :wikidata "Q111"}}
                  {:reep/id    "reep_t22222222"
                   :reep/type  :team
                   :team/name  "Test Team"
                   :providers  {:transfermarkt "222" :wikidata "Q222"}}]
        indexes  (index/build-indexes entities)]
    {:entities (:by-reep-id indexes)
     :indexes  indexes
     :meta     {:people-count 1 :teams-count 1 :load-ms 42}}))

(deftest db-stats-test
  (testing "returns correct total"
    (let [stats (db/db-stats mini-db)]
      (is (= 2 (:total-entities stats)))))

  (testing "by-type breakdown"
    (let [stats (db/db-stats mini-db)]
      (is (= 1 (get (:by-type stats) "player")))
      (is (= 1 (get (:by-type stats) "team")))))

  (testing "by-provider breakdown"
    (let [stats (db/db-stats mini-db)]
      (is (= 2 (get (:by-provider stats) "transfermarkt")))
      (is (= 2 (get (:by-provider stats) "wikidata"))))))
