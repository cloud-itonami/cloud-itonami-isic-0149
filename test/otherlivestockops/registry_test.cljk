(ns otherlivestockops.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [otherlivestockops.registry :as registry]))

(deftest cost-exceeds-threshold-test
  (testing "Cost within threshold"
    (is (false? (registry/cost-exceeds-threshold? 400 500))))

  (testing "Cost at threshold (inclusive boundary, not exceeded)"
    (is (false? (registry/cost-exceeds-threshold? 500 500))))

  (testing "Cost exceeds threshold"
    (is (true? (registry/cost-exceeds-threshold? 600 500)))))

(deftest husbandry-count-non-positive-test
  (testing "Positive count is valid"
    (is (false? (registry/husbandry-count-non-positive? 24))))

  (testing "Zero count is invalid"
    (is (true? (registry/husbandry-count-non-positive? 0))))

  (testing "Negative count is invalid"
    (is (true? (registry/husbandry-count-non-positive? -5)))))

(deftest confidence-below-floor-test
  (testing "Confidence above floor"
    (is (false? (registry/confidence-below-floor? 0.9 0.7))))

  (testing "Confidence at floor (inclusive, not below)"
    (is (false? (registry/confidence-below-floor? 0.7 0.7))))

  (testing "Confidence below floor"
    (is (true? (registry/confidence-below-floor? 0.5 0.7)))))
