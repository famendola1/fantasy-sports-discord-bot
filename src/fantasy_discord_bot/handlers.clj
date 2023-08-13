(ns fantasy-discord-bot.handlers
  (:require [clojure.string :as s]
            [discljord.messaging :as m]
            [fantasy-discord-bot.providers.providers :as providers]))

(def command-regex #"^!(?<cmd>\w+) ?(?<args>.*)")
(def allowed-commands #{:standings :scoreboard})

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


(defn- mk-message-create-handler
  "Creates the handler for the message create event."
  [state provider]
  (fn [event-type {{bot :bot} :author :keys [channel-id content]}]
    (when-not bot
      (when-let [cmd (parse-content content)]
        (if (allowed-commands (:cmd cmd))
          (m/create-message! (:messaging state)
                             channel-id
                             :content (render (provider cmd))))))))

(defn init-handlers
  "Initializes the Discord event handlers."
  [state config]
  (let [provider (providers/init-provider config)]
    {:message-create [(mk-message-create-handler state provider)]}))
