(ns thorold.id-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [thorold.id :as id]))

(deftest reep-id-format
  (testing "deterministic ID has correct format"
    (let [rid (id/reep-id :player "Q615")]
      (is (re-matches #"reep_p[0-9a-f]{8}" rid))))

  (testing "all type prefixes produce correct format"
    (doseq [[type prefix] {:player "p" :coach "c" :team "t"
                           :competition "l" :season "s"}]
      (let [rid (id/reep-id type "Q12345")]
        (is (re-matches (re-pattern (str "reep_" prefix "[0-9a-f]{8}")) rid)
            (str "Failed for type " type))))))

(deftest reep-id-deterministic
  (testing "same inputs produce same output"
    (is (= (id/reep-id :player "Q615")
           (id/reep-id :player "Q615"))))

  (testing "different seeds produce different outputs"
    (is (not= (id/reep-id :player "Q615")
              (id/reep-id :player "Q99760796"))))

  (testing "different types with same seed produce different outputs"
    (is (not= (id/reep-id :player "Q615")
              (id/reep-id :coach "Q615")))))

(deftest reep-id-validation
  (testing "valid Reep IDs"
    (is (id/valid-reep-id? "reep_p2804f5db"))
    (is (id/valid-reep-id? "reep_t0871097b"))
    (is (id/valid-reep-id? "reep_c9103de59"))
    (is (id/valid-reep-id? "reep_lb3d230cb"))
    (is (id/valid-reep-id? "reep_sa7f63ba6")))

  (testing "invalid Reep IDs"
    (is (not (id/valid-reep-id? nil)))
    (is (not (id/valid-reep-id? "")))
    (is (not (id/valid-reep-id? "reep_x1234567")))
    (is (not (id/valid-reep-id? "reep_p123")))
    (is (not (id/valid-reep-id? "not_a_reep_id")))))

(deftest reep-id-type-extraction
  (testing "extracts correct type from valid IDs"
    (is (= :player (id/reep-id-type "reep_p2804f5db")))
    (is (= :team (id/reep-id-type "reep_t0871097b")))
    (is (= :coach (id/reep-id-type "reep_c9103de59")))
    (is (= :competition (id/reep-id-type "reep_lb3d230cb")))
    (is (= :season (id/reep-id-type "reep_sa7f63ba6"))))

  (testing "returns nil for invalid IDs"
    (is (nil? (id/reep-id-type "garbage")))))

(deftest mint-reep-id-random
  (testing "minted IDs have correct format"
    (doseq [type [:player :coach :team :competition :season]]
      (let [rid (id/mint-reep-id type)]
        (is (id/valid-reep-id? rid)))))

  (testing "minted IDs are non-deterministic"
    (is (not= (id/mint-reep-id :player)
              (id/mint-reep-id :player)))))

(deftest mint-unique-reep-id-test
  (testing "avoids collisions with existing IDs"
    (let [existing #{"reep_p00000000"}
          new-id   (id/mint-unique-reep-id :player existing)]
      (is (id/valid-reep-id? new-id))
      (is (not (contains? existing new-id))))))

(deftest error-handling
  (testing "invalid type throws ex-info"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid entity type"
                          (id/reep-id :invalid "Q615"))))

  (testing "empty seed throws ex-info"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Seed must be"
                          (id/reep-id :player ""))))

  (testing "nil seed throws ex-info"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Seed must be"
                          (id/reep-id :player nil)))))

;; Property-based tests
(defspec reep-id-always-valid-format 100
  (prop/for-all [seed (gen/not-empty gen/string-alphanumeric)
                 type (gen/elements [:player :coach :team :competition :season])]
    (id/valid-reep-id? (id/reep-id type seed))))

(defspec reep-id-is-deterministic 100
  (prop/for-all [seed (gen/not-empty gen/string-alphanumeric)
                 type (gen/elements [:player :coach :team :competition :season])]
    (= (id/reep-id type seed) (id/reep-id type seed))))

(defspec mint-reep-id-always-valid 100
  (prop/for-all [type (gen/elements [:player :coach :team :competition :season])]
    (id/valid-reep-id? (id/mint-reep-id type))))
