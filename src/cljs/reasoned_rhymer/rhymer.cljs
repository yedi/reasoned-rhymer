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
            [om.dom :as d]))

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

(defn header [data owner]
  (reify
    om/IRender
    (render [this]
      (d/div #js {:className "page-header"}
        (d/ul #js {:className "nav nav-pills pull-right"}
          (d/li #js {:id "view-analyses-btn"
                     :className (when (= (:viewing data) :get-analysis) "active")}
            (d/a #js {:href "#"
                      :onClick #(om/update! data :viewing :get-analysis)}
               "View Analyses"))
          (d/li #js {:id "analyze-poem-btn"
                     :className (when (= (:viewing data) :post-analysis) "active")}
            (d/a #js {:href "#"
                      :onClick #(om/update! data :viewing :post-analysis)}
               "Analyze Poem"))
          (d/li nil
            (d/a #js {:href "http://github.com/yedi/rhyme-finder"}
              "View on Github")))
        (d/h3 #js {:className "text-muted"} "Reasoned Rhymer")))))

(defn handle-change [e owner field]
  (om/set-state! owner field (.. e -target -value)))

(defn post-view [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:post (chan) :title "" :text ""})
    om/IWillMount
    (will-mount [_]
      (let [post-ch (om/get-state owner :post)]
        (go-loop []
          (let [posting (<! post-ch)]
            (POST "/analyze"
                  {:params {:title (om/get-state owner :title)
                            :text (om/get-state owner :text)}
                   :handler (fn [resp]
                              (om/update! data :analysis resp)
                              (om/transact! data :titles #(conj % (:title resp)))
                              (om/update! data :viewing :get-analysis))}))
          (recur))))
    om/IRender
    (render [this]
       (d/div #js {:id "add-poem" :className "nav-section"}
         (d/form #js {:id "add-poem-form" :className "form-info"}
           (d/button #js {:type "button" :className "btn btn-info"
                          :onClick (fn [e] (put!(om/get-state owner :post) 1) false)}
              "Analyze poem or song")
           (d/input #js {:id "poem-title" :type "text" :className "form-control"
                         :placeholder "Name of poem or song"
                         :value (om/get-state owner :title)
                         :onChange #(handle-change % owner :title)})
           (d/textarea #js {:id "poem-text" :rows 16 :className "form-control"
                            :placeholder "Lyrics go here"
                            :value (om/get-state owner :text)
                            :onChange #(handle-change % owner :text)}))))))

(def COLORS ["red" "orange" "yellow" "green" "blue" "violet" "purple" "brown"])

(defn brs [text]
  (print (str "br: " text))
  (take (count (filter #{\newline} text))
        (repeatedly (partial d/br nil))))

(defn word-span [idx word wmap]
  (print [idx word])
  (let [matching-id (first (get wmap idx))
        color (if matching-id (nth (cycle COLORS) matching-id) "black")]
    (d/a #js {:style #js {:color color}} word)))

(defn text-spans [text wmap]
  (print wmap)
  (let [tokens (map (partial apply str)
                    (partition-by #{\space \tab \newline} text))]
    (loop [tokens tokens
           idx 0
           spans '()]
      (cond
        (empty? tokens) (reverse spans)
        (re-matches #"\s*" (first tokens))
          (recur (rest tokens)
                 idx
                 (apply (partial conj spans)
                         (if (empty? (filter #{\newline} (first tokens)))
                           (list (d/span nil (first tokens)))
                           (brs (first tokens)))))  ; handle line breaks
        :else (recur (rest tokens)
                     (inc idx)
                     (conj spans (word-span idx (first tokens) wmap)))))))

(defn analysis-view [data owner]
  (reify
    om/IRender
    (render [this]
      (d/div #js {:className "row"}
        (apply d/div #js {:className "col-xs-6"}
          (text-spans (get-in data [:text]) (get-in data [:words])))
        (d/div #js {:className "col-xs-6"} (get-in data [:text]))))))

(defn add-combos [ret [index {:keys [value streams]}]]
  (let [streams (flatten streams)
        reducing-fn (fn [m stream]
                      (update-in m [(:index stream)] conj index))]
    (reduce reducing-fn ret streams)))

(defn update-values [m f]
  (into {} (for [[k v] m] [k (f v)])))

(defn word=>combo [analysis]
  (update-values (reduce add-combos {} (map vector (range) analysis)) sort))

(defn update-analysis [resp data]
  (let [wmap (word=>combo (:analysis resp))]
    (om/update! data :analysis (assoc resp :words wmap))))

(defn get-view [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:get (chan) :selected (first (:titles data))})
    om/IWillMount
    (will-mount [_]
      (let [get-ch (om/get-state owner :get)]
        (go-loop []
          (let [getting (<! get-ch)]
            (GET "/analysis"
                 {:params {:title (om/get-state owner :selected)}
                  :handler #(update-analysis % data)})
            (print (str "Retrieving: " getting)))
          (recur))))
    om/IRender
    (render [this]
      (let [selected (om/get-state owner :selected)
            get-ch (om/get-state owner :get)]
        (d/div #js {:id "view-analyses" :className "nav-section"}
          (d/form #js {:id "get-analysis-form" :className "form-inline"}
            (apply d/select #js {:id "select-title" :value selected
                                 :onChange #(handle-change % owner :selected)}
              (map #(d/option nil %) (:titles data)))
            (d/button #js {:type "button" :className "btn btn-info"
                           :onClick (fn [e] (put! get-ch selected) false)}
              "See Analysis"))
          (om/build analysis-view (:analysis data)))))))

(defn app [data owner opts]
  (reify
    om/IRender
    (render [this]
      (d/div nil
        (om/build header data)
        (cond
           (= (get-in data [:viewing]) :get-analysis) (om/build get-view data)
           (= (get-in data [:viewing]) :post-analysis) (om/build post-view data)
           :else (d/h2 nil "No View Hooked Up"))))))

(defn start [target state]
  (let [comms (:comms state)]
    (om/root
     app
     state
     {:target target
      :opts {:comms comms}})))

(start (sel1 :#app) app-state)

(print (get-in @app-state [:analysis :analysis]))
