(ns thorold.query-test
  (:require [clojure.test :refer [deftest is testing]]
            [thorold.query :as query]
            [thorold.index :as index]))

(def sample-entities
  [{:reep/id     "reep_p2804f5db"
    :reep/type   :person/player
    :person/name "Cole Palmer"
    :person/full-name "Cole Jermaine Palmer"
    :person/dob  "2002-05-06"
    :providers   {:transfermarkt "568177"
                  :fbref         "dc7f8a28"
                  :wikidata      "Q99760796"}}
   {:reep/id     "reep_pbd9a559b"
    :reep/type   :person/player
    :person/name "Lionel Messi"
    :person/dob  "1987-06-24"
    :providers   {:transfermarkt "28003"
                  :fbref         "d70ce98e"
                  :wikidata      "Q615"}}
   {:reep/id    "reep_t0871097b"
    :reep/type  :team
    :team/name  "Arsenal F.C."
    :providers  {:transfermarkt "11"
                 :wikidata      "Q9616"}}])

(def test-db
  (let [indexes (index/build-indexes sample-entities)]
    {:entities (:by-reep-id indexes)
     :indexes  indexes}))

;; --- lookup ---

(deftest lookup-by-reep-id
  (testing "finds entity by Reep ID"
    (let [entity (query/lookup test-db "reep_p2804f5db")]
      (is (some? entity))
      (is (= "Cole Palmer" (:person/name entity)))))

  (testing "returns nil for unknown Reep ID"
    (is (nil? (query/lookup test-db "reep_p00000000")))))

(deftest lookup-by-qid
  (testing "finds entity by Wikidata QID"
    (let [entity (query/lookup test-db "Q615")]
      (is (some? entity))
      (is (= "Lionel Messi" (:person/name entity)))))

  (testing "returns nil for unknown QID"
    (is (nil? (query/lookup test-db "Q00000000")))))

(deftest lookup-malformed-input
  (testing "throws on nil input"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"non-empty string"
                          (query/lookup test-db nil))))

  (testing "throws on empty string"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"non-empty string"
                          (query/lookup test-db "")))))

;; --- resolve ---

(deftest resolve-provider-id
  (testing "resolves Transfermarkt ID to entity"
    (let [entity (query/resolve test-db :transfermarkt "568177")]
      (is (some? entity))
      (is (= "Cole Palmer" (:person/name entity)))))

  (testing "resolves FBref ID to entity"
    (let [entity (query/resolve test-db :fbref "d70ce98e")]
      (is (some? entity))
      (is (= "Lionel Messi" (:person/name entity)))))

  (testing "returns nil for unknown provider ID"
    (is (nil? (query/resolve test-db :transfermarkt "999999"))))

  (testing "throws on invalid provider type"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"keyword"
                          (query/resolve test-db "transfermarkt" "568177")))))

;; --- translate ---

(deftest translate-reep-to-provider
  (testing "translates Reep ID to provider ID"
    (is (= "dc7f8a28" (query/translate test-db "reep_p2804f5db" :fbref)))
    (is (= "568177" (query/translate test-db "reep_p2804f5db" :transfermarkt))))

  (testing "returns nil for missing provider"
    (is (nil? (query/translate test-db "reep_p2804f5db" :unknown_provider))))

  (testing "returns nil for unknown Reep ID"
    (is (nil? (query/translate test-db "reep_p00000000" :fbref)))))

;; --- search ---

(deftest search-by-name
  (testing "finds exact match"
    (let [results (query/search test-db "Cole Palmer" {})]
      (is (pos? (count results)))
      (is (= "Cole Palmer" (:person/name (first results))))))

  (testing "finds partial match"
    (let [results (query/search test-db "Palmer" {})]
      (is (pos? (count results)))))

  (testing "finds team"
    (let [results (query/search test-db "Arsenal" {})]
      (is (pos? (count results)))
      (is (= :team (:reep/type (first results))))))

  (testing "type filter works"
    (let [results (query/search test-db "Cole Palmer" {:type :team})]
      (is (zero? (count results)))))

  (testing "limit works"
    (let [results (query/search test-db "a" {:limit 1})]
      (is (<= (count results) 1))))

  (testing "no results returns empty vector"
    (let [results (query/search test-db "zzzznonexistent" {})]
      (is (zero? (count results)))))

  (testing "throws on empty query"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"non-empty"
                          (query/search test-db "" {})))))
