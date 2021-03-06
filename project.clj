(defproject reasoned-rhymer "0.1.0-SNAPSHOT"
  :description "Rhyme Analysis and Exploration App"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2173"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [om "0.6.0"]
                 [ring "1.2.2"]
                 [compojure "1.1.6"]
                 [com.datomic/datomic-free "0.9.4324"]
                 [prismatic/dommy "0.1.1"]
                 [cljs-ajax "0.2.3"]
                 [fogus/ring-edn "0.2.0"]
                 [selmer "0.5.4"]
                 [rhyme-finder "0.1.0-SNAPSHOT"]
                 [org.clojure/tools.cli "0.3.1"]
                 [environ "0.4.0"]]
  :plugins [[lein-cljsbuild "1.0.2"]
            [lein-ring "0.8.8"]]
  :source-paths ["src/clj"]
  :cljsbuild {:builds [{
                        :id "dev"
                        :source-paths ["src/cljs"]
                        :compiler {
                                   :output-to "resources/public/rhymer.js"
                                   :optimizations :none
                                   :output-dir "resources/public/out"
                                   :source-map true}}
                       {
                        :id "prod"
                        :source-paths ["src"]
                        :compiler {
                                   :output-to "resources/public/rhymer.js"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :preamble ["react/react.min.js"]
                                   :externs ["react/externs/react.js"]}}]}
  :main reasoned-rhymer.handler
  :ring {:handler reasoned-rhymer.handler/app
         :auto-reload? true
         :auto-refresh true})
