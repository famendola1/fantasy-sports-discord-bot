(ns fantasy-discord-bot.providers.yahoo.constants)

(def valid-stat-types
  #{"week" "lastweek" "lastmonth" "average_lastweek"
    "average_lastmonth" "date" "season" "average_season"})

(def stat-id-to-name
  {5 "FG%"
   8 "FT%"
   10 "3PM"
   12 "PTS"   
   15 "REB"
   16 "AST"
   17 "STL"
   18 "BLK"
   19 "TOV"
   9004003 "FG"
   9007006 "FT"})

(def stat-name-to-id
  (reduce (fn [out [id name]]
            (assoc out name id))
          {}
          stat-id-to-name))

(def nba-9cat-ids
  #{5 8 10 12 15 16 17 18 19})

(def nba-inverse-stats
  #{19})

(def nba-roster-order
  {"PG" 0
   "SG" 1
   "G" 2
   "SF" 3
   "PF" 4
   "F" 5
   "C" 6
   "Util" 7
   "BN" 8
   "IL" 9
   "IL+" 10})

(def nfl-roster-order
  {"QB" 0
   "WR" 1
   "RB" 2
   "TE" 3
   "W/R" 4
   "W/R/T" 5
   "K" 6
   "DEF" 7
   "BN" 8
   "IR" 9})
