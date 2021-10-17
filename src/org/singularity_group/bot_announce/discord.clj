(ns
    org.singularity-group.bot-announce.discord
    (:require
     [org.httpkit.client :as client]
     [clojure.data.json :as json]
     [org.singularity-group.bot-announce.config
      :refer
      [config]]))

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

(comment
  (let [dm (:id (create-dm "240081690058424320"))]
    (message
     dm
     :content "yea bois"))
  (let [user-id
        "240081690058424320"
        ticket-key
        "AUT-38"
        dm
        (:id (create-dm user-id))]
    (message
     dm
     :content
     (str
      (format "Your ticket [%s] was moved to done! " ticket-key)
      "The fix will be included in the upcomming release versions. "
      "Let us know when you still experience the issue."))))
