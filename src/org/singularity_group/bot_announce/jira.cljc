(ns
    org.singularity-group.bot-announce.jira
    (:require
     [fierycod.holy-lambda.agent
      :as
      agent]
     [org.httpkit.client :as client]
     [clojure.data.json :as json]
     [org.singularity-group.bot-announce.config
      :refer
      [config]]))

(set! *warn-on-reflection* true)

(defn req [url]
  (let [config (:jira config)]
    {:url (str (:url config) url)
     :headers {"Content-Type" "application/json"}
     :basic-auth [(:username config) (:token config)]}))

(defn jql-query [jql]
  (->
   @(let [opts
          (assoc
           (req "/rest/api/2/search")
           :method :post
           :body
           (json/write-str
            {:jql jql}))]
      (client/request opts))
   :body
   (json/read-str :key-fn keyword)))

(defn- discord-threads*
  [project version]
  (:issues
   (jql-query
    (format "project = %s and \"Discord[URL Field]\" is not EMPTY and fixVersion >= %s order by created DESC"
            project
            version))))

(defn- tickets-discord-threads [issues]
  (for
      [{{discord-link :customfield_10046} :fields}
       issues
       :when discord-link
       :let [[_ thread]
             (re-find
              #"^discord://discord.com/channels/\d+/(\d+)"
              discord-link)]
       :when thread]
    thread))

(defn
  discord-threads
  [project version]
  (tickets-discord-threads
   (discord-threads*
    version
    project)))

(agent/in-context
 (try (client/request {}) (catch Exception _)))

;; (comment
;;   (def issues
;;     (let [
;;           project "BEN"
;;           ;; project "COS"
;;           version "1.70"
;;           ;; version "40.0.0"
;;           ]
;;       (discord-threads* project version)))

;;   (tickets-discord-threads issues)

;;   )
