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
         :name "support-bot-version-release"
         :events ["jira:version_released"]
         :excludeBody false)]
    @(-> (req
          "/rest/webhooks/1.0/webhook")
         (assoc :method :post :body (json/encode hook))
         client/request)))

(make-hook
 {:url
  "https://big-impala-38.loca.lt"
  ;; (str
  ;;  (get-in config [:lambda-url])
  ;;  "?"
  ;;  "sgtoken="
  ;;  (get-in
  ;;   config
  ;;   [:jira :hook-token]))
  })

(comment (ticket "BEN-2"))

(comment
  (defn
    ticket
    "Fetch ticket with jira api."
    [id]
    (let [opts (req
                (str "/rest/api/3/issue/" id))
          ticket @(client/request opts)]
      (json/decode
       (:body ticket)
       keyword))))


(comment

  )
