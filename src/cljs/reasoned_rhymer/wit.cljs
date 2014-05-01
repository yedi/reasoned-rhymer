(ns reasoned-rhymer.wit
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [goog.events :as events]
            [cljs.core.async :refer [<! >! put! take! chan]]
            [cljs.reader :refer [read-string]]
            [dommy.core :as dom]
            [ajax.core :refer [GET POST]]
            [clojure.string :as str]
            [om.core :as om]
            [om.dom :as d]
            [reasoned-rhymer.components :as c]))

(defn process-result [intent entities]
  (println "process-result")
  (println intent)
  (println entities) "tet")

(defn microphone [data owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [node (om/get-node owner)
            mic (js/Wit.Microphone. node)]
        (set! (.-onready mic) #(om/update! (om/get-props owner)
                                           [:wit :mic] "Microphone is ready to record"))
        (set! (.-onaudiostart mic) #(om/update! (om/get-props owner)
                                                [:wit :mic] "Recording started"))
        (set! (.-onaudioend mic) #(om/update! (om/get-props owner)
                                              [:wit :mic] "Recording stopped, processing started"))
        (set! (.-onerror mic) #(om/update! (om/get-props owner)
                                              [:wit :mic] (str "Error: " %)))
        (set! (.-onresult mic) process-result)
        (.connect mic "PH72PLCJ3XUCHVNVQP726HSIODKVNKON")))
    om/IRender
    (render [this]
      (d/div #js {:id "microphone"} ""))))

(defn wit-handler [data resp]
  (when (= (get-in resp ["outcome" "intent"]) "get_analysis")
    (let [song (get-in resp ["outcome" "entities" "song_to_grab" "value"])]
      (do (c/get-analysis song #(c/update-analysis % data))
          (om/update! data [:wit :info]
                      (str "Ok, we will get the analysis for " song))))))

(defn nlp-comp [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:get (chan) :text "" :is-getting false})
    om/IWillMount
    (will-mount [_]
      (let [get-ch (om/get-state owner :get)]
        (go-loop []
          (let [getting (<! get-ch)]
            (om/set-state! owner :is-getting true)
            (GET "https://api.wit.ai/message"
                  {:params {:q (om/get-state owner :text)}
                   :headers {:Authorization (str "Bearer " js/bearer)}
                   :handler (partial wit-handler data)}))
          (recur))))
    om/IRender
    (render [this]
      (d/form #js {:id "wit-text-form" :className "form-info form-inline"
                   :onSubmit (fn [e] (put! (om/get-state owner :get) 1) false)}
        "Type natural language: "
        (d/div #js {:className "form-group"}
          (d/input #js {:id "poem-title" :type "text" :className "form-control"
                        :placeholder "What to ask wit"
                        :value (om/get-state owner :text)
                        :onChange #(c/handle-change % owner :text)}))
        (d/span nil " ")
        (d/div #js {:className "form-group"}
          (d/button #js {:type "submit" :className "btn btn-info"}
            "Ask Wit"))))))

(defn wit [data owner]
  (reify
    om/IRender
    (render [this]
      (d/div nil
;;  Microphone is acting up on my machine.
;;  Can't even get the basic js demos working
;;         (om/build microphone data)
;;         (d/div #js {:id "mic-info"} (get-in data [:wit :mic]))
;;         (d/br nil)
        (om/build nlp-comp data)
        (d/br nil)
        (d/pre #js {:id "result"} (get-in data [:wit :info]))
        (d/hr nil)))))

(defn app [data owner opts]
  (reify
    om/IRender
    (render [this]
      (d/div #js {:className "row"}
        (om/build c/header data)
        (om/build wit data)
        (cond
           (= (get-in data [:viewing]) :get-analysis) (om/build c/get-view data)
           (= (get-in data [:viewing]) :post-analysis) (om/build c/post-view data)
           :else (d/h2 nil "No View Hooked Up"))))))

;; (:wit @reasoned-rhymer.rhymer/app-state)
