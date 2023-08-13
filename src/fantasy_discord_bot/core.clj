(ns fantasy-discord-bot.core
  (:require [clojure.core.async :as a]
            [clojure.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]]
            [discljord.connections :as c]
            [discljord.events :as e]
            [discljord.messaging :as m]
            [fantasy-discord-bot.handlers :refer [init-handlers]])
  (:gen-class))

(def intents #{:guild-messages})
(def state (atom nil))
(def cli-options
  [["-c" "--config_path PATH" "Path to the config file."
    :default ""
    :id :config
    :parse-fn #(edn/read-string (slurp %))]
   ["-h" "--help"]])

(defn -main
  [& args]
  (let [options (parse-opts args cli-options)
        config (:config (:options options))
        token (:token (:discord config))
        event-ch (a/chan 100)
        connection-ch (c/connect-bot!
                       token
                       event-ch
                       :intents intents)
        messaging-ch (m/start-connection! token)
        init-state {:connection connection-ch
                    :event event-ch
                    :messaging messaging-ch}]
    (reset! state init-state)
    (try (e/message-pump! event-ch
                          (partial e/dispatch-handlers
                                   (init-handlers @state (:provider config))))
         (finally
           (m/stop-connection! messaging-ch)
           (c/disconnect-bot! connection-ch)))))

