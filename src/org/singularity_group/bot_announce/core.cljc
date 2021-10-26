(ns
    org.singularity-group.bot-announce.core
    (:gen-class)
    (:require
     [clojure.edn :as edn]
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

(defn emojis [n]
  (->>
   [emoji-gold-chest emoji-happy-girl emoji-star emoji-vote-up]
   cycle
   (random-sample 0.20)
   (take n)
   str/join))

(defn
  announce-in-public-thread
  [{:keys [store version]}]
  (let
      [channel
       (get-in
        config
        [:discord
         :announce-thread
         :prod])]
      (discord/message
       channel
       {:content
        (format
         (str "%s New version %s has been published on the %s%s "
              "\nIf you cannot see the update please "
              "wait at least 2 hours or try clearing cache of your store app. ")
         (emojis 1)
         version
         (case store
           "iOS" "iOS Appstore"
           "Android" "Android Playstore"
           store)
         (emojis (inc (rand-int 2))))})))

(defn announce-in-thread
  "Put verion fix-version message in `thread`."
  [version thread]
  (println "announce in thread " version thread)
  (discord/message
   thread
   {:content
    (format
     "%sThis issue has been fixed on version %s. %s Please update via the store and reply back if you still have problems."
         (emojis 1)
     version
     (emojis (inc (rand-int 2))))}))

(defn
  announce-to-ticket-creators
  [version]
  (let [project (get-in
                 config
                 [:jira :project])]
    (run!
     #(announce-in-thread version %)
     (jira/discord-threads
      version
      project))))

(defn
  announce-in-log-thread
  [version]
  (discord/message
   (get-in config [:discord :log-channel])
   {:content
    (format
     "Announced on all stores: %s"
     version)}))

(defn bot-log-msgs []
    (discord/messages
      (get-in config [:discord :log-channel])))

(defn parse-version [msg]
  (when-let
      [[_ version]
       (re-find
        #"Announced on all stores: ([\d\.]+)"
        msg)]
      version))

(defn announced-versions [msgs]
    (into
     #{}
     (comp
      (keep :content)
      (keep parse-version))
     msgs))

(defn
  announced?
  [version]
  ((announced-versions
    (bot-log-msgs))
   version))

(defn
  BotAnnounceLambda
  ""
  [{{:keys [headers] :as event} :event :keys [ctx] :as request}]
  (if
      (and
       headers
       (=
        (headers "x-support-bot-admin-token")
        (get-in
         config
         [:support-bot :token])))
      (let
          [payload
           (edn/read-string
            (:body event))
           d (:cos-version/release payload)]
          (announce-in-public-thread d)
          (when (:all-released? d)
            (announce-to-ticket-creators
             (:version d)))
          (hr/text "Success."))
      (hr/not-found "Token invalid")))


;; jira status --

(defn thread-served?
  "Return true when our bot already put a fix message in `thread`."
  [thread]
  (seq
   (discord/fix-msgs
    (discord/messages thread))))

(defn parse-discord [field]
  (and field
       (when-let
           [[_ thread]
            (re-find
             #"^discord://discord.com/channels/\d+/(\d+)"
             field)]
           thread)))

(defn
  announce-when-released-and-fixed
  [{{{discord (get-in
               config
               [:jira :discord-field])
      [{fix-version :name}] :fixVersions} :fields} :issue}]
  (when-let [thread (parse-discord discord)]
    (println "thread: " thread "fix version: "  fix-version )
      (and
       fix-version
       (doto (not (thread-served? thread)) println)
       (doto (announced? fix-version) println))
      (announce-in-thread fix-version thread)))

(defn
  JiraStatusLambda
  ""
  [{:keys [event
           ctx
           queryStringParameters]
    :as request}]
  (prn request)
  (if
      (=
       (:sgtoken
        queryStringParameters)
       (get-in
        config
        [:jira :hook-token]))
      (let [payload (json/read-str
                     (:body event)
                     :key-fn
                     keyword)]
        (prn payload "\n")
        (when
            (=
             "jira:issue_updated"
             (doto
                 (:webhookEvent payload)
                 (println " - webhook event")))
            (announce-when-released-and-fixed
             payload))
        (hr/text "ok"))
      (hr/bad-request
       "Singularity Group token invalid")))

;; end

(h/entrypoint [#'BotAnnounceLambda #'JiraStatusLambda])

(agent/in-context)

(comment
  (announced-versions
   (bot-log-msgs))

  (announced? "1337")

  (announce-in-log-thread "1337")
  (announce-in-log-thread "26.0.1")

  (announce-in-thread "1337" "902167426249146398")

  (def jira-payload (edn/read-string (slurp "/tmp/spb.edn")))
  (def jira-payload (edn/read-string (slurp "/home/benj/repos/clojure/support-bot/example-jira-input.edn")))
  (def jira-payload (edn/read-string (slurp "/tmp/example-jira-update.edn")))

(announce-when-released-and-fixed jira-payload)

  (announce-when-released-and-fixed (assoc-in
                                     jira-payload
                                     [:fields :fixVersions]
                                     [{:name "26.0.1"}]))


  (let [d (:cos-version/release payload)
        d (assoc d :version "1.70")]
    (announce-in-public-thread d)
    (when (:all-released? d)
      (announce-to-ticket-creators
       (:version d))))

  (announce-in-public-thread
   (:cos-version/release payload))

  (announce-in-public-thread
   {:version "fo" :store "Appstore"})

  (announce-to-ticket-creators "26.0.0")

  (let [project "BEN" version "26.0.0"]
    (announce-to-ticket-creators version project)))
