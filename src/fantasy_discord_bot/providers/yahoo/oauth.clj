(ns fantasy-discord-bot.providers.yahoo.oauth
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [ring.util.codec :refer [url-encode]]))

(def MSEC_IN_HOUR 3600000)
(def API-URL "https://api.login.yahoo.com/oauth2/")

(defn oauth2-params  
  [client-id client-secret]
  {:client-id client-id
   :client-secret client-secret
   :redirect-uri "oob"
   :authorize-uri (str API-URL "request_auth")
   :access-token-uri (str API-URL "get_token")
   :request-token-uri (str API-URL "get_request_token")})

(defn authorize-uri
  "Returns the URI to request OAuth2 authorizaton."
  [client-params]
  (str (:authorize-uri client-params)
       "?response_type=code"
       "&client_id="
       (url-encode (:client-id client-params))
       "&redirect_uri="
       (url-encode (:redirect-uri client-params))))

(defn get-token
  "Sends a request for an OAuth2 access token. Returns the body of the
  response, if successful, otherwise nil.  Code should be the code retrieved
  from the authorization URI."
  [client-params code]
  (try
    (-> (http/post (:access-token-uri client-params)
                   {:form-params {:code code
                                  :grant_type "authorization_code"
                                  :redirect-uri (:redirect-uri client-params)}
                    :basic-auth (str (:client-id client-params) ":" (:client-secret client-params))
                    :as :x-www-form-urlencoded})
        :body)
    (catch Exception _ nil)))

(defn refresh-tokens
  "Refreshs an OAuth2 access token given an old token and a refresh token."
  [client-params access-token refresh-token]
  (try
    (let [resp
          (http/post (:access-token-uri client-params)
                     {:form-params {:grant_type       "refresh_token"
                                    :refresh_token    refresh-token
                                    :redirect-uri (:redirect-uri client-params)}
                      :basic-auth (str (:client-id client-params) ":" (:client-secret client-params))
                      :as :x-www-form-urlencoded})
          {access-token "access_token" refresh-token "refresh_token"} (json/read-str (:body resp))]
      [access-token refresh-token])
    (catch Exception _ nil)))
