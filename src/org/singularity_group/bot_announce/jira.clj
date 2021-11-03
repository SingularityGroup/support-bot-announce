(ns
    org.singularity-group.bot-announce.jira
    (:require
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

(defn read-resp [{:keys [body]}]
  (json/read-str body :key-fn keyword))

(def project-id->key
  (get-in config [:jira :project-id->key]))

(defn jql-query [body & {:as opts}]
  (->
   @(client/request
     (merge
      opts
      (req "/rest/api/2/search")
      {:method :post
       :body
       (json/write-str body)}))
   :body
   (json/read-str :key-fn keyword)))

(defn
  relevant-issues-jql
  [project version]
  (str
   "project = "
   project
   " and assignee = currentUser()"
   " and status = WAITING"
   " and \"Discord[URL Field]\" is not EMPTY"
   " and fixVersion <= " version
   " order by created DESC"))

(defn
  tickets
  [{:keys [project version res total cnt]
    :or {res [] cnt 0}
    :as opts}]
  (if (and
       total
       (>= (count res) total))
    res
    (if (> cnt 5)
      (throw (Exception. "looped to much"))
      (let
          [{:keys [total issues]}
           (jql-query
            {:jql
             (relevant-issues-jql project version)
             :startAt (count res)})]
          (recur
           (merge
            opts
            {:res (concat res issues)
             :total total
             :cnt (inc cnt)}))))))

(defn
  ticket->discord-field
  [{{discord-link :customfield_10046} :fields}]
  discord-link)

(defn ticket->discord-thread
  [{{discord-link :customfield_10046} :fields}]
  (when-let
      [[_ thread]
       (and
        discord-link
        (re-find
         #"^discord://discord.com/channels/\d+/(\d+)"
         discord-link))]
      thread))

(defn transition*
  [& {:keys [transition] issue-key :key}]
  (merge
   (req (str "/rest/api/2/issue/" issue-key "/transitions"))
   {:method :post
    :body (json/write-str {:transition transition})}))

(defn transition
  [& {:as opts}]
  @(client/request (transition* opts)))

(defn make-comment [ticket-key content]
  @(client/request
    (assoc
     (req (str "/rest/api/2/issue/" ticket-key "/comment"))
     :body (json/json-str {:body  content})
     :method :post)))

(defn
  versions*
  []
  (read-resp
   @(client/request
     (req
      (format
       "/rest/api/latest/project/%s/versions"
       (get-in
        config
        [:jira :project]))))))

(def versions (memoize versions*))

(defn released? [version]
  ((into
    #{}
    (comp
     (filter :released)
     (keep :name))
    (versions))
   version))

(defn
  relevant?
  [{{:keys
     [assignee status fixVersions]
     :as fields} :fields
    :as issue}]
  (and
   (every?
    fields
    (get-in
     config
     [:jira :required-fields]))
   (ticket->discord-field issue)
   (=
    (:accountId assignee)
    (get-in
     config
     [:jira :my-account-id]))
   (= (:name status) "WAITING")
   (some released? (map :name fixVersions))))

(comment

  (count (tickets {:project 1 :version "COS"}))
  (count (tickets {:project "BEN" :version "1"}))

  (def ticket (first (tickets {:project "BEN" :version "1"})))

  (keys ticket)


  (def trnss @(client/request (req "/rest/api/2/issue/BEN-3/transitions")))
  (json/read-str  (:body trnss) :key-fn keyword)

  (transition*
   {:key "BEN-3"
    :transition (get-in config [:jira :transition])})

  (transition
   {:key "BEN-3"
    :transition (get-in config [:jira :transition])})

  (released? "1.78")

  @(client/request (make-comment "BEN-3" "yea"))

  (def projects
    (->
     @(client/request (req "/rest/api/2/project"))
     :body
     (json/read-str :key-fn keyword)))
  (into {} (map (juxt :id :key)) projects)



  (tickets-discord-threads issues))
