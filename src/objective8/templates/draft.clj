(ns objective8.templates.draft
  (:require [net.cgrand.enlive-html :as html]
            [net.cgrand.jsoup :as jsoup]
            [objective8.templates.template-functions :as tf]
            [objective8.templates.page-furniture :as pf]
            [objective8.utils :as utils]
            [objective8.permissions :as permissions]))

(def draft-template (html/html-resource "templates/jade/draft.html" {:parser jsoup/parser}))

(def draft-version-navigation-snippet (html/select draft-template [:.clj-draft-version-navigation]))

(defn draft-version-navigation [{:keys [data] :as context}]
  (let [draft (:draft data)
        objective-id (:objective-id draft)
        previous-id (:previous-draft-id draft)
        next-id (:next-draft-id draft)] 
    (html/at draft-version-navigation-snippet
             [:.clj-draft-version-writer-author] (html/content (:username draft))
             [:.clj-draft-version-time] (html/content (utils/iso-time-string->pretty-time (:_created_at draft)))
             [:.clj-draft-version-navigation-previous] 
             (when previous-id
               (html/transformation
                 [:.clj-draft-version-previous-link] 
                 (html/set-attr :href
                                (utils/local-path-for :fe/draft :id objective-id
                                                      :d-id previous-id))))
             [:.clj-draft-version-navigation-next] 
             (when next-id
               (html/transformation
                 [:.clj-draft-version-next-link] 
                 (html/set-attr :href
                                (utils/local-path-for :fe/draft :id objective-id
                                                      :d-id next-id)))))))

(def no-drafts-snippet (html/select pf/library-html-resource [:.clj-no-drafts-yet]))

(def draft-wrapper-snippet (html/select draft-template [:.clj-draft-wrapper]))

(defn draft-wrapper [{:keys [data user] :as context}]
  (let [draft (:draft data)
        objective (:objective data)
        {objective-id :_id objective-status :status} objective
        optionally-disable-voting (if (tf/in-drafting? objective) 
                                    identity
                                    pf/disable-voting-actions)]
    (html/at draft-wrapper-snippet
             [:.clj-draft-version-navigation] (if draft
                                                (html/substitute (draft-version-navigation context))
                                                (html/content no-drafts-snippet))
             [:.clj-add-a-draft] (when (permissions/writer-for? user objective-id)
                                   (html/set-attr :href
                                                  (utils/local-path-for :fe/add-draft-get
                                                                        :id objective-id)))
             [:.clj-import-draft] (when (permissions/writer-for? user objective-id)
                                   (html/set-attr :href
                                                  (utils/local-path-for :fe/import-draft-get
                                                                        :id objective-id)))

             [:.clj-draft-preview-document] (when-let [draft-content (:draft-content data)] 
                                              (html/html-content draft-content)) 

             [:.clj-what-changed-link] (when (:previous-draft-id draft)
                                         (html/set-attr :href 
                                                        (utils/path-for :fe/draft-diff
                                                                        :id objective-id
                                                                        :d-id (:_id draft))))

             [:.clj-writer-item-list] (html/content (pf/writer-list context))
             [:.clj-draft-comments] (when draft
                                      (html/transformation
                                        [:.clj-comment-list] (html/content (optionally-disable-voting (pf/comment-list context)))
                                        [:.clj-comment-create] (html/content (pf/comment-create context :draft)))))))

(def drafting-begins-in-snippet (html/select pf/library-html-resource [:.clj-drafting-begins-in]))

(defn drafting-begins-in [{:keys [data] :as context}]
  (let [end-date (get-in data [:objective :end-date])
        drafting-begins-in-days (get-in data [:objective :days-until-drafting-begins])]
    (html/at drafting-begins-in-snippet
             [:.clj-drafting-begins-in] (html/set-attr :drafting-begins-date end-date)
             [:.clj-drafting-begins-in-days] (html/content (str drafting-begins-in-days)))))

(defn draft-page [{:keys [translations data doc] :as context}]
  (let [objective (:objective data)]
    (apply str
           (html/emit*
             (tf/translate context
                           (pf/add-google-analytics
                             (html/at draft-template
                                      [:title] (html/content (:title doc))
                                      [(and (html/has :meta) (html/attr= :name "description"))] (html/set-attr "content" (:description doc))
                                      [:.clj-masthead-signed-out] (html/substitute (pf/masthead context))
                                      [:.clj-status-bar] (html/substitute (pf/status-flash-bar context))

                                      [:.clj-guidance-buttons] nil
                                      [:.clj-guidance-heading] (html/content (translations :draft-guidance/heading))

                                      [:.clj-draft-wrapper] (if (tf/in-drafting? objective)
                                                              (html/substitute (draft-wrapper context))
                                                              (html/content (drafting-begins-in context))))))))))
