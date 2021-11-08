(ns
    org.singularity-group.bot-announce.discord
    (:require
     [clojure.string :as str]
     [clojure.set :as set]
     [clojure.data.json :as json]
     [org.httpkit.client :as client]
     [org.singularity-group.bot-announce.config
      :refer
      [config]]))

(set! *warn-on-reflection* true)

;; thanks https://github.com/IGJoshua/discljord

(defn- extract-id [entity]
  (cond-> entity (map? entity) :id))

(def user-mention
  "Regex pattern that matches user or member mentions.

  Captures the user id in its first capture group labelled \"id\"."
  #"<@!?(?<id>\d+)>")

(defn mention-user
  "Takes a user object or id and returns a mention of that user for use in messages."
  [user]
  (str "<@" (extract-id user) \>))

(defn mention-emoji
  "Takes an emoji object or a custom emoji id and returns a mention of that emoji for use in messages.

  A provided emoji object may also represent a regular unicode emoji with just a name,
  in which case that name will be returned."
  [emoji]
  (if (map? emoji)
    (let [{:keys [animated name id]} emoji]
      (if id
        (str \< (if animated \a "") \: name \: id \>)
        name))
    (str "<:_:" emoji \>)))

;; disljord end

(def emoji-happy-girl
  (mention-emoji
   {:name "VoHiYo", :id "586548388829593611"}))

(def emoji-gold-chest
  (mention-emoji
   {:name "DankLoot", :id "586215241046556672"}))

(def emoji-star
  (mention-emoji
   {:name "OneStar", :id "585532547522625546"}))

(def emoji-vote-up
  (mention-emoji
   {:name "VoteUp" , :id "585532001646280734"}))

(defn emojis [n]
  (->>
   [emoji-gold-chest emoji-happy-girl emoji-star emoji-vote-up]
   cycle
   (random-sample 0.20)
   (take n)
   str/join))

(defn
  rest-api
  [point & {:as opts}]
  (try
    (let [opts (assoc
                opts
                :headers
                {"Content-Type" "application/json"
                 "Authorization" (str
                                  "Bot "
                                  (:discord-bot-token config))}
                :url
                (str
                 "https://discord.com/api"
                 point))]
      (->
       @(client/request opts)
       :body
       (json/read-str :key-fn keyword)))
    (catch Exception e
      (println (.getMessage e)))))

(defn channel [id]
  (rest-api
   (str "/channels/" id)))

(defn user [id]
  (rest-api
   (str "/users/" id)))

(defn
  roles
  [guild-id]
  (rest-api
   (str
    "/guilds/"
    guild-id
    "/roles")))

(defn
  members
  [channel-id]
  (rest-api
   (str
    "/channels/"
    channel-id
    "/thread-members")))

(defn create-dm [user-id]
  (rest-api
   "/users/@me/channels"
   :method :post
   :body (json/write-str
          {:recipient_id user-id})))

(defn
  my-guilds
  []
  (rest-api
   "/users/@me/guilds"))

(defn
  active-threads
  "Active threads for guild."
  [{:keys [id]}]
  (:threads
   (rest-api
    (format
     "/guilds/%s/threads/active"
     id))))

(defn some-thread
  "Get some active thread with `pred`."
  [pred]
  (some
   pred
   (mapcat
    active-threads
    (my-guilds))))

(defn some-thread-re
  [re-str]
  "First active thread matching `re-str`."
  (some-thread
   (fn
     [{:keys [name] :as thread}]
     (and
      (re-find (re-pattern re-str) name)
      thread))))

(defn message
  [channel-id & {:keys [content] :as opts}]
  (rest-api
   (format
    "/channels/%s/messages"
    channel-id)
   :method :post
   :body (json/json-str opts)))

(defn messages [channel-id]
  (rest-api
   (format
    "/channels/%s/messages"
    channel-id)))

(defn bot-msg [{:keys [content] {id :id} :author}]
  (when
   (= id (get-in config [:discord :bot-id]))
   content))


(defn fix-msgs [msgs]
  (into
   []
   (comp
    (keep
     bot-msg)
    (filter
     (partial
      re-find
      #"This issue has been fixed on version")))
   msgs))

(defn thread-served?
  "Return true when our bot already put a fix message in `thread`."
  [thread]
  (seq
   (fix-msgs
    (messages thread))))


(defn non-crew?*
  [thread-members]
  (seq
   (set/difference
    (into
     #{}
     (keep :user_id)
     thread-members)
    (into
     #{}
     (map
      (:discord config)
      [:lenny-id :bot-id :cassy-id])))))

(defn
  non-crew?
  [thread]
  (non-crew?* (members thread)))

(defn
  ^{:side-effects :discord-api}
  message-for-announce
  "Return the message string to announce."
  [thread version]
  (if
      (non-crew? thread)
      (format
       "%sThis issue has been fixed on version %s. %s Please update via the store and reply back if you still have problems."
       (emojis 1)
       version
       (emojis (inc (rand-int 2))))
      (str
       (mention-user (get-in config [:discord :lenny-id]))
       " "
       "This issue has been fixed on version "
       "`" version "`."
       " Please notify user via in-game whisper.")))

(comment

  (let [dm (:id (create-dm "240081690058424320"))]
    (message
     dm
     :content "yea bois :robot:"))

  (def channel-id "902167426249146398")
  (user "888816549689966672")
  (user (get-in config [:discord :cassy-id]))
  (def archived "906901968616820786")
  (def thread (channel archived))
  (def thread-members (members archived))
  (messages "898962261647953950")
  (def msgs (messages channel-id))
  (bot-msg (first msgs))
  (def dms (rest-api "/users/@me/channels"))

  (message archived :content "supp")

  (def guilds (my-guilds))
  (roles "452486349862469652")
  (map
   :user_name
   (map
    user
    (map :user_id (members channel-id))))

  (def guild
    (first
     (filter
      #(#{"singularity-bot-test-server"} (:name %))
      guilds)))

  (non-crew? channel-id)
  (seq (set/difference #{} #{}))

  (active-threads
   guild)

  (non-crew?
   thread-members)

  (message-for-announce archived "fo")
  (message-for-announce channel-id "fo"))
