(ns fantasy-discord-bot.providers.providers
  (:require [fantasy-discord-bot.providers.yahoo.yahoo :as yahoo]))

(defmulti init-provider
  "Initializes the fantasy sports provider according to the config."
  :type)

(defmethod init-provider :yahoo
  [config]
  (yahoo/init-provider config))
