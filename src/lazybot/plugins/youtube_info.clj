(ns lazybot.plugins.youtube-info
  (:use [lazybot registry info])
  (:require [cheshire.core :refer [parse-string]]
            [clj-http.client :as http]))

(def youtube-query-url "http://gdata.youtube.com/feeds/api/videos/")

(defn get-video-info [video-hash]
  (let [url (str youtube-query-url video-hash "?alt=json")
        res (http/get url)
        body (parse-string (:body res) (fn [k] (keyword k)))]
    (:entry body)))

(defn video-info [video]
  (let [title (:$t (:title video))
        time (Integer/parseInt (:seconds (:yt$duration (:media$group video))))]
    (str "Youtube: " title " (" (quot time 60) "m" (rem time 60) "s)")))

(defplugin
  (:hook
   :on-message
   (fn [{:keys [message nick bot com] :as com-m}]
     (let [[_ url video-hash] (re-find #"https?:\/\/(www\.youtube\.com\/watch\?v=|youtu\.be\/)(.+?)(?:\s|$)" message)]
       (when (and video-hash (not (is-command? message (:prepends (:config @bot)))))
         (send-message com-m (video-info (get-video-info video-hash))))))))