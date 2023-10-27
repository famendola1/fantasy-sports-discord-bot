(ns fantasy-discord-bot.providers.yahoo.yahoo
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.string :as s]
            [fantasy-discord-bot.providers.yahoo.constants :as c]
            [fantasy-discord-bot.providers.yahoo.yquery :as yq]))

(defmulti calculate-matchup-result
  "Calculates the result of a matchup based on each team's stats."
  (fn [sport _ _] sport))

(defmethod calculate-matchup-result :nba
  [_ home-team away-team]
  (let [home-stats (sort-by
                    (comp :stat_id :stat)
                    (filter #(c/nba-9cat-ids (Integer/parseInt (:stat_id (:stat %))))
                            (get-in home-team [:team :team_stats :stats])))
        away-stats (sort-by
                    (comp :stat_id :stat)
                    (filter #(c/nba-9cat-ids (Integer/parseInt (:stat_id (:stat %))))
                            (get-in away-team [:team :team_stats :stats])))
        stat-pairs (map vector home-stats away-stats)]
    (reduce (fn [res [home away]]
              (let [home-val (Float/parseFloat (get-in home [:stat :value]))
                    away-val (Float/parseFloat (get-in away [:stat :value]))]
                (cond
                  (c/nba-inverse-stats (Integer/parseInt (:stat_id (:stat home))))
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
          (get-in team [:team :team_standings :wins] 0)
          (get-in team [:team :team_standings :losses] 0)
          (get-in team [:team :team_standings :ties] 0)))

(defn- format-matchup
  "Format a matchup into a string."
  [matchup]
  (let [result (reduce (fn [res stat]
                         (let [winner (get-in stat [:stat_winner :winner_team_key])]
                           (if (nil? (res winner))
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
  "Formats a single matchup from Yahoo."
  (let [result (cond (get-in matchup [:matchup :is_tied])
                     "T"
                     (= (get-in team [:team :team_key])
                        (get-in matchup [:matchup :winner_team_key]))
                     "W"
                     :else
                     "L")]
    (condp = (get-in matchup [:matchup :status])
      "postevent"
      (format "%2s: %s %s"
              (get-in matchup [:matchup :week])
              (get-in (second (:teams (:matchup matchup))) [:team :name])
              result)
      "midevent"
      (format "%2s: *%s*"
              (get-in matchup [:matchup :week])
              (get-in (second (:teams (:matchup matchup))) [:team :name]))
      "preevent"
      (format "%2s: %s"
              (get-in matchup [:matchup :week])
              (get-in (second (:teams (:matchup matchup))) [:team :name])))))

(defn- format-matchup-results
  "Formats the results of matchup data from Yahoo."
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
  "Formats a result of single calculated matchup"
  [home-team-name [away-team-name result]]
  (format "%s (%.2f)\n%s (%.2f)\n"
          home-team-name
          (float (:win result))
          away-team-name
          (float (:loss result))))

(defn- format-vs-league-matchups
  "Formats matchups for the !vs command."
  [sport team other-teams]
  (let [results (map (partial calculate-matchup-result sport team) other-teams)
        win (count (filter #(> (:win %) (:loss %)) results))
        lost (count (filter #(< (:win %) (:loss %)) results))
        tied (- (count results) win lost)]
    (str (->> (map vector (map (comp :name :team) other-teams) results)
              (map (partial format-result (get-in team [:team :name])))
              (s/join "\n"))
         (format "\nTotal: %d-%d-%d" win lost tied))))

(defn- get-player-roster-order
  [roster-order player]
  (get roster-order
       (get-in player [:player :selected_position :position])
       100))

(defn- format-roster-entry
  [player]
  (format "%s: %s"
          (get-in player [:player :selected_position :position])
          (get-in player [:player :name :full])))

(defmulti format-team-roster (fn [sport _] sport))

(defmethod format-team-roster :nba
  [sport team]
  (let [players (get-in team [:team :roster :players])
        sorted-players (sort-by (partial get-player-roster-order c/nba-roster-order) players)]
    (s/join "\n" (map format-roster-entry sorted-players))))

(defmethod format-team-roster :nfl
  [sport team]
  (let [players (get-in team [:team :roster :players])
        sorted-players (sort-by (partial get-player-roster-order c/nfl-roster-order) players)]
    (s/join "\n" (map format-roster-entry sorted-players))))

(defn- format-error-message
  [message]
  {:header "A problem occurred"
   :body message})

(defn- find-team-by-name
  [teams team-name]
  (first (filter #(= team-name (:name (:team %))) teams)))

(defmulti handle-command
  "Hanlder for commands for the Yahoo provider."
  (fn [_ _ _ cmd] (:cmd cmd)))

(defmethod handle-command :standings
  [sport league-id auth _]
  (let [resp ((yq/get-standings (name sport) league-id) auth)]
    (if (seq (:error resp))
      (format-error-message (get-in resp [:error :description]))
      {:header "Standings"
       :body (->> (get-in resp [:fantasy_content :league :standings :teams])
                  (map format-team-standings)
                  (s/join "\n"))})))

(defmethod handle-command :scoreboard
  [sport league-id auth cmd]
  (let [week-matcher (re-matcher #"(?<week>\d+)" (:args cmd))
        query-func (if (.matches week-matcher)
                     (yq/get-scoreboard (name sport) league-id (.group week-matcher "week"))
                     (yq/get-scoreboard (name sport) league-id))
        resp (query-func auth)]
    (if (seq (:error resp))
      (format-error-message (get-in resp [:error :description]))
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
    (cond
      (not (seq team-name)) (format-error-message "must provide a team name")
      (seq (:error resp)) (format-error-message (get-in resp [:error :description]))
      (nil? team) (format-error-message (format "team \"%s\" not found", team-name))
      :else {:header (str team-name " Schedule")
             :body (format-matchup-results team)})))

(defmethod handle-command :vs
  [sport league-id auth cmd]
  (let [team-name (s/trim (:args cmd))
        resp ((yq/get-teams-stats (name sport) league-id "week") auth)
        teams (get-in resp [:fantasy_content :league :teams])
        team (find-team-by-name teams team-name)]
    (cond (nil? team) (format-error-message (format "team \"%s\" not found", team-name))
          (seq (:error resp)) (format-error-message (get-in resp [:error :description]))
          :else {:header (str team-name " vs. The League")
                 :body (format-vs-league-matchups
                        sport
                        team
                        (remove #(= team-name (get-in % [:team :name])) teams))})))

(defmethod handle-command :roster
  [sport league-id auth cmd]
  (let [team-name (s/trim (:args cmd))
        resp ((yq/get-teams-rosters (name sport) league-id) auth)
        teams (get-in resp [:fantasy_content :league :teams])
        team (find-team-by-name teams team-name)]
    (cond (nil? team) (format-error-message (format "team \"%s\" not found", team-name))
          (seq (:error resp)) (format-error-message (get-in resp [:error :description]))
          :else {:header team-name
                 :body (format-team-roster sport team)})))

(defn- get-leaders-for-stat
  [sport stat date auth]
  (let [resp ((yq/get-stat-category-leaders sport stat date 5) auth)
        players (get-in resp [:fantasy_content :game :players])
        leaders (map (fn [p]
                       {:name (get-in p [:player :name :full])
                        :position (get-in p [:player :display_position])
                        :value (first (filter #(= (str stat) (get-in % [:stat :stat_id])) (get-in p [:player :player_stats :stats])))})
                     players)]
    {:stat (c/stat-id-to-name stat)
     :leaders leaders}))

(defn format-leaders
  [leaders]
  (format "%s\n%s\n%s\n"
          (:stat leaders)
          (apply str (repeat (count (:stat leaders)) "-"))
          (s/join "\n"
                  (map #(format "%s - %s" (:name %) (str (or (:value %) 0)))
                       (:leaders leaders)))))

(defmulti get-leaders (fn [sport _ _] sport))

(defmethod get-leaders :nba
  [sport date auth]
  (map #(get-leaders-for-stat sport % date auth) c/nba-9cat-ids))

(defmethod handle-command :leaders
  [sport _ auth cmd]
  (let [date (f/unparse (f/formatter "yyyy-MM-dd") (t/now))
        leaders (get-leaders sport date auth)]
    (if (some nil? leaders)
      (format-error-message "Unable to compute stats leaders.")
      {:header (format "Leaders - %s" date)
       :body (s/join "\n" (map format-leaders leaders))})))

(defn init-provider
"Initializes the Yahoo provider for handling commands the bot receives."
[config]
(fn [cmd]
  (handle-command (:sport config)
                  (:league-id config)
                  (:auth config)
                  cmd)))
