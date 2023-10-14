(ns fantasy-discord-bot.handlers
  (:require [clojure.string :as s]
            [discljord.messaging :as m]
            [fantasy-discord-bot.providers.providers :as providers]))

(def command-regex #"^!(?<cmd>\w+)\s*(?<args>.*)")
(def allowed-commands #{:help :standings :scoreboard :schedule :vs :roster})
(def help-message
  "```
Fantasy Sports Discord Bot
--------------------------

!help
Returns this message.

!roster <team>
Returns the roster of the given team.

!schedule <team>
Returns season schedule of the provided team.

!scoreboard <week>
Returns the scoreboard of the given week. If no week is provided, returns the current scoreboard.

!standings
Returns the current league standings.

!vs [week] <team>
Returns the matchups results of the provided team against all other teams in the league. If week is not provided, the current week is used.
```")

(defn- render
  "Renders the given content as a string."
  [content]
  (str "```\n"
       (:header content)
       "\n"
       (apply str (repeat (count (:header content)) "-"))
       "\n\n"
       (:body content)
       "\n```"))

(defn- parse-message
  "Parses the message for a bot command."
  [message]
  (when (s/starts-with? message "!")
    (let [matcher (re-matcher command-regex message)]
      (if (.matches matcher)
        {:cmd (keyword (.group matcher "cmd"))
         :args (.group matcher "args")}))))

(defn create-help-message
  [state channel-id]
  (m/create-message! (:messaging state)
                     channel-id
                     :content help-message))

(defn- mk-message-create-handler
  "Creates the handler for the message create event."
  [state provider]
  (fn [event-type {{bot :bot} :author :keys [channel-id content]}]
    (when-not bot
      (when-let [cmd (parse-message content)]
        (when (allowed-commands (:cmd cmd))
          (if (= :help (:cmd cmd))
            (create-help-message state channel-id)
            (m/create-message! (:messaging state)
                               channel-id
                               :content (render (provider cmd)))))))))

(defn init-handlers
  "Initializes the Discord event handlers."
  [state config]
  (let [provider (providers/init-provider config)]
    {:message-create [(mk-message-create-handler state provider)]}))
