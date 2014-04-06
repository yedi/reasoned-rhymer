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
                              (update-analysis resp data)
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

(def COLORS [     "1CE6FF", "FF34FF", "FF4A46", "008941", "006FA6", "A30059",
        "FFDBE5", "7A4900", "0000A6", "63FFAC", "B79762", "004D43", "8FB0FF", "997D87",
        "5A0007", "809693", "1B4400", "4FC601", "3B5DFF", "4A3B53", "FF2F80",
        "61615A", "BA0900", "6B7900", "00C2A0", "FFAA92", "FF90C9", "B903AA", "D16100",
        "DDEFFF", "7B4F4B", "A1C299", "300018", "0AA6D8", "00846F",
        "372101", "FFB500", "C2FFED", "A079BF", "CC0744", "C0B9B2", "C2FF99", "001E09",
        "00489C", "6F0062", "0CBD66", "EEC3FF", "456D75", "B77B68", "7A87A1", "788D66",
        "885578", "FAD09F", "FF8A9A", "D157A0", "BEC459", "456648", "0086ED", "886F4C",

        "34362D", "B4A8BD", "00A6AA", "452C2C", "636375", "A3C8C9", "FF913F", "938A81",
        "575329", "00FECF", "B05B6F", "8CD0FF", "3B9700", "04F757", "C8A1A1", "1E6E00",
        "7900D7", "A77500", "6367A9", "A05837", "6B002C", "772600", "D790FF", "9B9700",
        "549E79", "72418F", "BC23FF", "99ADC0", "3A2465", "922329",
        "5B4534", "FDE8DC", "404E55", "0089A3", "CB7E98", "A4E804", "324E72", "6A3A4C",
        "83AB58", "001C1E", "D1F7CE", "004B28", "C8D0F6", "A3A489", "806C66",
        "BF5650", "E83000", "66796D", "DA007C", "FF1A59", "8ADBB4", "1E0200", "5B4E51",
        "C895C5", "FF6832", "66E1D3", "CFCDAC", "D0AC94", "7ED379", "012C58"])

(defn brs [text]
  (take (count (filter #{\newline} text))
        (repeatedly (partial d/br nil))))

(defn word-span [idx word wmap data]
  (let [matching-id (first (get wmap idx))
        color (if matching-id (nth (cycle COLORS) matching-id) "black")
        font-weight (if matching-id  "bold" "normal")]
    (d/a #js {:style #js {:color color :font-weight font-weight}
              :data-match matching-id :data-id idx}
         word)))

(defn text-spans [{:keys [text words] :as data}]
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
                     (conj spans (word-span idx (first tokens) words data)))))))

(defn text-view [data owner]
  (reify
    om/IRender
    (render [this]
      (apply d/div #js {:className "col-xs-6"}
        (text-spans data)))))

;; ======================================================================
;; TODO: Replace with rhyme-finder equivalents when cljx support is added

(defn compress [coll]
  (when-let [[f & r] (seq coll)]
    (if (= f (first r))
      (compress r)
      (cons f (compress r)))))

(defn rstreams->words [rstreams]
  ; #todo replace with the rhyme-finder version when cljx support is added
  "streams are a phone/word combination
   rstreams are a list of streams
   ([{:index 88, :phone 'eh', :word 'there'}
     {:index 89, :phone 'ow', :word 'goes'}
     {:index 90, :phone 'ae', :word 'gravity'}
     {:index 90, :phone 'ah', :word 'gravity'}
     {:index 90, :phone 'iy', :word 'gravity'}]
    [{:index 93, :phone 'eh', :word 'there'}
     {:index 94, :phone 'ow', :word 'goes'}
     {:index 95, :phone 'ae', :word 'rabbit'}
     {:index 95, :phone 'ah', :word 'rabbit'}
     {:index 97, :phone 'iy', :word 'he'}]) =>
   ('there goes gravity' 'there goes rabbit he')"
  (map (fn [rstream] (str/join " " (compress (map :word rstream))))
       rstreams))

;; ======================================================================


(defn combo-view [data owner]
  (reify
    om/IRender
    (render [this]
      (apply d/div nil
        (d/strong nil (str/join "-" (:value data)))
        (let [all-streams (nth (:streams data) 0)
              streams (if (:extended data) all-streams (take 3 all-streams))
              stream-divs (map #(d/div nil %) (rstreams->words streams))]
          (if (and (< 3 (count all-streams)) (not (:extended data)))
            (concat stream-divs
              [(d/a #js {:onClick #(om/update! data [:extended] true) :href "#"}
                 (d/small nil "more..."))])
            stream-divs))))))

(defn combos-view [data owner]
  (reify
    om/IRender
    (render [this]
      (apply d/div #js {:className "col-xs-6"}
        (om/build-all combo-view (get-in data [:analysis]))))))

(defn analysis-view [data owner]
  (reify
    om/IRender
    (render [this]
      (d/div #js {:className "row"}
        (om/build text-view data)
        (om/build combos-view data)))))

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
  (let [resp (update-in resp [:analysis] (partial into []))
        wmap (word=>combo (:analysis resp))]
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

(nth (get-in @app-state [:analysis :analysis]) 19)

(start (sel1 :#app) app-state)
















