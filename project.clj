(defproject reasoned-rhymer "0.1.0-SNAPSHOT"
  :description "Rhyme Analysis and Exploration App"
  :url "http://reasonedrhymer.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.216"]
                 [org.clojure/core.async "0.2.385"]
                 [org.omcljs/om "0.9.0" :exclusions [cljsjs/react]]
                 [cljsjs/react-with-addons "0.13.3-0"]
                 [ring "1.2.0"]
                 [compojure "1.5.1"]
                 [com.datomic/datomic-free "0.9.4384" :exclusions [com.google.guava/guava]]
                 [prismatic/dommy "0.1.1"]
                 [cljs-ajax "0.2.3"]
                 [fogus/ring-edn "0.2.0"]
                 [selmer "0.5.4"]
                 [rhyme-finder "0.1.0-SNAPSHOT"]
                 [org.clojure/tools.cli "0.3.1"]
                 [environ "0.4.0"]
                 [clj-http "2.2.0"]]
  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-ring "0.9.7" :exclusions [org.clojure/clojure]]]
  :source-paths ["src/clj"]
  :hooks [leiningen.cljsbuild]
  :clean-targets ^{:protect false} ["resources/public/out", "resources/public/rhymer.js"]
  :cljsbuild {:builds [{
                        :id "prod"
                        :source-paths ["src/cljs"]
                        :compiler {
                                   :output-to "resources/public/rhymer.js"
                                   :optimizations :advanced
                                   :pretty-print false}}
                        {
                        :id "dev"
                        :source-paths ["src/cljs"]
                        :compiler {
                                   :output-to "resources/public/rhymer.js"
                                   :optimizations :none
                                   :output-dir "resources/public/out"
                                   :source-map true}}]}
  :main reasoned-rhymer.handler
  :aot [reasoned-rhymer.handler]
  :ring {:handler reasoned-rhymer.handler/app
         :auto-reload? true
         :auto-refresh true})
