#!/usr/bin/env bb

(require '[babashka.curl :as curl])
(require '[clojure.java.io :as io])
(require '[cheshire.core :as json])
(require '[clojure.edn :as edn])
(require '[org.httpkit.client :as client])

(def config
  (merge-with merge
              (edn/read-string
               (slurp
                (io/resource "tokens.edn")))
              (edn/read-string
               (slurp (io/resource "config.edn")))))

(defn
  make-version
  [store version all?]
  #:cos-version{:release {:store store
                          :version version
                          :all-released? all?}})

(defn trigger-announce-hook [store version all?]
  (client/post
   (str
    "https://f2eg7wtbx6.execute-api.us-east-1.amazonaws.com/prod/slack-announce"
    (get-in config [:jira :status-webhook-url]))
   {:headers {"x-suppoRt-bot-admin-token" (get-in config [:support-bot :token])}
    :body
    (prn-str
     (make-version store version all?))}))


@(trigger-announce-hook  "Android" "26.0.1" true)
