(ns objective8.diff
  (:require [diff-match-patch-clj.core :as dmp]
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

(defn get-hiccup-draft [draft-id] 
  (apply list (:content (drafts/retrieve-draft draft-id))))

(defn get-all-views []
  (let [draft-1 (get-hiccup-draft 27741)
        draft-2 (get-hiccup-draft 27742)
        diffs (dmp/cleanup! (dmp/diff (get-text draft-1) (get-text draft-2)))]
    {:first-draft draft-1
     :diff (dmp/as-hiccup diffs) 
     :second-draft draft-2}))



(defn get-diff []
  (let [draft-1 (get-hiccup-draft 27741)
        draft-2 (get-hiccup-draft 27742)
        diffs (dmp/cleanup! (dmp/diff (get-text draft-1) (get-text draft-2)))
        inserts (remove nil? (map get-insert diffs))
        deletes (remove nil? (map get-delete diffs))]
    {:inserts inserts 
     :deletes deletes  
     :second-draft draft-2}))

