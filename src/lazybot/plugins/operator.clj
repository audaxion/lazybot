(ns lazybot.plugins.operator
  (:use lazybot.registry
        [lazybot.plugins.login :only [when-privs]])
  (:require [clojure.string :as string]
            [irclj.core :as ircb]
            [irclj.connection :as connection]))

(defplugin
  (:cmd
   "Sets the person you specify as operator. ADMIN ONLY."
   #{"op"}
   (fn [{:keys [com bot nick channel args] :as com-m}]
     (when-privs com-m :admin (ircb/mode com channel "+o" (first args)))))

  (:cmd
   "Deops the person you specify. ADMIN ONLY."
   #{"deop"}
   (fn [{:keys [com bot nick channel args] :as com-m}]
     (when-privs com-m :admin (ircb/mode com channel  "-o" (first args)))))

  (:cmd
   "Kicks the person you specify. ADMIN ONLY."
   #{"kick"}
   (fn [{:keys [com bot nick channel args] :as com-m}]
     (when-privs com-m :admin (ircb/kick com channel (first args) :reason (apply str (rest args))))))

  (:cmd
    "Set's the channel's topic."
    #{"settopic" "topic"}
    (fn [{:keys [com bot nick channel args] :as com-m}]
      (connection/write-irc-line com "TOPIC" channel
                                 (connection/end (string/join " " args)))))

  (:cmd
   "Ban's whatever you specify. ADMIN ONLY."
   #{"ban"}
   (fn [{:keys [com bot nick channel args] :as com-m}]
     (when-privs com-m :admin (ircb/mode com channel "+b" (first args)))))

  (:cmd
   "Unban whatever you specify. ADMIN ONLY."
   #{"unban"}
   (fn [{:keys [com bot nick channel args] :as com-m}]
     (when-privs com-m :admin (ircb/mode com channel "-b" (first args)))))

  (:cmd
   "Voices the person you specify. ADMIN 0NLY."
   #{"voice"}
   (fn [{:keys [com bot channel nick args] :as com-m}]
     (when-privs com-m :admin (ircb/mode com channel "+v" (first args)))))

  (:cmd
   "Devoices the person you specify. ADMIN ONLY."
   #{"devoice"}
   (fn [{:keys [com bot channel nick args] :as com-m}]
     (when-privs com-m :admin (ircb/mode com channel "-v" (first args))))))
