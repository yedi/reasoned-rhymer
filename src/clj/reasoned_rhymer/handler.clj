(ns reasoned-rhymer.handler
  (:gen-class)
  (:use compojure.core)
  (:use ring.middleware.edn)
  (:use environ.core)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [ring.util.response :as resp]
            [ring.adapter.jetty :refer [run-jetty]]
            [rhyme-finder.core :as rhyme]
            [reasoned-rhymer.db :as db]
            [selmer.parser :as selmer]
            [clojure.tools.cli :refer [parse-opts]]
            [clj-http.client :as client]))

(selmer/cache-off!)

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn analyze!
  ([filename]
     (analyze! filename (slurp filename)))
  ([title txt]
     (let [poem (rhyme/format-as-poem txt)
           rs (rhyme/rhyme-streams poem 2 6 24 2)]
       (db/add-poem-analysis! title txt (pr-str rs))
       rs)))

(defn get-analysis [req]
  (let [title (-> req :params :title)]
    (generate-response (db/get-poem-data title))))

(defn new-analysis [req]
  (let [title (-> req :params :title)
        txt (-> req :params :text)
        rs (analyze! title txt)]
    (generate-response (db/get-poem-data title))))

(defn reanalyze!
  ([]
    (println "reanalyzing all songs")
    (doall (map reanalyze! (db/get-all-titles))))
  ([title]
    (println (str "reanalyzing: " title))
    (analyze! title (:text (db/get-poem-data title)))))

(defn analyze-text [text]
  (let [poem (rhyme/format-as-poem text)
        rs (rhyme/rhyme-streams poem 1 6 24 2)]
    (generate-response {:text text :analysis rs :title "custom"})))

(def dev false)

(defn wit-api [q]
  (generate-response
   (:body (client/get "https://api.wit.ai/message"
            {:headers {:Authorization (str "Bearer " (env :wit-bearer-id))}
             :query-params {"q" q} :as :json}))))

(defn gen-context [page-version]
  {:app-state (pr-str {:titles (db/get-all-titles)}) :dev dev
   :ga-id (when-not dev (env :ga-id)) :page-version page-version
   :bearer (env :wit-bearer-id)})

(defroutes app-routes
  (GET "/" [] (selmer/render-file "templates/client.html"
                                  (gen-context "normal")))
  (GET "/witty" [] (selmer/render-file "templates/client.html"
                                  (gen-context "wit")))
  (GET "/wit_api" req (wit-api (get-in req [:params :q])))
  (GET "/analysis" req (get-analysis req))
  (POST "/analyze" req (new-analysis req))
  (POST "/analyze_text" req (analyze-text (get-in req [:params :text])))
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (-> app-routes
      wrap-edn-params
      handler/site))

(defn start-server [port]
  (run-jetty app {:port port :join? false}))

(def cli-options
  [
   ["-p" "--port PORT" "Port number"
    :default 3000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   [nil "--create-db" "Detach from controlling process"]
   [nil "--reanalyze" "Re-analyzes all the songs in the database"]
   ["-d" "--dev" "Run the dev server"]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (when (< 0 (count errors)) (println (clojure.string/join "\n" errors)))
    (when (:create-db options)
      (db/init-db!)
      (System/exit 0))
    (when (:reanalyze options)
      (reanalyze!)
      (System/exit 0))
    (when (:dev options)
      (def dev true))
    (println (str "starting server on port " (:port options)))
    (start-server (:port options))))

