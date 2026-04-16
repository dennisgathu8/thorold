(ns thorold.cli-test
  "Tests for thorold.cli — captures output with with-out-str."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [thorold.cli :as cli]
            [thorold.index :as index]))

(def sample-entities
  [{:reep/id     "reep_p2804f5db"
    :reep/type   :person/player
    :person/name "Cole Palmer"
    :reep/dob    "2002-05-06"
    :providers   {:transfermarkt "568177"
                  :fbref         "dc7f8a28"
                  :wikidata      "Q99760796"}}
   {:reep/id     "reep_pbd9a559b"
    :reep/type   :person/player
    :person/name "Lionel Messi"
    :providers   {:transfermarkt "28003"
                  :fbref         "d70ce98e"
                  :wikidata      "Q615"}}])

(def test-db
  (let [indexes (index/build-indexes sample-entities)]
    {:entities (:by-reep-id indexes)
     :indexes  indexes
     :meta     {:people-count 2 :teams-count 0 :load-ms 10}}))

(deftest cmd-search-test
  (testing "finds entities by name"
    (let [result (cli/cmd-search {:name "Cole Palmer" :limit 10} test-db)]
      (is (pos? (:count result)))
      (is (= "Cole Palmer" (:person/name (first (:results result)))))))

  (testing "returns empty for no match"
    (let [result (cli/cmd-search {:name "zzzznonexistent" :limit 10} test-db)]
      (is (zero? (:count result))))))

(deftest cmd-resolve-test
  (testing "resolves provider ID"
    (let [result (cli/cmd-resolve {:provider "transfermarkt" :id "568177"} test-db)]
      (is (= 1 (:count result)))
      (is (= "Cole Palmer" (:person/name (first (:results result)))))))

  (testing "miss returns count 0"
    (let [result (cli/cmd-resolve {:provider "transfermarkt" :id "999999"} test-db)]
      (is (zero? (:count result))))))

(deftest cmd-translate-test
  (testing "translates between providers"
    (let [result (cli/cmd-translate {:source "transfermarkt" :id "568177" :target "fbref"} test-db)]
      (is (:found result))
      (is (= "dc7f8a28" (:result result)))))

  (testing "miss returns found=false"
    (let [result (cli/cmd-translate {:source "transfermarkt" :id "999999" :target "fbref"} test-db)]
      (is (not (:found result))))))

(deftest cmd-lookup-test
  (testing "looks up by Reep ID"
    (let [result (cli/cmd-lookup {:id "reep_p2804f5db"} test-db)]
      (is (= 1 (:count result)))))

  (testing "looks up by QID"
    (let [result (cli/cmd-lookup {:id "Q615"} test-db)]
      (is (= 1 (:count result))))))

(deftest cmd-stats-test
  (testing "returns stats map"
    (let [result (cli/cmd-stats {} test-db)]
      (is (= 2 (:total-entities result))))))

(deftest print-result-human
  (testing "human format outputs to stdout"
    (let [result {:results sample-entities :count 2}
          output (with-out-str (cli/print-result result {:format "human"}))]
      (is (clojure.string/includes? output "Cole Palmer"))
      (is (clojure.string/includes? output "Lionel Messi")))))

(deftest print-result-edn
  (testing "edn format outputs valid edn"
    (let [result {:results [] :count 0}
          output (with-out-str (cli/print-result result {:format "edn"}))]
      (is (some? (read-string output))))))

(deftest dispatch-test
  (testing "dispatches search command"
    (let [result (cli/dispatch {:command "search" :arguments ["Cole" "Palmer"]} test-db)]
      (is (some? (:results result)))))

  (testing "unknown command returns error"
    (let [result (cli/dispatch {:command "nope" :arguments []} test-db)]
      (is (some? (:error result))))))
