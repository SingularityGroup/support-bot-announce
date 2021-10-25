(ns
    org.singularity-group.bot-announce.discord
    (:require
     [org.httpkit.client :as client]
     [clojure.data.json :as json]
     [org.singularity-group.bot-announce.config
      :refer
      [config]]))


(set! *warn-on-reflection* true)

(defn
  rest-api
  [point & {:as opts}]
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
     (json/read-str :key-fn keyword))))

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
  (and
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

(defn fix-msg?
  "Return true when our bot already put a fix message in `thread`."
  [thread]
  (some?
   (fix-msgs
    (messages thread))))

(set! *warn-on-reflection* true)

;; thanks https://github.com/IGJoshua/discljord
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

(comment
  (let [dm (:id (create-dm "240081690058424320"))]
    (message
     dm
     :content "yea bois"))

  (def channel-id "902167426249146398")


  (messages "898962261647953950")

  (def msgs (messages channel-id))

  (bot-msg (first msgs))


  )
