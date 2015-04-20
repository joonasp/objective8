(ns objective8.diff
  (:require [diff-match-patch-clj.core :as dmp]
            [net.cgrand.enlive-html :as html]
            [cljsoup.core :as soup-core]
            [cljsoup.nodes :as soup-nodes]
            [jsoup.soup :as jsoup]
            [objective8.utils :as utils]
            [objective8.drafts :as drafts]))

(defn get-delete [diff]
  (when (= "DELETE" (str (. diff operation)))
    (. diff text)))

(defn get-insert [diff]
  (when (= "INSERT" (str (. diff operation)))
    (. diff text)))

(defn get-third-element-if-exists [elements]
  (if (> (count elements) 2) (nth elements 2) " "))

(defn get-text [hiccup-draft]
  (clojure.string/join (map get-third-element-if-exists hiccup-draft)))

(defn remove-tags [hiccup-draft]
  (let [html-draft (utils/hiccup->html hiccup-draft)
        parsed-draft (soup-core/from-string html-draft)
        body (soup-nodes/body parsed-draft)]
    (prn parsed-draft)
    (prn "---------------------------------")
    (prn body)
    (prn "***********************")
    (prn (soup-nodes/select parsed-draft "body > p"))
    (prn "$$$$$$$$$$$$$$$$$$$$$$$$$$$")
    (prn (html/text (soup-nodes/select parsed-draft "body > p")))))

(defn get-hiccup-draft [draft-id] 
  (apply list (:content (drafts/retrieve-draft draft-id))))

(defn get-all-views []
  (let [draft-1 (get-hiccup-draft 31572)
        draft-2 (get-hiccup-draft 31573)
        diffs (dmp/cleanup! (dmp/diff (get-text draft-1) (get-text draft-2)))]
    (remove-tags draft-1)
    {:first-draft draft-1
     :diff (dmp/as-hiccup diffs) 
     :second-draft draft-2}))



(defn get-diff []
  (let [draft-1 (get-hiccup-draft 31572)
        draft-2 (get-hiccup-draft 31573)
        diffs (dmp/cleanup! (dmp/diff (get-text draft-1) (get-text draft-2)))
        inserts (remove nil? (map get-insert diffs))
        deletes (remove nil? (map get-delete diffs))]
    {:inserts inserts 
     :deletes deletes  
     :second-draft draft-2}))

