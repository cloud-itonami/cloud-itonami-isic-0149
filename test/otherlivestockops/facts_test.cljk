(ns otherlivestockops.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [otherlivestockops.facts :as facts]))

(deftest husbandry-line-lookup
  (testing "Lookup valid husbandry line"
    (let [l (facts/husbandry-line-by-id "apiculture")]
      (is (= "apiculture" (:id l)))
      (is (= "養蜂 (apiculture / beekeeping)" (:name l)))))

  (testing "Lookup invalid husbandry line"
    (is (nil? (facts/husbandry-line-by-id "unknown")))))

(deftest husbandry-line-coverage
  (testing "Illustrative other-animal lines are all present"
    (are [id] (some? (facts/husbandry-line-by-id id))
      "apiculture"
      "rabbit"
      "fur-animal"
      "sericulture"
      "game-farming")))

(deftest supply-category-lookup
  (testing "Lookup valid supply category"
    (let [c (facts/supply-category-by-id "feed")]
      (is (= "feed" (:id c)))
      (is (= "飼料" (:name c)))))

  (testing "Lookup invalid supply category"
    (is (nil? (facts/supply-category-by-id "unknown")))))

(deftest supply-category-cost-thresholds
  (testing "Category-specific cost thresholds"
    (are [id expected] (= expected (:cost-threshold (facts/supply-category-by-id id)))
      "feed"                  500
      "veterinary-supply"     500
      "husbandry-equipment"   1000)))

(deftest default-cost-threshold-value
  (testing "Default fallback threshold matches the conservative baseline"
    (is (= 500 facts/default-cost-threshold))))

(deftest health-concern-lookup
  (testing "Lookup valid health/biosecurity concern"
    (let [c (facts/health-concern-by-id "foulbrood")]
      (is (= "foulbrood" (:id c)))
      (is (true? (:notifiable c)))))

  (testing "Notifiable flag distinguishes reportable diseases"
    (are [id expected-notifiable?] (= expected-notifiable? (:notifiable (facts/health-concern-by-id id)))
      "varroa"      false
      "foulbrood"   true
      "myxomatosis" true
      "rhd"         true
      "pebrine"     false))

  (testing "Lookup invalid concern"
    (is (nil? (facts/health-concern-by-id "unknown")))))
