(ns thorold.api-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ring.mock.request :as mock]
            [cheshire.core :as json]
            [thorold.api :as api]
            [thorold.index :as index]))

(def test-data
  [{:reep/id "reep_p12345678" :reep/type :person/player :person/name "Test Player" :providers {:wikidata "Q1"}}
   {:reep/id "reep_t87654321" :reep/type :team :team/name "Test Team" :providers {:wikidata "Q2"}}])

(def test-db (atom nil))

(defn db-fixture [f]
  (let [indexes (index/build-indexes test-data)
        db {:entities (:by-reep-id indexes)
            :indexes  indexes
            :meta     {:people-count 1 :teams-count 1 :load-ms 1}}]
    (reset! test-db db))
  (f))

(use-fixtures :once db-fixture)

(defn run-req [method uri]
  (let [app (api/create-app @test-db)
        response (app (mock/request method uri))]
    (update response :body #(when % (json/parse-string (if (string? %) % (slurp %)) true)))))

(deftest api-search-test
  (testing "search returns results"
    (let [{:keys [status body]} (run-req :get "/search?name=Test")]
      (is (= 200 status))
      (is (= 2 (:count body)))))
  
  (testing "search filters by type"
    (let [{:keys [status body]} (run-req :get "/search?name=Test&type=player")]
      (is (= 200 status))
      (is (= 1 (:count body))))))

(deftest api-lookup-test
  (testing "lookup by Reep ID"
    (let [{:keys [status body]} (run-req :get "/lookup?id=reep_p12345678")]
      (is (= 200 status))
      (is (= "Test Player" (:name_en (first (:results body)))))))
  
  (testing "lookup missing ID returns count 0"
    (let [{:keys [status body]} (run-req :get "/lookup?id=reep_p99999999")]
      (is (= 200 status))
      (is (= 0 (:count body))))))

(deftest api-resolve-test
  (testing "resolve by qid"
    (let [{:keys [status body]} (run-req :get "/resolve?provider=wikidata&id=Q1")]
      (is (= 200 status))
      (is (= "Test Player" (:name_en (first (:results body)))))))
  
  (testing "resolve missing query param returns 400"
    (let [{:keys [status]} (run-req :get "/resolve?id=1")]
      (is (= 400 status)))))

(deftest api-stats-test
  (testing "stats endpoint returns db stats"
    (let [{:keys [status body]} (run-req :get "/stats")]
      (is (= 200 status))
      (is (contains? body :total-entities)))))
