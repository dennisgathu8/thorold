(ns thorold.api-test
  "Pure function tests for thorold.api using plain Ring request maps."
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [thorold.api :as api]
            [thorold.index :as index]))

;; ---------------------------------------------------------------------------
;; Mock Database Setup
;; ---------------------------------------------------------------------------

(def mock-entities
  [{:reep/id "reep_p2804f5db"
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
                :fbref "dc7f8a28"
                :sofascore "982780"}}
   {:reep/id "reep_t12345678"
    :reep/type :team
    :team/name "Chelsea"
    :team/country "England"
    :team/founded "1905"
    :team/stadium "Stamford Bridge"
    :providers {:wikidata "Q19080"
                :transfermarkt "631"}}])

(def mock-db
  (let [indexes (index/build-indexes mock-entities)]
    {:entities (:by-reep-id indexes)
     :indexes  indexes
     :names    []
     :meta     {:people-count 1
                :teams-count 1
                :names-count 0
                :people-errors 0
                :teams-errors 0
                :load-ms 5}}))

;; ---------------------------------------------------------------------------
;; Request Helper
;; ---------------------------------------------------------------------------

(defn make-request
  "Builds a minimal Ring request map with :query-params populated."
  ([query-params]
   {:query-params query-params}))

(defn parse-json-body
  "Parses the JSON response body into a Clojure map with keyword keys."
  [response]
  (json/parse-string (:body response) true))

;; ---------------------------------------------------------------------------
;; API Handler Tests
;; ---------------------------------------------------------------------------

(deftest handle-search-test
  (testing "search with valid name"
    (let [req  (make-request {"name" "Cole Palmer"})
          resp (api/handle-search req mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= "application/json" (get-in resp [:headers "Content-Type"])))
      (is (= 1 (:count body)))
      (let [result (first (:results body))]
        (is (= "reep_p2804f5db" (:reep_id result)))
        (is (= "Cole Palmer" (:name_en result)))
        (is (= "player" (:type result)))
        (is (= "Q99760796" (:qid result)))
        (is (= "568177" (get-in result [:external_ids :transfermarkt]))))))

  (testing "search with missing name returns 400"
    (let [req  (make-request {})
          resp (api/handle-search req mock-db)
          body (parse-json-body resp)]
      (is (= 400 (:status resp)))
      (is (= "application/json" (get-in resp [:headers "Content-Type"])))
      (is (= "Required: ?name=Cole Palmer" (:error body)))
      (is (= "missing_param" (:code body)))))

  (testing "search with type filter team"
    (let [req  (make-request {"name" "Cole Palmer" "type" "team"})
          resp (api/handle-search req mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 0 (:count body))))))

(deftest handle-resolve-test
  (testing "resolve with known provider ID"
    (let [req  (make-request {"provider" "transfermarkt" "id" "568177"})
          resp (api/handle-resolve req mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= "application/json" (get-in resp [:headers "Content-Type"])))
      (is (= 1 (:count body)))
      (is (= "Cole Palmer" (:name_en (first (:results body)))))))

  (testing "resolve with unknown provider ID returns empty results"
    (let [req  (make-request {"provider" "transfermarkt" "id" "999999"})
          resp (api/handle-resolve req mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 0 (:count body)))))

  (testing "resolve with missing provider param returns 400"
    (let [req  (make-request {"id" "568177"})
          resp (api/handle-resolve req mock-db)
          body (parse-json-body resp)]
      (is (= 400 (:status resp)))
      (is (= "Required: ?provider=transfermarkt&id=568177" (:error body)))
      (is (= "missing_param" (:code body))))))

(deftest handle-lookup-test
  (testing "lookup by Reep ID"
    (let [req  (make-request {"id" "reep_p2804f5db"})
          resp (api/handle-lookup req mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 1 (:count body)))
      (is (= "Cole Palmer" (:name_en (first (:results body)))))))

  (testing "lookup by Wikidata QID"
    (let [req  (make-request {"id" "Q19080"})
          resp (api/handle-lookup req mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 1 (:count body)))
      (is (= "Chelsea" (:name_en (first (:results body)))))))

  (testing "lookup unknown ID returns empty results"
    (let [req  (make-request {"id" "reep_p99999999"})
          resp (api/handle-lookup req mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 0 (:count body)))))

  (testing "lookup missing ID returns 400"
    (let [req  (make-request {})
          resp (api/handle-lookup req mock-db)
          body (parse-json-body resp)]
      (is (= 400 (:status resp)))
      (is (= "Required: ?id=reep_p2804f5db" (:error body))))))

(deftest handle-stats-test
  (testing "stats returns correct structure"
    (let [resp (api/handle-stats {} mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= "application/json" (get-in resp [:headers "Content-Type"])))
      (is (= 2 (:total-entities body)))
      (is (= 1 (get-in body [:by-type :player])))
      (is (= 1 (get-in body [:by-type :team]))))))

(deftest batch-lookup-test
  (testing "valid batch lookup"
    (let [req  {:body (json/generate-string {"ids" ["reep_p2804f5db" "Q19080" "invalid"]})}
          resp (api/handle-batch-lookup req mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 2 (:count body)))
      (is (= 1 (:not_found body)))
      (is (= "Cole Palmer" (:name_en (first (:results body)))))
      (is (= "Chelsea" (:name_en (second (:results body)))))))

  (testing "batch lookup with missing ids"
    (let [req  {:body (json/generate-string {})}
          resp (api/handle-batch-lookup req mock-db)
          body (parse-json-body resp)]
      (is (= 400 (:status resp)))
      (is (= "missing_param" (:code body)))))

  (testing "batch lookup over limit"
    (let [ids  (mapv #(str "reep_p" %) (range 101))
          req  {:body (json/generate-string {"ids" ids})}
          resp (api/handle-batch-lookup req mock-db)
          body (parse-json-body resp)]
      (is (= 400 (:status resp)))
      (is (= "batch_limit_exceeded" (:code body))))))

(deftest batch-resolve-test
  (testing "valid batch resolve"
    (let [req  {:body (json/generate-string {"items" [{"provider" "transfermarkt" "id" "568177"}
                                                     {"provider" "wikidata" "id" "Q19080"}
                                                     {"provider" "transfermarkt" "id" "99999"}]})}
          resp (api/handle-batch-resolve req mock-db)
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 2 (:count body)))
      (is (= 1 (:not_found body)))))

  (testing "batch resolve over limit"
    (let [items (mapv (fn [i] {"provider" "transfermarkt" "id" (str i)}) (range 101))
          req   {:body (json/generate-string {"items" items})}
          resp  (api/handle-batch-resolve req mock-db)
          body  (parse-json-body resp)]
      (is (= 400 (:status resp)))
      (is (= "batch_limit_exceeded" (:code body))))))

(deftest content-negotiation-test
  (testing "serves EDN when Accept header requests it"
    (let [app    (api/create-app mock-db)
          req    {:request-method :get
                  :uri            "/stats"
                  :headers        {"accept" "application/edn"}}
          resp   (app req)
          body   (edn/read-string (:body resp))]
      (is (= 200 (:status resp)))
      (is (= "application/edn" (get-in resp [:headers "Content-Type"])))
      (is (= 2 (:total-entities body)))))

  (testing "serves JSON when Accept header requests JSON or is missing"
    (let [app    (api/create-app mock-db)
          req    {:request-method :get
                  :uri            "/stats"
                  :headers        {"accept" "application/json"}}
          resp   (app req)
          body   (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= "application/json" (get-in resp [:headers "Content-Type"])))
      (is (= 2 (:total-entities body))))))

