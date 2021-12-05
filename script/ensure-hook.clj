#!/usr/bin/env bb

(require '[org.httpkit.client :as client])

(def config
  (merge-with merge
              (edn/read-string
               (slurp
                (io/resource "tokens.edn")))))

(defn req [url]
  (let [config (:jira config)]
    {:url (str (:url config) url)
     :headers {"Content-Type" "application/json"}
     :basic-auth [(:username config) (:token config)]}))

(defn make-hook
  [opts]
  (let [hook
        (assoc
         opts
         :name "support-bot-status"
         :events ["jira:issue_updated"]
         :excludeBody false)]
    @(-> (req
          "/rest/webhooks/1.0/webhook")
         (assoc :method :post :body (json/encode hook))
         client/request)))

(make-hook
 {:url
  (str
   (get-in config [:lambda-url])
   "jira-status"
   "?"
   "sgtoken="
   (get-in
    config
    [:jira :hook-token]))
  :filters
  {:issue-related-events-section
   (str
    "project = " (get-in config [:jira :project])
    ;; " and resolution is not EMPTY"
    ;; " and assignee = currentUser()"
    ;; " and status = WAITING"
    ;; " and \"Discord[URL Field]\" is not EMPTY"
    )}})


(comment

  )
