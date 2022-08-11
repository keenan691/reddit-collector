;; * reddit.api
(ns reddit.api
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [clj-http.client :as client]
   [malli.transform :as mt]
   [reddit.schema :refer [extracted-subreddit-schema wrap-in-response-schema]]
   [taoensso.timbre :as log]))

(def api-configuration
  {:host "https://www.reddit.com",
   :endpoints
   {::get-popular-subreddits
    {:method       :get,
     :path         "/subreddits/popular",
     :malli/schema [:=>
                    [:cat [:map [:limit pos-int?]]]
                    (wrap-in-response-schema
                      extracted-subreddit-schema)]}}})

(defn get-endpoint-url
  [endpoint]
  (str (-> api-configuration
           :host)
       (-> api-configuration
           :endpoints
           endpoint
           :path)))

(defn decode
  [type data]
  (m/decode type
            data
            (mt/transformer
              mt/json-transformer
              mt/string-transformer
              mt/strip-extra-keys-transformer)))

(defn route->data
  [route-resolver route & params]
  (let [conf            (get-in api-configuration [:endpoints route])
        {:keys [method], :malli/keys [schema]} conf
        [_ params-schema response-schema] schema
        valid-params?   (m/validate params-schema params)
        response        (when valid-params?
                          (->> (first params)
                               (route-resolver route)
                               (decode response-schema)))
        valid-response? (when response (m/validate response-schema response))]

    (cond-> {}
      (not valid-params?)
        (assoc :error
          {:type    :error.type/params-validation,
           :details (-> params-schema
                        (m/explain params)
                        (me/humanize))})

      (and valid-params? (not valid-response?))
        (assoc :error
          {:type    :error.type/response-validation,
           :details (-> response-schema
                        (m/explain response)
                        (me/humanize))})

      valid-response?
        (assoc :data response))))
