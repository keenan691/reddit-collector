;; * reddit.collector
(ns reddit.collector
  (:require
    [camel-snake-kebab.core :as csk]
    [clj-http.client :as client]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [reddit.api :refer [get-endpoint-url route->data] :as api]
    [taoensso.timbre :as log]))

;; ** Http Resolver

(defn read-json-str
  [s]
  (json/read-str s :key-fn csk/->kebab-case-keyword))

(defn write-json-str
  [d]
  (json/write-str d
                  :key-fn
                    csk/->snake_case_string
                  :date-formatter
                    (-> "yyyy-MM-dd'T'HH:mm:ss'Z'"
                        java.time.format.DateTimeFormatter/ofPattern
                        (.withZone (java.time.ZoneId/systemDefault)))))

(defn http-resolver
  [route params]
  (-> (client/get
        (str (get-endpoint-url route) ".json")
        {:headers
           {"User-Agent"
              "Mozilla/5.0 (Windows NT 6.1;) Gecko/20100101 Firefox/13.0.1"},
         :query-params params})
      :body
      read-json-str))

#_
  (http-resolver ::api/get-popular-subreddits {:limit 1})

;; ** Data collector

(defn- extract-data
  [response]
  {:popular-reddits
     (->> (get-in response [:data :data :children])
          (mapv :data))})

(defn resolve-route
  [route-resolver route params]
  (let [data (try (route->data
                    route-resolver
                    route
                    params)
                  (catch Exception e
                    {:error {:type    :error.type/api-error,
                             :details e}}))]

    (cond-> data
      (:data data) extract-data)))

(defn- route->file-name
  [r]
  (-> r
      name
      (str/replace-first "get-" "")))

(defn collect-data-to-json-file
  [route limit]
  (let [response    (resolve-route
                      http-resolver
                      route
                      {:limit limit})
        error       (:error response)
        output-path (str (route->file-name route) ".json")]

    (when (not error)
      (->> response
           write-json-str
           (spit output-path))

      (println (str "Collected "
                      (count (:popular-reddits response))
                    " subreddits from route "
                      route
                    " to file "
                      output-path)))

    (when error
      (println "Something is wrong!!")
      (println (:type error) " | " (ex-message (:details error)))
      #_(println (ex-data (:details error))))))

(defn -main
  []
  (collect-data-to-json-file ::api/get-popular-subreddits 100))

(defn x
  []
  (collect-data-to-json-file ::api/get-popular-subreddits 1))

(comment
  (collect-data-to-json-file ::api/get-popular-subreddits 100))
