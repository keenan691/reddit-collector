(ns main.reddit-test
  (:require
    [clojure.test :refer [deftest is]]
    [main.reddit :refer [resolve-route]]
    [main.schema :refer
     [create-response-schema extracted-subreddit-schema
      infered-subreddit-schema]]
    [malli.core :as m]
    [malli.generator :as mg]))

(defn gen-resolver
  [_ _]
  (mg/generate (create-response-schema infered-subreddit-schema)))

(defn error-resolver [_ _] (throw (Exception. "Connection Error")))
(defn wrong-response-resolver [_ _] {:kind 2})

(deftest parsing
  (let [r (-> (resolve-route gen-resolver ::get-popular-subreddits {:limit 1}))]
    (is (m/validate [:map
                     [:popular-reddits
                      [:vector extracted-subreddit-schema]]]
          r))))

(deftest params-validation
  (let [r (resolve-route gen-resolver
                         ::get-popular-subreddits
                         {:offset 1})]
    (is (= "missing required key"
           (get-in r [:error :details 0 :limit 0])))))

(deftest response-validation
  (let [r (resolve-route wrong-response-resolver
                         ::get-popular-subreddits
                         {:limit 1})]
    (is (= :error.type/response-validation
           (get-in r [:error :type])))))

(deftest api-error
  (let [r (resolve-route error-resolver
                         ::get-popular-subreddits
                         {:limit 1})]
    (is (= :error.type/api-error
           (get-in r [:error :type])))))
