(ns lazybot.plugins.google
  (:require [lazybot.registry :refer [defplugin send-message]]
            [lazybot.utilities :refer [trim-string]]
            [cheshire.core :refer [parse-string]] 
            [clojure.string :as s]
            [clj-http.client :as http]
            [clojure.math.numeric-tower :refer [abs]])
  (:import org.apache.commons.lang.StringEscapeUtils
           java.net.URLDecoder
           java.text.NumberFormat
           java.util.Locale))

(defn commify
  ([n] (commify n (Locale/US)))
  ([n locale]
   (.format (NumberFormat/getInstance locale) (bigdec n))))

(defn google [service term]
  "Services: \"web\", \"images\""
  (-> (http/get (str "http://ajax.googleapis.com/ajax/services/search/"
                     service)
                {:query-params {"v"  "1.0"
                                "rsz" 8 ; 8 results
                                "q"   term}})
      :body
      parse-string))

(defn google-autocomplete [term]
  (-> (http/get "http://google.com/complete/search"
                {:query-params {"q" term
                                "client" "firefox"}})
      :body
      parse-string
      second))

(defn search-string
  "From an argument list, builds a search string."
  [args]
  (->> args
       (s/join " ")
       s/trim))

(defn handle-search [com-m]
  (send-message com-m
                (let [q (search-string (:args com-m))]
                  (if-not (seq q)
                    (str "No search terms!")
                    (let [results (google "web" q)
                          {:strs [url titleNoFormatting]}
                            (first (get-in results ["responseData" "results"]))
                          res-count (get-in results ["responseData"
                                                     "cursor"
                                                     "estimatedResultCount"])]
                      (if (and results url)
                        (str "["
                             (trim-string 80 (constantly "...")
                                          (StringEscapeUtils/unescapeHtml
                                            titleNoFormatting))
                             "] "
                             (URLDecoder/decode url "UTF-8"))))))))

(defn handle-image-search
  "Finds a random google image for a string, and responds with the URI."
  [com-m]
  (send-message com-m
                (let [q (search-string (:args com-m))]
                  (if-not (seq q)
                    (str "No search terms!")
                    (-> (google "images" q)
                        (get "responseData")
                        (get "results")
                        rand-nth
                        (get "url")
                        (URLDecoder/decode "UTF-8"))))))

(defn handle-autocomplete
  "Returns a list of autocompletions for a search phrase"
  [com-m]
  (let [results (google-autocomplete (search-string (:args com-m)))]
    (doseq [result results]
        (send-message com-m result))))

(defn handle-googlefight
  "Compares result count between two search terms"
  [com-m]
  (let [[_ term1 term2] (re-find #"(.+)\s?\/\s?(.+)" (s/join " " (:args com-m)))]
    (when (and (seq term1) (seq term2))
      (let [result1 (google "web" (search-string term1))
            result2 (google "web" (search-string term2))
            res1-count (read-string (get-in result1 ["responseData"
                                        "cursor"
                                        "estimatedResultCount"]))
            res2-count (read-string (get-in result2 ["responseData"
                                        "cursor"
                                        "estimatedResultCount"]))
            result-difference (abs (- res1-count res2-count))]
        (send-message com-m
                      (str term1 ": " (commify res1-count)))
        (send-message com-m
                      (str term2 ": " (commify res2-count)))
        (if (= res1-count res2-count)
          (send-message com-m "It's a tie!")
          (if (> res1-count res2-count)
            (send-message com-m (str term1 " wins with a difference of " (commify result-difference) "!"))
            (send-message com-m (str term2 " wins with a difference of " (commify result-difference) "!"))))))))

(defplugin
  (:cmd
   "Searches google for whatever you ask it to, and displays the first result
   and the estimated number of results found."
   #{"google"}
   #'handle-search)

  (:cmd
    "Searches google for a string, and returns a random image from the results."
    #{"gis"}
    #'handle-image-search)

  (:cmd
   "Returns a list of autocompletions for a search phrase"
   #{"gac"}
   #'handle-autocomplete)

  (:cmd
   "GOOGLEFIGHT!"
   #{"googlefight"}
   #'handle-googlefight)

  (:cmd
   "Searches wikipedia via google."
   #{"wiki"}
   (fn [args]
     (handle-search (assoc args :args (conj (:args args) "site:en.wikipedia.org")))))

  (:cmd
   "Searches encyclopediadramtica via google."
   #{"ed"}
   (fn [args]
     (handle-search (assoc args :args (conj (:args args) "site:encyclopediadramatica.com"))))))
