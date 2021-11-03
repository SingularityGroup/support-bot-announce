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
      jira]))


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

(defn announce-msg [{:keys [store version]}]
  {:content
   (format
    (str "%s New version %s has been published on the %s"
         "\nIf you cannot see the update please "
         "wait at least 2 hours or try clearing cache of your store app. ")
    (emojis 1)
    version
    (if (= "iOS" store)
      "iOS Appstore :green_apple:"
      "Android Playstore :robot:"))})
(defn
  announce-in-public-threads
  [opts]
  (let [msg (announce-msg opts)]
    (doseq
        [channel
         (get-in
          config
          [:discord
           :announce-threads])]
        (discord/message
         channel
         msg))))

(defn announce-in-thread
  "Put verion fix-version message in `thread`."
  [version thread]
  (println "announce in thread " version thread)
  (:id
   (discord/message
    thread
    {:content
     (format
      "%sThis issue has been fixed on version %s. %s Please update via the store and reply back if you still have problems."
      (emojis 1)
      version
      (emojis (inc (rand-int 2))))})))

(def finish-transition (get-in config [:jira :transition]))

(defn handle-ticket
  [{:keys [key] :as ticket} version]
  (if-let [thread
           (jira/ticket->discord-thread ticket)]
    (if
        (announce-in-thread version thread)
        (do
          (jira/make-comment
           key
           "Messaged user in thread that issue got fixed.")
          (jira/transition
           :transition finish-transition
           ticket))
        (jira/make-comment
         key
         "Messaging in thread didn't work. Try assign me again."))
    (jira/make-comment
     key
     "I tried to parse discord url field but did not succeed.")))

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

(defn
  bot-log-msgs
  []
  (discord/messages
   (get-in
    config
    [:discord :log-channel])))

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
  announce-for-version
  "Get jira tickets and make announcements.
  This also ads comments to jira tickets.
  {:version .. :project ..}
  Has side effects."
  [{:keys [version] :as opts}]
  (when-let
      [tickets (jira/tickets opts)]
      (run!
       #(handle-ticket % version)
       tickets)))

(defn
  announce-for-version-1
  "Like `announce-for-version-1` but parses jira hook data."
  [{{version-name :name
     project-id :projectId} :version}]
  (announce-for-version
   {:project (jira/project-id->key
              project-id)
    :version version-name}))

(defn
  BotAnnounceLambda
  ""
  [{{:keys [headers] :as event} :event}]
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
            (announce-in-public-threads d)
            (announce-in-log-thread d))
        (when
            (announced?
             (select-keys d [:version]))
          (announce-for-version
           {:project (get-in config [:jira :project])
            :version (:version d)}))
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
  [{{{[{fix-version :name}] :fixVersions} :fields :as issue} :issue}]
  (when
      (jira/relevant? issue)
      (handle-ticket issue fix-version)))
;;

(defn
  jira-payload
  [{:keys [event]}]
  (when
      (=
       (get-in
        event
        [:queryStringParameters
         :sgtoken])
       (get-in
        config
        [:jira :hook-token]))
      (let [payload
            (json/read-str
             (:body event)
             :key-fn
             keyword)]
        (prn payload "\n")
        payload)))

(defn
  wrap-jira-parse
  [f]
  (fn
    [data]
    (if-let
        [payload (jira-payload data)]
        (do (f payload) (hr/text "ok"))
        (hr/bad-request
         "Singularity Group token invalid"))))

(def JiraVersionLambda
  (wrap-jira-parse
   announce-for-version-1))

(def JiraStatusLambda
  (wrap-jira-parse
   announce-when-released-and-fixed))

;; end

(h/entrypoint [#'BotAnnounceLambda #'JiraStatusLambda #'JiraVersionLambda])

(agent/in-context)

(comment
  (announced-versions
   (bot-log-msgs))
  (announce-in-thread "1337" "902167426249146398")

  (run!
   #(handle-ticket % "1337")
   (jira/tickets {:project "BEN" :version "1"}))


  (def jira-payload (edn/read-string (slurp "/tmp/spb.edn")))
  (def jira-payload (edn/read-string (slurp "/home/benj/repos/clojure/support-bot/example-jira-input.edn")))
  (def jira-payload (edn/read-string (slurp "/tmp/example-jira-update.edn")))

  (announce-when-released-and-fixed jira-payload)

  (announce-when-released-and-fixed (assoc-in
                                     jira-payload
                                     [:fields :fixVersions]
                                     [{:name "26.0.1"}]))

  (jira/make-comment
   "BEN-3"
   "Messaged user in thread that issue got fixed.")
  (announce-in-public-threads
   {:version "fo" :store "iOS"})
  (announce-to-ticket-creators "1.70"))

(comment
  (def version-event
    (edn/read-string (slurp "/tmp/jira-event.edn")))
  (announce-for-version version-event)

  (keys version-event)

  (:issue_event_type_name version-event)

  (keys (:fields (:issue version-event)))

  (:assignee (:fields (:issue version-event)))

  (def version-event (update-in
                      version-event
                      [:issue :fields :status :name]
                      (constantly "WAITING")))

  (every?
   (:fields (:issue version-event))
   (get-in config [:jira :required-fields]))

  (jira/relevant?
   (:issue version-event))

  (announce-when-released-and-fixed
   version-event))
