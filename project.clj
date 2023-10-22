(defproject fantasy-discord-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[clj-http "3.12.3"]
                 [clj-oauth2 "0.2.0"]
                 [clj-time "0.15.2"]
                 [com.github.discljord/discljord "1.3.1"]
                 [org.apache.httpcomponents/httpclient "4.5.3"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/tools.cli "1.0.219"]
                 [ring/ring-codec "1.1.3"]]
  :main ^:skip-aot fantasy-discord-bot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
