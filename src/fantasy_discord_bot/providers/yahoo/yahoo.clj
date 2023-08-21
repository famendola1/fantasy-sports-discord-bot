(ns fantasy-discord-bot.providers.yahoo.yahoo
  (:require [clojure.string :as s]
            [fantasy-discord-bot.providers.yahoo.constants :as c]
            [fantasy-discord-bot.providers.yahoo.yquery :as yq]))

(defmulti calculate-matchup-result (fn [sport _ _] sport))

(defmethod calculate-matchup-result :nba
  [_ home-team away-team]
  (let [home-stats (filter #(c/nba-9cat-ids (:stat_id (:stat %)))
                           (get-in home-team [:team :team_stats :stats]))
        away-stats (filter #(c/nba-9cat-ids (:stat_id (:stat %)))
                           (get-in away-team [:team :team_stats :stats]))
        stat-pairs (map vector home-stats away-stats)]
    (reduce (fn [res [home away]]
              (let [home-val (Float/parseFloat (get-in home [:stat :value]))
                    away-val (Float/parseFloat (get-in away [:stat :value]))]
                (cond
                  (c/nba-inverse-stats (:stat_id (:stat home)))
                  (cond (> home-val away-val) (update res :loss inc)
                        (< home-val away-val) (update res :win inc)
                        :else (update res :tie inc))
                  (> home-val away-val) (update res :win inc)
                  (< home-val away-val) (update res :loss inc)
                  :else (update res :tie inc))))
            {:win 0
             :loss 0
             :tie 0}
            stat-pairs)))

(defmethod calculate-matchup-result :nfl
  [_ home-team away-team]
  {:win (Float/parseFloat (get-in home-team [:team :team_points :total]))
   :loss (Float/parseFloat (get-in away-team [:team :team_points :total]))})

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

(defn- format-matchup-result
  [team matchup]
  (let [result (cond (get-in matchup [:matchup :is_tied])
                     "T"
                     (= (get-in team [:team :team_key])
                        (get-in matchup [:matchup :winner_team_key]))
                     "W"
                     :else
                     "L")]
    (condp = (get-in matchup [:matchup :status])
      "postevent"
      (format "%2s: %s %s\n"
              (get-in matchup [:matchup :week])
              (get-in (second (:teams (:matchup matchup))) [:team :name])
              result)
      "midevent"
      (format "%2s: *%s*\n"
              (get-in matchup [:matchup :week])
              (get-in (second (:teams (:matchup matchup))) [:team :name]))
      "preevent"
      (format "%2s: %s\n"
              (get-in matchup [:matchup :week])
              (get-in (second (:teams (:matchup matchup))) [:team :name])))))

(defn- format-matchup-results
  [team]
  (let [results (reduce (fn [res matchup]
                          (if (= (get-in matchup [:matchup :status])
                                 "postevent")
                            (cond (get-in matchup [:matchup :is_tied])
                                  (update res "T" inc)
                                  (= (get-in team [:team :team_key])
                                     (get-in matchup [:matchup :winner_team_key]))
                                  (update res "W" inc)
                                  :else
                                  (update res "L" inc))
                            res))
                        {"T" 0 "W" 0 "L" 0}
                        (get-in team [:team :matchups]))]
    (str (->> (get-in team [:team :matchups])
              (map (partial format-matchup-result team))
              (s/join "\n"))
         (format "\nTotal: %d-%d-%d"
                 (results "W")
                 (results "L")
                 (results "T")))))

(defn- format-result
  [home-team-name [away-team-name result]]
  (format "%s (%.2f)\n%s (%.2f)\n"
          home-team-name
          (float (:win result))
          away-team-name
          (float (:loss result))))

(defn- format-vs-league-matchups
  [sport team other-teams]
  (let [results (map (partial calculate-matchup-result sport team) other-teams)
        win (count (filter #(> (:win %) (:loss %)) results))
        lost (count (filter #(< (:win %) (:loss %)) results))
        tied (- (count results) win lost)]
    (str (->> (map vector (map (comp :name :team) other-teams) results)
              (map (partial format-result (get-in team [:team :name])))
              (s/join "\n"))
         (format "\nTotal: %d-%d-%d" win lost tied))))

(defn- mk-error-msg
  [message]
  {:header "A problem occurred"
   :body message})

(defn- find-team-by-name
  [teams team-name]
  (first (filter #(= team-name
                     (:name (:team %)))
                 teams)))

(defmulti handle-command
  "Hanlder for commands for the Yahoo provider."
  (fn [_ _ _ cmd] (:cmd cmd)))

(defmethod handle-command :standings
  [sport league-id auth _]
  (let [resp ((yq/get-standings (name sport) league-id) auth)]
    {:header "Standings"
     :body (->> (get-in resp [:fantasy_content :league :standings :teams])
                (map format-team-standings)
                (s/join "\n"))}))

(defmethod handle-command :scoreboard
  [sport league-id auth cmd]
  (let [week-matcher (re-matcher #"(?<week>\d+)" (:args cmd))
        query-func (if (.matches week-matcher)
                     (yq/get-scoreboard (name sport) league-id (.group week-matcher "week"))
                     (yq/get-scoreboard (name sport) league-id))
        resp (query-func auth)]
    (if (seq (:error resp))
      (mk-error-msg (:description resp))
      {:header (str "Week "                  
                    (get-in resp [:fantasy_content :league :scoreboard :week])
                    " Scoreboard")
       :body (->> (get-in resp [:fantasy_content :league :scoreboard :matchups])
                  (map format-matchup)
                  (s/join "\n"))})))

(defmethod handle-command :schedule
  [sport league-id auth cmd]
  (let [team-name (:args cmd)
        resp ((yq/get-teams-matchups (name sport) league-id) auth)
        teams (get-in resp [:fantasy_content :league :teams])
        team (find-team-by-name teams team-name)]
    (cond (seq (:error resp)) (mk-error-msg (get-in resp [:error :description]))
          (nil? team) (mk-error-msg (format "team \"%s\" not found", team-name))
          :else {:header (str team-name " Schedule")
                 :body (format-matchup-results team)})))

(defmethod handle-command :vs
  [sport league-id auth cmd]
  (let [team-name (s/trim (:args cmd))
        resp ((yq/get-teams-stats (name sport) league-id "week") auth)
        teams (get-in resp [:fantasy_content :league :teams])
        team (find-team-by-name teams team-name)]
    (cond (seq (:error resp)) (mk-error-msg (get-in resp [:error :description]))
          (nil? team) (mk-error-msg (format "team \"%s\" not found", team-name))
          :else {:header (str team-name " vs. The League")
                 :body (format-vs-league-matchups
                        sport
                        team
                        (remove #(= team-name (get-in % [:team :name])) teams))})))

(defn init-provider
  "Initializes the Yahoo provider for handling commands the bot receives."
  [config]
  (fn [cmd]
    (handle-command (:sport config)
                    (:league-id config)
                    (:auth config)
                    cmd)))
