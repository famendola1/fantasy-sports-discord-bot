(ns fantasy-discord-bot.providers.yahoo.yquery
  (:require [fantasy-discord-bot.providers.yahoo.query :as q]))

(def YAHOO_FANTASY_API_ENDPOINT
  "https://fantasysports.yahooapis.com/fantasy/v2" )

(defn- mk-league-key
  "Makes a league key from a game code and a league ID."
  [game league-id]  
  (str game ".l." league-id))

(defn get-standings
  "Queries Yahoo's fantasy API for the league's standings."
  [game league-id]
  (fn [auth]
    (q/ask auth (q/query YAHOO_FANTASY_API_ENDPOINT
                         :league
                         (keyword (mk-league-key game league-id))
                         :standings))))

(defn get-scoreboard
  "Queries Yahoo's fantasy sports API for the a league's scoreboard. Optionally
  takes a week parameter to return the scoreboard for that week."
  ([game league-id]
   (get-scoreboard game league-id nil))
  ([game league-id week]
   (fn [auth]
     (q/ask auth (q/query YAHOO_FANTASY_API_ENDPOINT
                          :league
                          (keyword (mk-league-key game league-id))
                          (if-not (nil? week)
                            [:scoreboard {:week week}]
                            :scoreboard))))))
