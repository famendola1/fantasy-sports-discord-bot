(ns fantasy-discord-bot.providers.yahoo.yahoo
  (:require [clojure.string :as s]
            [fantasy-discord-bot.providers.yahoo.yquery :as yq]))

(defn- format-team-standings
  "Format the standings for a team into a string."
  [team]
  (format "%2d: %s (%d-%d-%d)"
          (get-in team [:team :team_standings :rank])
          (get-in team [:team :name])
          (get-in team [:team :team_standings :wins])
          (get-in team [:team :team_standings :losses])
          (get-in team [:team :team_standings :ties])))

(defn- format-matchup
  "Format a matchup into a string."
  [matchup]
  (let [result (reduce (fn [res stat]
                         (let [winner (get-in stat [:stat_winner :winner_team_key])]
                           (if-not (nil? (res winner))
                             (assoc res winner 1)
                             (update res winner inc))))
                       {}
                       (get-in matchup [:matchup :stat_winners]))
        home-team (:team (first (get-in matchup [:matchup :teams])))
        away-team (:team (second (get-in matchup [:matchup :teams])))]
    (format "%s (%d)\n%s (%d)\n"
            (:name home-team)
            (result (:team_key home-team))
            (:name away-team)
            (result (:team_key away-team)))))

(defmulti handle-command
  "Hanlder for commands for the Yahoo provider."
  (fn [_ _ _ cmd] (:cmd cmd)))

(defmethod handle-command :standings
  [sport league-id auth _]
  (let [resp ((yq/get-standings sport league-id) auth)]
    {:header "Standings"
     :body (->> (get-in resp [:fantasy_content :league :standings :teams])
                (map format-team-standings)
                (s/join "\n"))}))

(defmethod handle-command :scoreboard
  [sport league-id auth cmd]
  (let [week-matcher (re-matcher #"(?<week>\d+)" (:args cmd))
        query-func (if (.matches week-matcher)
                     (yq/get-scoreboard sport league-id (.group week-matcher "week"))
                     (yq/get-scoreboard sport league-id))
        resp (query-func auth)]
    (if (seq (:error resp))
      {:header "A problem occurred"
       :description (:detail resp)}
      {:header (str "Week "                  
                    (get-in resp [:fantasy_content :league :scoreboard :week])
                    " Scoreboard")
       :body (->> (get-in resp [:fantasy_content :league :scoreboard :matchups])
                  (map format-matchup)
                  (s/join "\n"))})))

(defn init-provider
  "Initializes the Yahoo provider for handling commands the bot receives."
  [config]
  (fn [cmd]
    (handle-command (name (:sport config))
                    (:league-id config)
                    (:auth config)
                    cmd)))
