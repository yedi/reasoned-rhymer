(ns reasoned-rhymer.handler
  (:use compojure.core)
  (:use ring.middleware.edn)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [ring.util.response :as resp]
            [ring.adapter.jetty :refer [run-jetty]]
            [rhyme-finder.core :as rhyme]
            [reasoned-rhymer.db :as db]
            [selmer.parser :as selmer]))

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

(defroutes app-routes
  (GET "/" [] (selmer/render-file "reasoned_rhymer/client.html"
                                  {:app-state (pr-str {:titles (db/get-all-titles)})}))
  (GET "/analysis" req (get-analysis req))
  (POST "/analyze" req (new-analysis req))
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (-> app-routes
      wrap-edn-params
      handler/site))

(defn start-server [port]
  (run-jetty app {:port port :join? false}))

(defn -main [& args]
  (let [port (Integer. (or (first args) "3000"))]
    (start-server port)))

