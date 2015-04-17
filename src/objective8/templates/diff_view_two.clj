(ns objective8.templates.diff-view-two 
  (:require [net.cgrand.enlive-html :as html]))

(def diff-view-two (html/html-resource "templates/jade/diff-view-two.html"))
(def insert-item-snippet (html/select diff-view-two [:.insert-item]))
(def delete-item-snippet (html/select diff-view-two [:.delete-item]))

(defn inserts [inserts]
  (html/at insert-item-snippet
           [:.insert-item]
           (html/clone-for [insert inserts]
                           [:.insert-item-text] (html/content (str insert)))))

(defn deletes [deletes]
  (html/at delete-item-snippet
           [:.delete-item]
           (html/clone-for [delete deletes]
                           [:.delete-item-text] (html/content (str delete)))))

(defn diff-view-two-page [{:keys [data] :as context}]
  (apply str
         (html/emit*
           (html/at diff-view-two
                    [:.inserts-title] (html/content "Inserts")
                    [:.inserts] (html/content (inserts (:inserts data)))
                    [:.deletes-title] (html/content "Deletes")
                    [:.deletes] (html/content (deletes (:deletes data)))
                    [:.second-draft] (html/html-content (:second-draft data))))))
