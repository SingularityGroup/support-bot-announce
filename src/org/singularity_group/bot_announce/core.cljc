(ns org.singularity-group.bot-announce.core
  (:gen-class)
  (:require
   [clojure.data.json :as json]
   [fierycod.holy-lambda.response :as hr]
   [fierycod.holy-lambda.agent :as agent]
   [fierycod.holy-lambda.core :as h]
   [org.singularity-group.bot-announce.discord :as discord]
   [org.singularity-group.bot-announce.util :refer [when-let*]]))

(set! *warn-on-reflection* true)

(defn
  BotAnnounceLambda
  ""
  [{:keys [event ctx queryStringParameters] :as request}]
  (println "string params: " queryStringParameters)

  ;; todo
  #_(when (= "token" (:token queryStringParameters)))

  (hr/text event)

  ;; (let [event
  ;;       (json/read-str
  ;;        (:body event)
  ;;        :key-fn
  ;;        keyword)]
  ;;   (when
  ;;       (=
  ;;        "jira:issue_updated"
  ;;        (:webhookEvent event))
  ;;       (status-update-msg (ddb/connect-db) event))
  ;;   (hr/text "ok"))

  )

;; Specify the Lambda's entry point as a static main function when generating a class file

(h/entrypoint [#'BotAnnounceLambda])
;; Executes the body in a safe agent context for native configuration generation.
;; Useful when it's hard for agent payloads to cover all logic branches.

(agent/in-context
 (println "I will help in generation of native-configurations"))

(comment
  (def dd (clojure.edn/read-string (slurp "/tmp/example.edn")))
  (status-update-msg (update-in dd [:issue :key] (constantly "AUT-38"))))
