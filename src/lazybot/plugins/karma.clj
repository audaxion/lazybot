; Written by Michael D. Ivey <ivey@gweezlebur.com>
; Licensed under the EPL

(ns lazybot.plugins.karma
  (:use [lazybot registry info]
        [useful.map :only [keyed]]
        [somnium.congomongo :only [fetch-one fetch insert! update! fetch-and-modify]])
  (:import (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

(def inc-responses
  ["+1!" "gained a level!" "is on the rise!" "leveled up!" "hrheehrhen." "BOOM!"])

(def dec-responses
  ["took a hit! Ouch." "took a dive." "lost a life." "lost a level." "pwned." "O RLY?"])

(defn- key-attrs [nick server channel]
  (let [nick (.toLowerCase nick)]
    (keyed [nick server channel])))

(defn- set-karma
  [nick server channel karma]
  (let [attrs (key-attrs nick server channel)]
    (update! :karma attrs (assoc attrs :karma karma))))

(defn- inc-karma
  [nick server channel bot]
  (let [attrs (key-attrs nick server channel)
        karma-object (fetch-and-modify :karma attrs {:$inc {:karma 1}} :only [:karma] :upsert? true :return-new? true)]
    (str nick " " (rand-nth inc-responses) " (Karma " (get-in @bot [:config :prefix-arrow]) (:karma karma-object) ")")))

(defn- dec-karma
  [nick server channel bot]
  (let [attrs (key-attrs nick server channel)
        karma-object (fetch-and-modify :karma attrs {:$inc {:karma -1}} :only [:karma] :upsert? true :return-new? true)]
    (str nick " " (rand-nth dec-responses) " (Karma " (get-in @bot [:config :prefix-arrow]) (:karma karma-object) ")")))

(defn- get-karma
  [nick server channel]
  (let [user-map (fetch-one :karma
                            :where (key-attrs nick server channel))]
    (get user-map :karma 0)))

(defn- get-sorted-karma
  [server channel dir]
  (fetch :karma :where (keyed [server channel]) :sort {:karma dir} :limit 10))

(def limit (ref {}))

(let [scheduler (Executors/newScheduledThreadPool 1)]
  (defn schedule [^Runnable task]
    (.schedule scheduler task
               5 TimeUnit/MINUTES)))

;; TODO: mongo has atomic inc/dec commands - we should use those
(defn- change-karma
  [snick new-karma {:keys [^String nick com bot channel] :as com-m}]
  (let [[msg apply]
        (dosync
         (let [current (get-in @limit [nick snick])]
           (cond
            (.equalsIgnoreCase nick snick) ["You can't adjust your own karma."]
            (= current 5) ["Do I smell abuse? Wait a while before modifying that person's karma again."]
            (= current new-karma) ["You want me to leave karma the same? Fine I will."]
            :else [(str (get-in @bot [:config :prefix-arrow]) new-karma)
                   (alter limit update-in [nick snick] (fnil inc 0))])))]
    (when apply
      (set-karma snick (:network @com) channel new-karma)
      (schedule #(dosync (alter limit update-in [nick snick] dec))))
    (send-message com-m msg)))

(defn karma-fn
  "Create a plugin command function that applies f to the karma of the user specified in args."
  [f]
  (fn [{:keys [com bot channel args] :as com-m}]
    (let [snick (first args)
          msg (f snick (:network @com) channel bot)]
      (send-message com-m msg))))

(defn print-karma-list
  "Print list of users with karma"
  [users dir com-m]
  (send-message com-m (str dir " scores:"))
  (doseq [user users]
    (send-message com-m (str (:nick user) ": " (:karma user)))))

(def print-karma
  (fn [{:keys [com bot channel args] :as com-m}]
    (let [nick (first args)]
      (case nick
        "--high" (print-karma-list (get-sorted-karma (:network @com) channel -1) "High" com-m)
        "--low" (print-karma-list (get-sorted-karma (:network @com) channel 1) "Low" com-m)
        nil (print-karma-list (get-sorted-karma (:network @com) channel -1) "High" com-m)
        :default (send-message com-m
                    (if-let [karma (get-karma nick (:network @com) channel)]
                      (str nick " has " karma "points.")
                      (str "I have no record for " nick ".")))))))

(defplugin
  (:hook :on-message
         (fn [{:keys [message] :as com-m}]
           (let [[_ direction snick] (re-find #"^\((inc|dec|identity) (.+)\)(\s*;.*)?$" message)]
             (when snick
               ((case direction
                  "inc" (karma-fn inc-karma)
                  "dec" (karma-fn dec-karma)
                  "identity" print-karma)
                (merge com-m {:args [snick]}))))))

  (:hook :on-message
   (fn [{:keys [message] :as com-m}]
     (let [[_ snick direction] (re-find #"(\S+[^+\s])(\+\+|--)(\s*;.*)?$" message)]
       (when snick
         ((case direction
            "++" (karma-fn inc-karma)
            "--" (karma-fn dec-karma))
          (merge com-m {:args [snick]}))))))

  (:cmd
   "Checks the karma of the person you specify."
   #{"karma" "identity" "score"} print-karma)
  (:cmd
   "Increases the karma of the person you specify."
   #{"inc"} (karma-fn inc-karma))
  (:cmd
   "Decreases the karma of the person you specify."
   #{"dec"} (karma-fn dec-karma))
  (:indexes [[:server :channel :nick]]))
