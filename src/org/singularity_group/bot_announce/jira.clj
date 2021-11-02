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
  tickets
  ([project version]
   (tickets project version nil nil))
  ;; ([project
  ;;   version
  ;;   {:maxResults :total
  ;;    :startAt :issues}
  ;;   acc]
  ;;  (let [issues (concat issues acc)]
  ;;    (if
  ;;        (and
  ;;         total
  ;;         (>= (count issues) total))
  ;;      issues
  ;;      (recur
  ;;       project
  ;;       version
  ;;       (tickets
  ;;        (jql-query
  ;;         (str
  ;;          "project = "
  ;;          project
  ;;          " and \"Discord[URL Field]\" is not EMPTY and assignee = currentUser()"
  ;;          " and fixVersion <= "
  ;;          version
  ;;          " order by created DESC")))
  ;;       issues))))
  )

;; (concat nil nil)

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
             (str
              "project = " project
              " and assignee = currentUser()"
              " and status = WAITING"
              " and \"Discord[URL Field]\" is not EMPTY"
              ;; " and fixVersion <= " version
              " order by created DESC")
             :startAt (count res)})]
        (recur
         (merge
          opts
          {:res (concat res issues)
           :total total
           :cnt (inc cnt)}))))))

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

;; (defn
;;   discord-threads
;;   [project version]
;;   (tickets-discord-threads
;;    (tickets
;;     version
;;     project)))


(defn ticket-transition*
  [& {:keys [:key transition]}]
  (merge
   (req (str "/rest/api/2/issue/" key "/transitions"))
   {:method :post
    :body (json/write-str {:transition transition})}))

(defn ticket-transition
  [& {:as opts}]
  @(client/request (ticket-transition* opts)))

(defn make-comment [ticket-key content]
  @(client/request
    (assoc
     (req (str "/rest/api/2/issue/" ticket-key "/comment"))
     :body (json/json-str {:body  content})
     :method :post)))

(comment

  (count (tickets {:project 1 :version "COS"}))
  (count (tickets {:project "BEN" :version "1"}))

  (def ticket (first (tickets {:project "BEN" :version "1"})))

  (keys ticket)


  (def trnss @(client/request (req "/rest/api/2/issue/BEN-3/transitions")))
  (json/read-str  (:body trnss) :key-fn keyword)

  (ticket-transition*
   {:key "BEN-3"
    :transition (get-in config [:jira :transition])})

  (ticket-transition
   {:key "BEN-3"
    :transition (get-in config [:jira :transition])})



  @(client/request (make-comment "BEN-3" "yea"))




  (:startAt
   (->
    (:body
     @(client/request
       (let [project "COS"]
         (jql-query
          (str
           "project = " project
           " and \"Discord[URL Field]\" is not EMPTY"
           " order by created DESC")
          :query-params {:startAt 2}))))
    (json/read-str :key-fn keyword)))

  (tickets-discord-threads issues))
