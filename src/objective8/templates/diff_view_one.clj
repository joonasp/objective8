(ns objective8.templates.diff-view-one
  (:require [net.cgrand.enlive-html :as html]
            [objective8.templates.page-furniture :as pf]
            [objective8.templates.template-functions :as tf])) 

(def diff-view-one (html/html-resource "templates/jade/diff-view-one.html"))

(defn diff-view-one-page [{:keys [data] :as context}]
  (apply str
         (html/emit*
           (html/at diff-view-one
                    [:.first-draft] (html/html-content (:first-draft data))
                    [:.diff] (html/html-content (:diff data))
                    [:.second-draft] (html/html-content (:second-draft data))))))
