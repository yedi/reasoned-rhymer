(ns reasoned-rhymer.components
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [goog.events :as events]
            [cljs.core.async :refer [<! >! put! take! chan]]
            [cljs.reader :refer [read-string]]
            [dommy.core :as dom]
            [ajax.core :refer [GET POST]]
            [clojure.string :as str]
            [om.core :as om]
            [om.dom :as d]))


(defn a- [func]
  (fn [] (func) false))

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
               "Analyze Song"))
          (d/li nil
            (d/a #js {:href "https://github.com/yedi/reasoned-rhymer" :target "_blank"}
              "View on Github")))
        (d/h3 #js {:className "text-muted"} "Reasoned Rhymer")))))

(defn handle-change [e owner field]
  (om/set-state! owner field (.. e -target -value)))

(defn post-view [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:post (chan) :title "" :text "" :is-posting false})
    om/IWillMount
    (will-mount [_]
      (let [post-ch (om/get-state owner :post)]
        (go-loop []
          (let [posting (<! post-ch)]
            (om/set-state! owner :is-posting true)
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
      (if (om/get-state owner :is-posting)
        (d/div nil
          (d/h4 nil "Loading... Please wait.")
          "Your song is currently be analyzed, this make take a while.
           Feel free to browse some other analyses while you wait.
           Once the analyzing is complete, the analysis for this song will be shown.")
        (d/div #js {:id "add-poem" :className "nav-section col-md-12"}
          (d/form #js {:id "add-poem-form" :className "form-info form-horizontal"}
            (d/div #js {:className "form-group"}
              (d/button #js {:type "button" :className "btn btn-info"
                             :onClick (fn [e] (put!(om/get-state owner :post) 1) false)}
                 "Analyze poem or song"))
            (d/div #js {:className "form-group"}
              (d/input #js {:id "poem-title" :type "text" :className "form-control"
                            :placeholder "Name of poem or song"
                            :value (om/get-state owner :title)
                            :onChange #(handle-change % owner :title)}))
             (d/div #js {:className "form-group"}
               (d/textarea #js {:id "poem-text" :rows 16 :className "form-control"
                               :placeholder "Lyrics go here"
                               :value (om/get-state owner :text)
                               :onChange #(handle-change % owner :text)}))
             (d/p #js {:className "text-warning"}
               (d/strong nil "Note: ")
               "Some slang words are not currently supported because they are missing from the dictionary.
               This could result in some missing rhyme combos")))))))

(def COLORS [     "1CE6FF", "FF34FF", "FF4A46", "008941", "006FA6", "A30059",
        "FFDBE5", "7A4900", "0000A6", "63FFAC", "B79762", "004D43", "8FB0FF", "997D87",
        "5A0007", "809693", "1B4400", "4FC601", "3B5DFF", "4A3B53", "FF2F80",
        "BA0900", "6B7900", "00C2A0", "FFAA92", "FF90C9", "B903AA", "D16100",
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

(defn word-span [idx word wmap data words-ch]
  (let [matching-ids (get wmap idx)
        pred (if-not (empty? (:combos data)) (into #{} (:combos data)) identity)
        matching-id (first (filter pred matching-ids))
        color (if matching-id (str "#" (nth (cycle COLORS) matching-id)) "black")
        font-weight (if matching-id  "bold" "normal")]
    (d/a #js {:style #js {:color color :font-weight font-weight}
              :data-match matching-id :react-key (str "word-" idx) :href "#"
              :onClick (a- #(put! words-ch [:add idx]))}
         word)))

(defn text-spans [{:keys [text words] :as data} words-ch]
  (let [tokens (map (partial apply str)
                    (partition-by #{\space \tab \newline} text))]
    (loop [tokens tokens
           idx 0
           spans '()]
      (cond
        (empty? tokens) (reverse spans)
        (re-matches #"\s*[-_]*" (first tokens))
          (recur (rest tokens)
                 idx
                 (apply (partial conj spans)
                        (if (empty? (filter #{\newline} (first tokens)))
                          (list (d/span nil (first tokens)))
                          (brs (first tokens)))))  ; handle line breaks
        :else (recur (rest tokens)
                     (inc idx)
                     (conj spans (word-span idx (first tokens) words data words-ch)))))))

(defn combo-slugs [idxs analysis combo-ch]
  (map (fn [idx] (d/a #js {:style #js {:color (str "#" (nth (cycle COLORS) idx))}
                           :onClick (a- #(put! combo-ch [:remove idx])) :href "#" }
                      (str (str/join "-" (:value (get analysis idx))) " ")))
       idxs))

(defn text-view [data owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [combo-ch words-ch]}]
      (d/div #js {:className "col-xs-6"}
        (let [viewing (if-not (empty? (:combos data))
                        (combo-slugs (:combos data) (:analysis data) combo-ch)
                        [(d/span nil "All")])]
          (apply d/h5 nil (d/span nil "Viewing: ") viewing))
        (apply d/div nil (text-spans data words-ch))))))

;; ======================================================================
;; TODO: Replace with rhyme-finder equivalents when cljx support is added

(defn compress [coll]
  (when-let [[f & r] (seq coll)]
    (if (= f (first r))
      (compress r)
      (cons f (compress r)))))

(defn rstreams->words [rstreams]
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

(defn word-slugs [idxs text words-ch]
  (map (fn [idx]
         (d/a #js {:onClick (a- #(put! words-ch [:remove idx])) :href "#"}
              (str (nth (remove str/blank? (str/split text #"\s+")) idx) " ")))
       idxs))

(defn combo-view [data owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [combo-ch idx]}]
      (let [all-streams (apply concat (:streams data))
            streams (if (:extended data) all-streams (take 3 all-streams))
            stream-divs (map #(d/div nil %) (rstreams->words streams))]
        (apply d/div nil
          (d/a #js {:onClick (a- #(put! combo-ch [:add idx])) :href "#"}
               (str (str/join "-" (:value data)) " (" (count all-streams) ")"))
            (if (and (< 3 (count all-streams)) (not (:extended data)))
              (concat stream-divs
                [(d/a #js {:onClick (a- #(om/update! data [:extended] true)) :href "#"}
                   (d/small nil "more..."))])
              stream-divs))))))

(defn check-words [idx {:keys [words cur-words]}]
  (let [select-values (comp vals select-keys)
        combo-ids (into #{} (flatten (select-values words cur-words)))]
    (combo-ids idx)))

(defn combos-view [data owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [words-ch] :as state}]
      (let [viewing (if-not (empty? (:cur-words data))
                      (word-slugs (:cur-words data) (:text data) words-ch)
                      [(d/span nil "All")])
            pred (if-not (empty? (:cur-words data))
                   (fn [[idx _]] (check-words idx data))
                   identity)]
        (apply d/div #js {:className "col-xs-6"}
          (apply d/h5 nil (d/span nil "Viewing: ") viewing)
          (map (fn [[idx combo]]
                 (om/build combo-view combo {:state (assoc state :idx idx)}))
               (filter pred (map vector (range) (get-in data [:analysis])))))))))

(defn analysis-view [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:combo-ch (chan) :words-ch (chan)})
    om/IWillMount
    (will-mount [_]
      (let [combo-ch (om/get-state owner :combo-ch)
            words-ch (om/get-state owner :words-ch)]
        (go-loop []
          (let [[action combo] (<! combo-ch)]
            (print (str action "ing combo: " combo))
            (if (= action :add)
              (om/transact! (om/get-props owner) :combos #(conj % combo))
              (om/transact! (om/get-props owner) :combos (partial remove #{combo}))))
          (recur))
        (go-loop []
          (let [[action word] (<! words-ch)]
            (print (str action "ing word: " word))
            (if (= action :add)
              (om/transact! (om/get-props owner) :cur-words #(conj % word))
              (om/transact! (om/get-props owner) :cur-words (partial remove #{word}))))
          (recur))))
    om/IRenderState
    (render-state [this state]
      (d/div #js {:className "row"}
        (om/build text-view data {:state state})
        (om/build combos-view data {:state state})))))

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

(defn get-analysis [title callback]
  (print (str "Retrieving: " title))
  (GET "/analysis"
       {:params {:title title}
        :handler callback}))

(defn get-view [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:get (chan) :selected (or (get-in data [:analysis :title])
                                 (first (:titles data)))
       :is-loading false})
    om/IDidUpdate
    (did-update [this prev-props _]
      (when-not (= (get-in data [:analysis :title])
                   (get-in prev-props [:analysis :title]))
        (om/set-state! owner :selected (or (get-in data [:analysis :title])
                                           (first (:titles data))))))
    om/IWillMount
    (will-mount [_]
      (let [get-ch (om/get-state owner :get)]
        (go-loop []
          (let [getting (<! get-ch)]
            (om/set-state! owner :is-loading true)
            (get-analysis (om/get-state owner :selected)
                          #(do (update-analysis % data)
                               (om/set-state! owner :is-loading false))))
          (recur))))
    om/IRender
    (render [this]
      (let [selected (om/get-state owner :selected)
            get-ch (om/get-state owner :get)]
        (d/div #js {:id "view-analyses" :className "nav-section"}
          (d/form #js {:id "get-analysis-form" :className "form-inline"}
            (apply d/select #js {:className "form-control" :id "select-title" :value selected
                                 :onChange #(handle-change % owner :selected)}
              (map #(d/option nil %) (:titles data)))
            " "  ; needed because bootstrap is silly
            (d/button #js {:type "button" :className "btn btn-info"
                           :onClick (fn [e] (put! get-ch selected) false)
                           :disabled (when (om/get-state owner :is-loading) true)}
              (if (om/get-state owner :is-loading) "Loading Analysis..." "See Analysis")))
          (if (:analysis data)
            (om/build analysis-view (:analysis data))
            (d/h4 nil "Select a song to load it's rhyme analysis")))))))


