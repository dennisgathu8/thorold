(ns thorold.index-test
  (:require [clojure.test :refer [deftest is testing]]
            [thorold.index :as index]))

(def sample-entities
  [{:reep/id     "reep_p2804f5db"
    :reep/type   :person/player
    :person/name "Cole Palmer"
    :person/full-name "Cole Jermaine Palmer"
    :providers   {:transfermarkt "568177"
                  :fbref         "dc7f8a28"
                  :wikidata      "Q99760796"}}
   {:reep/id     "reep_pbd9a559b"
    :reep/type   :person/player
    :person/name "Lionel Messi"
    :providers   {:transfermarkt "28003"
                  :fbref         "d70ce98e"
                  :wikidata      "Q615"}}
   {:reep/id    "reep_t0871097b"
    :reep/type  :team
    :team/name  "Arsenal F.C."
    :providers  {:transfermarkt "11"
                 :fbref         "18bb7c10"
                 :wikidata      "Q9616"}}])

(deftest build-indexes-basic
  (let [indexes (index/build-indexes sample-entities)]

    (testing "by-reep-id index contains all entities"
      (is (= 3 (count (:by-reep-id indexes))))
      (is (= "Cole Palmer" (:person/name (get (:by-reep-id indexes) "reep_p2804f5db")))))

    (testing "by-provider index maps [provider id] → reep-id"
      (is (= "reep_p2804f5db" (get (:by-provider indexes) [:transfermarkt "568177"])))
      (is (= "reep_pbd9a559b" (get (:by-provider indexes) [:fbref "d70ce98e"])))
      (is (= "reep_t0871097b" (get (:by-provider indexes) [:transfermarkt "11"]))))

    (testing "by-qid index maps QID → reep-id"
      (is (= "reep_p2804f5db" (get (:by-qid indexes) "Q99760796")))
      (is (= "reep_pbd9a559b" (get (:by-qid indexes) "Q615")))
      (is (= "reep_t0871097b" (get (:by-qid indexes) "Q9616"))))

    (testing "by-name index maps normalized name → #{reep-id}"
      (is (contains? (get (:by-name indexes) "cole palmer") "reep_p2804f5db"))
      (is (contains? (get (:by-name indexes) "lionel messi") "reep_pbd9a559b"))
      (is (contains? (get (:by-name indexes) "arsenal fc") "reep_t0871097b")))))

(deftest build-indexes-full-name
  (testing "full name also indexed"
    (let [indexes (index/build-indexes sample-entities)]
      (is (contains? (get (:by-name indexes) "cole jermaine palmer") "reep_p2804f5db")))))

(deftest build-indexes-empty
  (testing "empty input produces empty indexes"
    (let [indexes (index/build-indexes [])]
      (is (= {} (:by-reep-id indexes)))
      (is (= {} (:by-provider indexes)))
      (is (= {} (:by-qid indexes)))
      (is (= {} (:by-name indexes))))))

(deftest index-stats-test
  (let [indexes (index/build-indexes sample-entities)
        stats   (index/index-stats indexes)]
    (is (= 3 (:by-reep-id-count stats)))
    (is (pos? (:by-provider-count stats)))
    (is (= 3 (:by-qid-count stats)))
    (is (pos? (:by-name-count stats)))))

(deftest normalize-name-test
  (testing "basic normalization"
    (is (= "muller" (index/normalize-name "Müller")))
    (is (= "jose mourinho" (index/normalize-name "José Mourinho")))
    (is (= "obrien" (index/normalize-name "O'Brien")))
    (is (= "van der sar" (index/normalize-name "van der Sar"))))

  (testing "whitespace handling"
    (is (= "a b" (index/normalize-name "  a   b  "))))

  (testing "nil input"
    (is (nil? (index/normalize-name nil)))))
