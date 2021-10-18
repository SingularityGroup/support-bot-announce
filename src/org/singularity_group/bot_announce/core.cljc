(ns org.singularity-group.bot-announce.core
  (:gen-class)
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [fierycod.holy-lambda.response :as hr]
   [fierycod.holy-lambda.agent :as agent]
   [fierycod.holy-lambda.core :as h]
   [org.singularity-group.bot-announce.discord :as discord]
   [org.singularity-group.bot-announce.util :refer [when-let*]]))

(set! *warn-on-reflection* true)

(def emoji-happy-girl
  (discord/mention-emoji
   {:name "VoHiYo", :id "586548388829593611"}))

(def emoji-gold-chest
  (discord/mention-emoji
   {:name "DankLoot", :id "586215241046556672"}))

(def emoji-star
  (discord/mention-emoji
   {:name "OneStar", :id "585532547522625546"}))

(def emoji-vote-up
  (discord/mention-emoji
   {:name "VoteUp" , :id "585532001646280734"}))

(defn emojis*
  "Get `n` random emojis."
  [n]
  (->>
   [emoji-happy-girl emoji-gold-chest emoji-star emoji-vote-up]
   cycle
   (random-sample 0.20)
   (take n)))

(defn emojis
  "Get `n` random emojis."
  [n]
  (str/join  (emojis* n)))

(defn announce-in-thread
  "Put verion fix message in `thread`."
  [thread]
  (discord/message
   thread
   {:content
    (format
     "%sThis issue has been fixed on version %s. %s Please update via the store and reply back if you still have problems."
     (emojis 4)
     "x.x"
     (emojis 3))}))


(defn announce-to-ticket-creators* [version project]

  )

(defn announce-to-ticket-creators [gitlab-data]
  ;; get version
  ;; tickets from jira
  ;; discord threads from tickets
  ;; make messages
  )




(defn
  BotAnnounceLambda
  ""
  [{:keys [event ctx queryStringParameters] :as request}]
  (println "string params: " queryStringParameters)

  ;; todo
  #_(when (= "token" (:token queryStringParameters)))


  (hr/text (with-out-str (prn request)))

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

  (announce-in-thread "897475380464730184")

  )
