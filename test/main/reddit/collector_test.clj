;; * reddit.collector-test
(ns reddit.collector-test
  (:require
    [clojure.test :refer [deftest is]]
    [reddit.collector :refer [resolve-route]]
    [reddit.api :as api]
    [reddit.schema :refer
     [wrap-in-response-schema extracted-subreddit-schema
      infered-subreddit-schema]]
    [malli.core :as m]
    [malli.generator :as mg]))

;; ** Mocked resolvers

(defn gen-resolver
  [_ _]
  (mg/generate (wrap-in-response-schema infered-subreddit-schema)))

(defn error-resolver [_ _] (throw (Exception. "Connection Error")))
(defn wrong-response-resolver [_ _] {:kind 2})

;; ** Tests

(deftest parsing
  (let [r (-> (resolve-route gen-resolver
                             ::api/get-popular-subreddits
                             {:limit 1}))]
    (is (m/validate [:map
                     [:popular-reddits
                      [:vector extracted-subreddit-schema]]]
                    r))))

(deftest params-validation
  (let [r (resolve-route gen-resolver
                         ::api/get-popular-subreddits
                         {:offset 1})]
    (is (= "missing required key"
           (get-in r [:error :details 0 :limit 0])))))

(deftest response-validation
  (let [r (resolve-route wrong-response-resolver
                         ::api/get-popular-subreddits
                         {:limit 1})]
    (is (= :error.type/response-validation
           (get-in r [:error :type])))))

(deftest api-error
  (let [r (resolve-route error-resolver
                         ::api/get-popular-subreddits
                         {:limit 1})]
    (is (= :error.type/api-error
           (get-in r [:error :type])))))
