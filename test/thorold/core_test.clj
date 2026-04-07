(ns thorold.core-test
  (:require [clojure.test :refer [deftest is testing]]))

(deftest core-namespace-loads
  (testing "thorold.core namespace loads without errors"
    (is (do (require 'thorold.core) true))))
