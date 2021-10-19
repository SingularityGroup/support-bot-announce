(ns
    org.singularity-group.bot-announce.core
    (:gen-class)
    (:require
     [clojure.data.json :as json]
     [clojure.string :as str]
     [fierycod.holy-lambda.response
      :as
      hr]
     [fierycod.holy-lambda.agent
      :as
      agent]
     [fierycod.holy-lambda.core
      :as
      h]
     [org.singularity-group.bot-announce.config
      :refer
      [config]]
     [org.singularity-group.bot-announce.discord
      :as
      discord]
     [org.singularity-group.bot-announce.jira
      :as
      jira]
     [org.singularity-group.bot-announce.util
      :refer
      [when-let*]]))

(set! *warn-on-reflection* true)

(defn
  version*
  [msg]
  (when-let
      [[_ v]
       (re-find
        #"v([\d\.]+) incoming"
        msg)]
      v))

(defn gitlab-version [{:keys [commits]}]
  (some
   #(version* (:message %))
   commits))

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

(def emojis
  (delay
    (->>
     [emoji-gold-chest emoji-happy-girl emoji-star emoji-vote-up]
     cycle
     (random-sample 0.20)
     (take
      (+ 3 (rand-int 2))))))

(defn announce-in-thread
  "Put verion fix message in `thread`."
  [version thread]
  (discord/message
   thread
   {:content
    (format
     "%sThis issue has been fixed on version %s. %s Please update via the store and reply back if you still have problems."
     (str/join @emojis)
     version
     (str/join @emojis))}))

(defn announce-to-ticket-creators
  ([data]
   (let [version (gitlab-version data)
         project (get-in config [:jira :project])]
     (announce-to-ticket-creators
      version project)))
  ([version project]
   (run! #(announce-in-thread version %)
         (jira/discord-threads version project))))

(defn
  BotAnnounceLambda
  ""
  [{{:keys [headers] :as event} :event :keys [ctx] :as request}]
  (if
      (and
       headers
       (or
        (=
         (headers "x-gitlab-token")
         (get-in
          config
          [:gitlab :webhook-token]))
        (=
         (headers "x-support-bot-admin-token")
         (get-in
          config
          [:gitlab :x-support-bot-admin-token]))))
      (let [event
            (json/read-str
             (:body event)
             :key-fn keyword)]
        (announce-to-ticket-creators event)
        (hr/text "Success."))
      (hr/not-found "Token invalid")))

;; Specify the Lambda's entry point as a static main function when generating a class file

(h/entrypoint [#'BotAnnounceLambda])
;; Executes the body in a safe agent context for native configuration generation.
;; Useful when it's hard for agent payloads to cover all logic branches.

(agent/in-context)

(comment
  (def dd (clojure.edn/read-string (slurp "/tmp/example.edn")))

  (announce-in-thread "897475380464730184")

  (let [project "BEN" version "26.0.0"]
    (announce-to-ticket-creators version project))

  (announce-to-ticket-creators
   {:commits
    [{:message "v26.0.0 incoming"}]})

  (def data
    (clojure.edn/read-string
     (slurp "/tmp/example-gitlab.edn")))

  (let [data
        (select-keys data [:commits])]
    data
    ;; (gitlab-version data)
    )

  (print
   (json/json-str
    {:commits
     [{:message "v26.0.0 incoming"}]}))
  )
