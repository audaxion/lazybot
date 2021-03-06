;; The result of a team effort between programble and Rayne.
(ns lazybot.plugins.title
  (:use [lazybot info registry utilities]
        [clojure.java.io :only [reader]]
        [clojure.string :only [triml]]
        [clojure.tools.logging :only [debug]]
        [clojail.core :only [thunk-timeout]])
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :refer [parse-string]]
            [clj-http.client :as http])
  (:import java.util.concurrent.TimeoutException
           java.net.URL
           org.apache.commons.lang.StringEscapeUtils))

(def titlere #"(?i)<title>([^<]+)</title>")

(def pagesynopsis-url "http://www.pagesynopsis.com/pageinfo?targetUrl=")

(defn collapse-whitespace [s]
  (->> s (.split #"\s+") (interpose " ") (apply str)))

(defn add-url-prefix [url]
  (if-not (.startsWith url "http")
    (str "http://" url)
    url))

(defn fetch-url [url]
  (try
    (parse-string (:body (http/get (str pagesynopsis-url url))) (fn [k] (keyword k)))
  (catch java.lang.Exception e nil)))

(defn get-title [url]
  (try
    (map html/text (html/select (fetch-url url) [:title]))
  (catch java.lang.Exception e nil)))

(defn get-description [url]
  (try
    (html/select (fetch-url url) [:head [:meta (html/attr-has :name "description")]])
  (catch java.lang.Exception e nil)))

(defn slurp-or-default [url]
  (try
   (with-open [readerurl (reader url)]
     (loop [acc [] lines (line-seq readerurl)]
       (cond
        (not (seq lines)) nil
        (some #(re-find #"</title>|</TITLE>" %) acc) (->> acc (apply str)
                                                          (#(.replace % "\n" " "))
                                                          (re-find titlere))
        :else (recur (conj acc (first lines)) (rest lines)))))
   (catch java.lang.Exception e nil)))

(defn url-blacklist-words [com bot] (:url-blacklist ((:config @bot) (:network @com))))

(defn url-check [com bot url]
  (some #(.contains url %) (url-blacklist-words com bot)))

(defn strip-tilde [s] (apply str (remove #{\~} s)))

(defn title [{:keys [com nick bot user channel] :as com-m}
             links & {verbose? :verbose?}]
  (if (or (and verbose? (seq links))
          (not (contains? (get-in @bot [:config (:network @com) :title :blacklist])
                          channel)))
    (doseq [link (take 1 links)]
      (try
       (thunk-timeout #(let [url (add-url-prefix link)
                             page (fetch-url url)
                             title (:pageTitle page)
                             description (:pageDescription page)]
                         (if (and (seq page) (seq title) (not (url-check com bot url)))
                           (send-message com-m
                                              (str "\""
                                                   (triml
                                                    (StringEscapeUtils/unescapeHtml
                                                     (collapse-whitespace title)))
                                                   "\""))

                           (when verbose? (send-message com-m "Page has no title.")))
                         (if (seq description)
                           (send-message com-m
                                         (str "\""
                                              (triml
                                                (StringEscapeUtils/unescapeHtml
                                                  (collapse-whitespace description)))
                                              "\""))))
                      20 :sec)
       (catch TimeoutException _
         (when verbose?
           (send-message com-m "It's taking too long to find the title. I'm giving up.")))))
    (when verbose? (send-message com-m "Which page?"))))

(defplugin
  (:hook
   :on-message
   (fn [{:keys [com bot nick channel message] :as com-m}]
     (let [info (:config @bot)
           get-links (fn [s]
                       (->> s
                            (re-seq #"(https?://|www\.)[^\]\[(){}\"'$^\s]+")
                            (map first)))]
       (let [prepend (:prepends info)
             links (get-links message)
             title-links? (and (not (is-command? message prepend))
                               (get-in info [(:network @com) :title :automatic?])
                               (seq links))]
         (when title-links?
           (title com-m links))))))

  (:cmd
   "Gets the title of a web page. Takes a link. This is verbose, and prints error messages."
   #{"title"} (fn [com-m] (title com-m (:args com-m) :verbose? true))))
