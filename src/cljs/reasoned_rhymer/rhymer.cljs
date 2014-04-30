(ns reasoned-rhymer.rhymer
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]
                   [dommy.macros :refer [node sel sel1]])
  (:require [goog.events :as events]
            [cljs.core.async :refer [<! >! put! take! chan]]
            [cljs.reader :refer [read-string]]
            [dommy.core :as dom]
            [ajax.core :refer [GET POST]]
            [clojure.string :as str]
            [om.core :as om]
            [om.dom :as d]
            [reasoned-rhymer.components :as c]
            [reasoned-rhymer.wit :as wit]))

(enable-console-print!)


(defn gen-app-state [init-data comms]
  (assoc init-data :comms comms :viewing :get-analysis))

(def controls-ch
  (chan))

(def api-ch
  (chan))

(def app-state
  (let [init-data (cljs.reader/read-string
                   (-> js/initial_app_state
                       (str/replace #"&quot;" "\"")
                       (str/replace #"&amp;" "&")))]
    (atom (gen-app-state init-data {:controls controls-ch :api api-ch}))))

(defn app [data owner opts]
  (reify
    om/IRender
    (render [this]
      (d/div #js {:className "row"}
        (om/build c/header data)
        (cond
           (= (get-in data [:viewing]) :get-analysis) (om/build c/get-view data)
           (= (get-in data [:viewing]) :post-analysis) (om/build c/post-view data)
           :else (d/h2 nil "No View Hooked Up"))))))

(defn start [target state app]
  (let [comms (:comms state)]
    (om/root
     app
     state
     {:target target
      :opts {:comms comms}})))

(start (sel1 :#app) app-state (if (= js/page_version "wit") wit/app app))
















