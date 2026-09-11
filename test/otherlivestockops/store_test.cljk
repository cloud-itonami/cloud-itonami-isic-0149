(ns otherlivestockops.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [otherlivestockops.store :as store]))

(deftest mem-store-creation
  (testing "Create empty store"
    (let [st (store/mem-store)]
      (is (some? st))
      (is (satisfies? store/Store st))))

  (testing "Create store with initial facilities"
    (let [facilities {"apiary-001" {:id "apiary-001" :name "Sunrise Apiary"}}
          st (store/mem-store {:initial-facilities facilities})]
      (is (some? st))
      (is (satisfies? store/Store st)))))

(deftest registered-facility-retrieval
  (testing "Retrieve existing facility"
    (let [facility {:id "apiary-001" :name "Sunrise Apiary"}
          st (store/mem-store {:initial-facilities {"apiary-001" facility}})]
      (is (= facility (store/registered-facility st "apiary-001")))))

  (testing "Retrieve non-existent facility"
    (let [st (store/mem-store)]
      (is (nil? (store/registered-facility st "no-such-facility")))))

  (testing "nil facility-id returns nil (never falls through to a default)"
    (let [st (store/mem-store {:initial-facilities {"apiary-001" {:id "apiary-001"}}})]
      (is (nil? (store/registered-facility st nil))))))

(deftest add-facility-test
  (testing "Register a new facility"
    (let [st (store/mem-store)
          facility-data {:id "apiary-002" :name "New Apiary"}
          result (store/add-facility st "apiary-002" facility-data)]
      (is (= facility-data result))
      (is (= facility-data (store/registered-facility st "apiary-002")))))

  (testing "Update an existing facility"
    (let [st (store/mem-store {:initial-facilities {"apiary-001" {:id "apiary-001"}}})
          updated {:id "apiary-001" :name "Renamed Apiary"}
          result (store/add-facility st "apiary-001" updated)]
      (is (= updated result))
      (is (= updated (store/registered-facility st "apiary-001"))))))
