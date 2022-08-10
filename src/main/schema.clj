;; * Main schema
(ns main.schema
  (:require
    [clojure.set :as set]
    [malli.util :as mu]))

(declare api-configuration)

;; ** Infered schema

#_
  (defonce cached-response (atom nil))
#_
  (comment
    (reset! cached-response (http-resolver ::get-popular-subreddits
                                           {:limit 100}))
    (tap> ((mp/provider {:registry (m/default-schemas)})
            [@cached-response])))

(def infered-subreddit-schema
  [:map
   [:created double?]
   [:description string?]
   [:url string?]
   [:display-name string?]

   [:name string?]
   [:banner-background-color string?]
   [:allow-prediction-contributors boolean?]
   [:original-content-tag-enabled boolean?] [:submit-text string?]
   [:can-assign-user-flair boolean?] [:allow-talks boolean?]
   [:user-flair-text :nil]
   [:comment-contribution-settings
    [:map
     [:allowed-media-types {:optional true} [:maybe [:vector string?]]]]]
   [:wiki-enabled boolean?] [:icon-img [:maybe string?]]
   [:community-icon string?] [:quarantine boolean?]
   [:allow-discovery boolean?]
   [:prediction-leaderboard-entry-type string?] [:subreddit-type string?]
   [:notification-level :nil] [:user-flair-css-class :nil]
   [:is-enrolled-in-new-modmail :nil] [:user-flair-position string?]
   [:banner-size [:maybe [:vector int?]]] [:allow-galleries boolean?]
   [:user-can-flair-in-sr :nil] [:subscribers int?]
   [:content-category {:optional true} string?]
   [:allow-chat-post-creation boolean?] [:allow-videogifs boolean?]
   [:disable-contributor-requests boolean?]
   [:icon-size [:maybe [:vector int?]]]
   [:allow-predictions-tournament boolean?] [:accounts-active :nil]
   [:banner-background-image string?]
   [:public-description string?] [:accept-followers boolean?]
   [:emojis-enabled boolean?] [:allow-images boolean?]
   [:user-sr-flair-enabled :nil] [:public-traffic boolean?]
   [:submit-text-label [:maybe string?]] [:link-flair-enabled boolean?]
   [:spoilers-enabled boolean?]
   [:restrict-commenting boolean?] [:advertiser-category string?]
   [:whitelist-status string?] [:user-has-favorited :nil]
   [:should-archive-posts boolean?] [:user-is-muted :nil]
   [:show-media-preview boolean?] [:allow-predictions boolean?]
   [:user-is-contributor :nil] [:suggested-comment-sort [:maybe string?]]
   [:allowed-media-in-comments [:vector string?]] [:title string?]
   [:user-flair-template-id :nil] [:submission-type string?]
   [:lang string?] [:accounts-active-is-fuzzed boolean?]
   [:is-crosspostable-subreddit boolean?]
   [:submit-text-html [:maybe string?]] [:description-html string?]
   [:user-flair-enabled-in-sr boolean?] [:all-original-content boolean?]
   [:should-show-media-in-comments-setting boolean?]
   [:emojis-custom-size [:maybe [:vector int?]]]
   [:comment-score-hide-mins int?] [:allow-polls boolean?] [:id string?]
   [:community-reviewed boolean?] [:user-is-moderator :nil]
   [:key-color string?] [:has-menu-widget boolean?]
   [:user-is-banned :nil] [:show-media boolean?]
   [:display-name-prefixed string?] [:user-sr-theme-enabled boolean?]
   [:wls int?] [:user-flair-text-color :nil]
   [:header-size [:maybe [:vector int?]]] [:user-is-subscriber :nil]
   [:active-user-count :nil]
   [:banner-img [:maybe string?]] [:can-assign-link-flair boolean?]
   [:header-img [:maybe string?]] [:free-form-reports boolean?]
   [:mobile-banner-image string?] [:collapse-deleted-comments boolean?]
   [:header-title [:maybe string?]] [:user-flair-richtext [:vector any?]]
   [:submit-link-label [:maybe string?]] [:created-utc double?]
   [:allow-videos boolean?] [:restrict-posting boolean?]
   [:over-18 boolean?] [:is-chat-post-feature-enabled boolean?]
   [:user-flair-background-color :nil]
   [:public-description-html [:maybe string?]]
   [:videostream-links-count {:optional true} int?]
   [:primary-color string?] [:link-flair-position string?]
   [:user-flair-type string?] [:hide-ads boolean?]])

(defn create-response-schema
  [schema]
  [:map
   [:kind string?]
   [:data
    [:map [:after string?] [:dist int?] [:modhash string?] [:geo-filter string?]
     [:children
      [:vector
       [:map [:kind string?]
        [:data schema]]]]
     [:before :nil]]]])

;; ** Internal schema

(def extracted-fields
  [:display-name
   :description
   :created
   :url])

(defn sec->inst
  [sec-num]
  (java.util.Date/from (java.time.Instant/ofEpochSecond sec-num)))

(defn prepend-domain
  [url]
  (str (:host api-configuration) url))

(def extracted-subreddit-schema
  (->
    infered-subreddit-schema
    (mu/select-keys extracted-fields)
    (mu/rename-keys {:created      :created-at,
                     :display-name :name})
    (mu/assoc :created-at inst?)
    (mu/update-properties assoc
                          :decode/string
                          {:enter #(-> %
                                       (set/rename-keys
                                         {:created      :created-at,
                                          :display-name :name})
                                       (update :created-at sec->inst)
                                       (update :url prepend-domain))})))

(def api-configuration
  {:host      "https://www.reddit.com",
   :endpoints
     {:api/get-popular-subreddits
        {:method       :get,
         :path         "/subreddits/popular",
         :malli/schema [:=>
                        [:cat [:map [:limit pos-int?]]]
                        (create-response-schema
                          extracted-subreddit-schema)]}}})
