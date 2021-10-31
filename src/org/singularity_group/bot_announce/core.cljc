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
    (let [ios? (= "iOS" store)]
      (discord/message
       channel
       {:content
        (format
         (str "%s New version %s has been published on the %s"
              "\nIf you cannot see the update please "
              "wait at least 2 hours or try clearing cache of your store app. ")
         (emojis 1)
         version
         (if ios?
           "iOS Appstore :green_apple:"
           "Android Playstore :robot:"))}))))
(defn
  announce-in-log-thread
  [{:keys [store version]}]
  (let
      [channel
       (get-in
        config
        [:discord
         :announce-thread
         :prod])]
    (let [ios? (= "iOS" store)]
      (discord/message
       channel
       {:content
        (format
         (str "%s New version %s has been published on the %s"
              "\nIf you cannot see the update please "
              "wait at least 2 hours or try clearing cache of your store app. ")
         (emojis 1)
         version
         (if ios?
           "iOS Appstore :green_apple:"
           "Android Playstore :robot:"))}))))

(defn announce-in-thread-with-served-check!
  "Put verion fix-version message in `thread`."
  [version thread]
  (println "announce in thread " version thread)
  (when (not (discord/thread-served? thread))
    (discord/message
     thread
     {:content
      (format
       "%sThis issue has been fixed on version %s. %s Please update via the store and reply back if you still have problems."
       (emojis 1)
       version
       (emojis (inc (rand-int 2))))})))

(defn
  announce-to-ticket-creators
  [{:keys [version]}]
  (let [project (get-in
                 config
                 [:jira :project])]
    (run!
     #(announce-in-thread-with-served-check! version %)
     (jira/discord-threads
      version
      project))))

(defn
  announce-in-log-thread
  [{:keys [version store]}]
  (discord/message
   (get-in config [:discord :log-channel])
   {:content
    (format
     "Announced: %s %s"
     store
     version)}))

(defn bot-log-msgs []
    (discord/messages
      (get-in config [:discord :log-channel])))

(defn parse-version [msg]
  (when-let
      [[_ store version]
       (re-find
        #"Announced: (\w+) ([\d\.]+)"
        msg)]
    [store version]))

(defn announced-versions [msgs]
  (into
   #{}
   (comp
    (keep :content)
    (keep parse-version))
   msgs))

(defn
  announced?
  [{:keys [version store]}]
  (if
      store
      ((announced-versions
        (bot-log-msgs))
       [store version])
      (>
       (count
        ((group-by
          second
          (announced-versions
           (bot-log-msgs)))
         version))
       1)))

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
      (let [payload (edn/read-string (:body event))
            d (doto
                  (:cos-version/release payload)
                  prn)]
        (when-not
            (announced? d)
            (announce-in-public-thread d)
            (announce-in-log-thread d))
        (when
            (announced?
             (select-keys d [:version]))
            (announce-to-ticket-creators d))
        (hr/text "Success."))
      (hr/not-found "Token invalid")))

;; jira status --


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
    (println "thread: " thread "fix version: "  fix-version)
    (when
        (and
         fix-version
         (announced? {:version fix-version}))
        (announce-in-thread-with-served-check! fix-version thread))))

(defn
  JiraStatusLambda
  ""
  [{:keys [event ctx]
     :as request}]
  (if
      (=
       (get-in event [:queryStringParameters :sgtoken])
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


  (announce-in-thread-with-served-check! "1337" "902167426249146398")

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


  (thread-served? "904070992249384990")


  (:all-released? (:cos-version/release jira-payload))

  (announce-in-public-thread
   {:version "fo" :store "Appstore"})

  (announce-in-public-thread
   {:version "fo" :store "Andrid"})
  (announce-in-public-thread
   {:version "fo" :store "iOS"})

  (announce-to-ticket-creators "26.0.0")
  (announce-to-ticket-creators "1.70")

  (let [project "BEN" version "26.0.0"]
    (announce-to-ticket-creators version project))


  (def ben-9-thread "904060721682325545")
  (announce-in-thread-with-served-check! "1.1" ben-9-thread)
  (thread-served? ben-9-thread)

  (not (thread-served? ben-9-thread))

  (announce-in-log-thread
   "iOS"
   "1.70")

  (announce-in-log-thread
   {:version "1.70"
    :store "Android"})

  (announced?
   {:version "1.70"
    :store "Android"})

  (announced? {:version "1.1" :store "Android"})

  (let [version "1.70"]
    ((announced-versions (bot-log-msgs)) version)
    ;; (>
    ;;  (count ((announced-versions (bot-log-msgs)) version))
    ;;  1)
    )



  (def req
    {:event {:path "/jira-status", :queryStringParameters {:sgtoken "fo"}}})

  (let [{:keys [event ctx] :as request} req]
    event
    (get-in event [:queryStringParameters :sgtoken])))
