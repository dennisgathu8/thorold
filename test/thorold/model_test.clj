(ns thorold.model-test
  (:require [clojure.test :refer [deftest is testing]]
            [thorold.model :as model]))

(deftest person-schema-validation
  (testing "valid player entity"
    (is (model/valid?
         {:reep/id     "reep_p2804f5db"
          :reep/type   :person/player
          :person/name "Cole Palmer"
          :person/dob  "2002-05-06"
          :providers   {:transfermarkt "568177"
                        :fbref         "dc7f8a28"}})))

  (testing "valid coach entity"
    (is (model/valid?
         {:reep/id     "reep_c9103de59"
          :reep/type   :person/coach
          :person/name "Pep Guardiola"
          :providers   {:transfermarkt "5672"}})))

  (testing "missing required fields"
    (is (not (model/valid?
              {:reep/type   :person/player
               :person/name "No ID"
               :providers   {}}))))

  (testing "optional fields can be nil"
    (is (model/valid?
         {:reep/id              "reep_pabcdef01"
          :reep/type            :person/player
          :person/name          "Test Player"
          :person/dob           nil
          :person/nationality   nil
          :person/height-cm     nil
          :providers            {}}))))

(deftest team-schema-validation
  (testing "valid team entity"
    (is (model/valid?
         {:reep/id      "reep_t0871097b"
          :reep/type    :team
          :team/name    "Arsenal F.C."
          :team/country "United Kingdom"
          :providers    {:transfermarkt "11"
                         :fbref         "18bb7c10"}})))

  (testing "invalid team — wrong type"
    (is (not (model/valid?
              {:reep/id   "reep_t0871097b"
               :reep/type :person/player
               :team/name "Arsenal"
               :providers {}})))))

(deftest validate!-throws-structured-error
  (testing "throws ex-info with structured data on invalid entity"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Entity validation failed"
         (model/validate! {:bad "entity"}))))

  (testing "ex-data contains :thorold/error-type"
    (try
      (model/validate! {:bad "entity"})
      (catch clojure.lang.ExceptionInfo e
        (is (= :validation-error (:thorold/error-type (ex-data e))))
        (is (some? (:errors (ex-data e))))))))

(deftest provider-key-registry
  (testing "person provider keys cover all 40 key_* columns"
    (is (= 40 (count model/person-provider-keys))))

  (testing "team provider keys cover all 22 key_* columns"
    (is (= 22 (count model/team-provider-keys))))

  (testing "all values are keywords"
    (is (every? keyword? (vals model/person-provider-keys)))
    (is (every? keyword? (vals model/team-provider-keys)))))
