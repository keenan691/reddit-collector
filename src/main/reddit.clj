;; * main.reddit
(ns main.reddit
  (:require
   [aleph.http :as http]
   [byte-streams :as bstr]
   [camel-snake-kebab.core :as csk]
   [clojure.data.json :as json]
   [main.schema :refer [api-configuration]]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [manifold.deferred :as d]
   [taoensso.timbre :as log]))

;; ** Resolver
(defn read-json-str
  [s]
  (json/read-str s :key-fn csk/->kebab-case-keyword))

(defn write-json-str
  [d]
  (json/write-str d :key-fn csk/->snake_case_string))

(defn get-url
  [endpoint]
  (str (-> api-configuration
           :host)
       (-> api-configuration
           :endpoints
           endpoint
           :path)))

(defn get-body
  [response]
  (-> response
      :body
      bstr/to-string))

(defn http-resolver
  [route params]
  @(d/chain (http/get (str (get-url route) "/.json?limit=2"))
            get-body
            read-json-str))

;; ** Collect data
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
  (log/info route)
  (let [conf            (get-in api-configuration [:endpoints route])
        {:keys [method], :malli/keys [schema]} conf
        [_ params-schema response-schema] schema
        valid-params?   (m/validate params-schema params)
        response        (when valid-params?
                          (->> params
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

(defn extract-data
  [response]
  {:popular-reddits
     (->> (get-in response [:data :data :children])
          (mapv :data))})

(defn resolve-route
  ([route-resolver route params]
   (let [data (try (route->data
                     route-resolver
                     route
                     params)
                   (catch Exception e
                     {:error {:type    :error.type/api-error,
                              :details e}}))]

     (cond-> data
       (:data data) extract-data))))

(defn collect-data
  []
  (let [r (resolve-route
            http-resolver
            :api/get-popular-subreddits
            {:limit 100})
        f "reddit-popular-subreddits.json"]

    (tap> r)
    (when (not (:error r))
      (spit f (write-json-str r))
      (log/info "saved"))

    (when (:error r)
      (tap> (:error r))
      (log/info "error"))))

#_
(collect-data)

(defn x
  []
  (tap> (collect-data)))

