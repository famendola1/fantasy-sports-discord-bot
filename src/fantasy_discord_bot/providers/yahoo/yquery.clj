(ns fantasy-discord-bot.providers.yahoo.yquery
  (:require [clojure.string :as s]
            [fantasy-discord-bot.providers.yahoo.query :as q]))

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

(defn get-teams-matchups
  "Queries Yahoo's fantasy API for the every teams' matchups for the season."
  [game league-id]
  (fn [auth]
    (q/ask auth (q/query YAHOO_FANTASY_API_ENDPOINT
                         :league
                         (keyword (mk-league-key game league-id))
                         :teams
                         :matchups))))

(defn- stat-type->request-param
  [type]
  (if (s/starts-with? type "average_")
    (keyword (s/replace-first type #"average_" ""))
    (keyword type)))

(defn get-teams-stats
  "Queries Yahoo's fantasy API for all teams stats of the given type and
  durations, if specified.

  Valid types: week, lastweek, lastmonth, average_lastweek, average_lastmonth,
  date, season, average_season"
  ([game league-id]
   (get-teams-stats game league-id nil nil))
  ([game league-id type]
   (get-teams-stats game league-id type 0))
  ([game league-id type duration]
   (fn [auth]
     (q/ask auth (q/query YAHOO_FANTASY_API_ENDPOINT
                          :league
                          (keyword (mk-league-key game league-id))
                          :teams
                          (cond (and type duration)
                                [:stats {:type type
                                         (stat-type->request-param type) duration}]
                                (not (nil? type))
                                [:stats {:type type}]
                                :else
                                :stats))))))

(defn get-teams-rosters
  "Queries Yahoo's fantasy API for all teams' rosters."
  [game league-id]
  (q/ask (q/query YAHOO_FANTASY_API_ENDPOINT
                  :league
                  (keyword (mk-league-key game league-id))
                  :teams
                  :roster)))

(defn get-stat-category-leaders
  "Queries Yahoo's fantasy API for the current leaders of a stat for a certain date."
  [game stat-id date limit]  
  (q/ask (q/query YAHOO_FANTASY_API_ENDPOINT
                  :game
                  (keyword game)
                  [:players {:sort stat-id
                             :sort_type "date"
                             :sort_date date
                             :count limit}]
                  [:stats {:type "date"
                           :date date}])))

(defn get-add-drops
  "Queries Yahoo's fantasy API for the last `count` add/drop transactions."
  [game league-id count]
  (q/ask (q/query YAHOO_FANTASY_API_ENDPOINT
                  :league
                  (keyword (mk-league-key game league-id))
                  [:transactions {:types "add,drop"
                                  :count count}] )))
