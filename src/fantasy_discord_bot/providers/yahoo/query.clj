(ns fantasy-discord-bot.providers.yahoo.query
  (:require [clojure.string :refer [join]]
            [clojure.xml :as xml]
            [clj-http.client :as http]
            [fantasy-discord-bot.providers.yahoo.oauth :as oauth])
  (:import (org.apache.http.client.utils URLEncodedUtils)
           (org.apache.http.message BasicNameValuePair)))

(def xml-list-tags #{:eligible_positions
                     :leagues
                     :players
                     :transactions
                     :matchups
                     :stat_winners
                     :stat_position_types
                     :stats
                     :groups
                     :games
                     :teams
                     :team_logos
                     :managers
                     :roster_positions
                     :divisions
                     :recommended_trade_partners
                     :position_types
                     :game_weeks
                     :positional_ranks
                     :starting_players
                     :bench_players
                     :draft_results})

(declare combine remove-star url-part?)

(derive clojure.lang.IPersistentList ::list)
(derive clojure.lang.Cons ::list)
(derive clojure.lang.IPersistentVector ::list)

(defn- named? [a]
  (instance? clojure.lang.Named a))

(defn- as-str [a]
  (if (named? a)
    (name a)
    (str a)))

(defmulti url-form class)

(defmethod url-form
  clojure.lang.IPersistentSet
  [form]
  (join "," (map url-form form)))

(defmethod url-form
  ::list
  [form]
  (let [[resource p-map] form]
    (combine (name resource) p-map)))

(defmethod url-form
  :default
  [form]
  (as-str form))

(defmacro query
  [url & resources]
  (let [[urls q-maps] (split-with url-part? resources)
        [k q-map] (first q-maps)
        url-vec (vec urls)
        url-keys (if (seq q-maps) 
                   (conj url-vec (remove-star k)) 
                   url-vec)] 
    `(vector (join "/" (cons ~url (map url-form (list ~@url-keys))))
             ~q-map)))

(defn- remove-star
  [k]
  (let [s (name k)
        k-str (apply str (take-while #(not (= \* %)) s))]
    (keyword k-str)))

(defn- url-part?  
  [x] 
  (or (keyword? x) 
      (and (coll? x) 
           (not (= \* (-> x first name last))))))

(defn- combine
  [resource p-map]
  (let [params (for [[k v] p-map]
                 (str (url-form k) "=" (url-form v)))]
    (apply str resource \; (join \; params))))

(defn- map->name-value-pairs
  "Take an associative structure and return a sequence of BasicNameValuePairs.
  Any associated value that is sequential will appear multiple times in the output, so that
  
    {:foo [\"bar\" \"baz\"]}
  
  will produce pairs that encode to
  
    ?foo=bar&foo=baz"
  [q]
  (mapcat
   (fn [[param value]]
     (if (and (sequential? value)
              (not (empty? value)))
       (map (partial (fn [#^String p v]
                       (new BasicNameValuePair p (as-str v)))
                     (as-str param))
            value)
       [(new BasicNameValuePair (as-str param) (str value))]))
   q))

(defn- encode-query [q]
  "Return an encoded query string.
  q is a map or list of pairs."
  (when (and q (not (empty? q)))
    (. URLEncodedUtils format
       (map->name-value-pairs q)
       "UTF-8")))

(defn- send-request
  [url token]
  (-> (http/get url {:oauth-token token
                     :throw-exceptions false})
      :body))

(defn- string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(defn- collect-items
  [tag items]
  (if (xml-list-tags tag)
    items
    (into {} items)))

(defn- convert-content
  [content]
  (if (and (> (count (:content content)) 0) (every? map? (:content content)))
    (assoc {}
           (:tag content)
           (collect-items (:tag content)
                          (map convert-content (:content content))))
    (assoc {} (:tag content) (first (:content content)))))

(defn ask 
  "Make a Yahoo query or a query function"
  ([auth q-info] ((ask q-info) auth))
  ([[url url-map]] 
   (fn [auth]
     (let [q (encode-query url-map)
           [access-token _](oauth/refresh-tokens
                            (oauth/oauth2-params (:client-id auth)
                                                 (:client-secret auth))
                            (:access-token auth)
                            (:refresh-token auth))]
       (convert-content
        (xml/parse
         (string->stream (send-request (str url "?" q) access-token))))))))

