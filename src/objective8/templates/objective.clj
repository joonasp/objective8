(ns objective8.templates.objective
  (:require [net.cgrand.enlive-html :as html]
            [net.cgrand.jsoup :as jsoup]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [cemerick.url :as url]
            [objective8.utils :as utils]
            [objective8.permissions :as permissions]
            [objective8.templates.page-furniture :as pf]
            [objective8.templates.template-functions :as tf]))   

(def objective-template (html/html-resource "templates/jade/objective.html" {:parser jsoup/parser}))


(defn mail-to-string [flash objective translations]
  (str "mailto:" (:writer-email flash)
       "?subject=" (translations :invitation-modal/email-subject)
       "&body=" (translations :invitation-modal/email-body-line-1) " " (:title objective)
       "%0d%0d" (translations :invitation-modal/email-body-line-2) "%0d" (:invitation-url flash)))

(defn writer-invitation-modal [{:keys [data doc translations] :as context}]
  (let [objective (:objective data)
        flash (:flash doc)]
    (html/transformation
     [:.clj-invitation-url] (html/set-attr "value" (:invitation-url flash))
     [:.clj-mail-to] (html/set-attr :href (mail-to-string flash
                                                          objective
                                                          translations)))))

(def invitation-response-snippet (html/select (html/html-resource "templates/jade/objective-invitation-response.html") [:.clj-invitation-response]))

(defn invitation-rsvp-modal [{:keys [data invitation-rsvp user ring-request] :as context}]
  (let [objective (:objective data)
        tl8 (tf/translator context)
        objective-id (:objective-id invitation-rsvp)
        invitation-id (:invitation-id invitation-rsvp)]
    (html/transformation
      [:.clj-modal-contents] (html/content

                               (html/at invitation-response-snippet
                                        [:.clj-objective-title] (html/content (:title objective)) 
                                        [:.clj-invitation-response-decline] 
                                        (html/do-> 
                                          (html/set-attr :action (utils/local-path-for :fe/decline-invitation :id (:objective-id invitation-rsvp) :i-id (:invitation-id invitation-rsvp)))
                                          (html/prepend (html/html-snippet (anti-forgery-field)))) 
                                        [:.clj-invitation-response-accept]
                                        (if user
                                          (html/do-> (html/prepend (html/html-snippet (anti-forgery-field))) 
                                                     (html/set-attr :action (utils/local-path-for :fe/accept-invitation :id (:objective-id invitation-rsvp) :i-id (:invitation-id invitation-rsvp))))
                                          (html/substitute (html/at pf/anchor-button 
                                                                    [:.clj-anchor-button] (html/do->
                                                                                            (html/add-class "func--sign-in-to-accept") 
                                                                                            (html/set-attr :href (str "/sign-in?refer=" (:uri ring-request)))
                                                                                            (tl8 :invitation-response/sign-in-to-accept))))))))))


(def share-question-modal-snippet (html/select (html/html-resource "templates/jade/modals/share-question-modal.html")
                                               [:.clj-share-question-modal]))

(def share-endpoints
  {:reddit (url/url "http://reddit.com/submit")
   :facebook (url/url "http://www.facebook.com/sharer.php")
   :linked-in (url/url "http://www.linkedin.com/shareArticle")
   :twitter (url/url "https://twitter.com/share")
   :google-plus (url/url "https://plusone.google.com/_/+1/confirm")})

(defn share-question-modal [{:keys [doc] :as context}]
  (when-let [question (get-in doc [:flash :created-question])]
    (let [question-url (utils/path-for :fe/question
                                       :id (:objective-id question)
                                       :q-id (:_id question))
          question-text (:question question)
          sharing-text question-text
          reddit-url (str (assoc (:reddit share-endpoints) :query {:url question-url :title sharing-text}))
          facebook-url (str (assoc (:facebook share-endpoints) :query {:u question-url :t sharing-text}))
          twitter-url (str (assoc (:twitter share-endpoints) :query {:url question-url :text sharing-text}))
          linked-in-url (str (assoc (:linked-in share-endpoints) :query {:mini "true" :url question-url}))
          google-plus-url (str (assoc (:google-plus share-endpoints) :query {:hl "en" :url question-url}))]

      (html/content (html/at share-question-modal-snippet
                           [:.clj-question-text] (html/content question-text)
                           [:.clj-share-on-reddit] (html/set-attr :href reddit-url)
                           [:.clj-share-on-facebook] (html/set-attr :href facebook-url)
                           [:.clj-share-on-linked-in] (html/set-attr :href linked-in-url)
                           [:.clj-share-on-twitter] (html/set-attr :href twitter-url)
                           [:.clj-share-on-google-plus] (html/set-attr :href google-plus-url)
                           [:.clj-share-by-url-text-input] (html/set-attr :value question-url))))))

;; DRAFTING HAS STARTED MESSAGE

(def drafting-message-snippet (html/select pf/library-html-resource [:.clj-drafting-message]))

(defn drafting-message [{:keys [data] :as context}]
  (let [objective (:objective data)]
    (html/at drafting-message-snippet
      [:.clj-drafting-message-link] (html/set-attr :href (str "/objectives/" (:_id objective) 
                                                              "/drafts")))))

(defn drafting-begins [objective]
  (html/transformation
    [:.clj-days-left-day] (html/do->
                            (html/set-attr :drafting-begins-date (:end-date objective))
                            (html/content (str (:days-until-drafting-begins objective))))))

(defn invitation-rsvp-for-objective? [objective invitation-rsvp]
  (let [objective-id (:_id objective)
        invitation-objective-id (:objective-id invitation-rsvp)]
    (and objective-id (= invitation-objective-id objective-id))))

;; QUESTION LIST

(def empty-community-question-list-item-snippet (html/select pf/library-html-resource [:.clj-empty-community-questions]))
(def empty-objective-question-list-item-snippet (html/select pf/library-html-resource [:.clj-empty-objective-questions]))

(def question-list-item-snippet (html/select pf/library-html-resource [:.clj-library-key--question-list-item]))
(def question-list-item-with-promote-form-snippet (html/select pf/library-html-resource [:.clj-library-key--question-list-item-with-promote-form]))
(def question-list-item-with-demote-form-snippet (html/select pf/library-html-resource [:.clj-library-key--question-list-item-with-demote-form]))

(defn question-list-items [list-item-snippet questions]
  (html/at list-item-snippet
           [:.clj-question-item] 
           (html/clone-for [question questions]
                           [:.clj-question-text] (html/content (:question question))
                           [:.clj-answer-link] (html/set-attr :href (str "/objectives/" (:objective-id question)
                                                                         "/questions/" (:_id question)))
                           [:.clj-promote-question-form] (html/prepend (html/html-snippet (anti-forgery-field)))
                           [:.clj-demote-question-form] (html/prepend (html/html-snippet (anti-forgery-field)))
                           [:.clj-refer] (html/set-attr :value (str "/objectives/" (:objective-id question) "#questions"))
                           [:.clj-question-uri] (html/set-attr :value (str "/objectives/" (:objective-id question)
                                                                           "/questions/" (:_id question))))))

(defn objective-question-list [{:keys [data user] :as context}]
  (let [objective-questions (filter tf/marked? (:questions data))
        list-item-snippet (if (permissions/can-mark-questions? (:objective data) user)
                            question-list-item-with-demote-form-snippet
                            question-list-item-snippet)]
    (if (empty? objective-questions)
      empty-objective-question-list-item-snippet
      (question-list-items list-item-snippet objective-questions))))

(defn community-question-list [{:keys [data user] :as context}]
  (let [community-questions (filter (complement tf/marked?) (:questions data))
        list-item-snippet (if (permissions/can-mark-questions? (:objective data) user)
                            question-list-item-with-promote-form-snippet
                            question-list-item-snippet)]
    (if (empty? community-questions)
      empty-community-question-list-item-snippet
      (question-list-items list-item-snippet community-questions))))


;; STAR FORM
(defn star-form-when-signed-in [{:keys [data ring-request] :as context}]
      (html/transformation
             [:.clj-star-form] (html/prepend (html/html-snippet (anti-forgery-field)))
             [:.clj-refer] (html/set-attr :value (:uri ring-request))
             [:.clj-star-on-uri] (html/set-attr :value (:uri ring-request))
             [:.clj-objective-star] (if (tf/starred? (:objective data))
                                      (html/add-class "starred")
                                      identity)))

(defn star-form-when-not-signed-in [{:keys [ring-request] :as context}]
  (html/transformation
             [:.clj-star-form] (html/set-attr :method "get")
             [:.clj-star-form] (html/set-attr :action "/sign-in")
             [:.clj-refer] (html/set-attr :value (:uri ring-request))
             [:.clj-star-on-uri] nil))

;; OBJECTIVE PAGE
(def star-form-snippet (html/select objective-template [:.clj-star-form]))

(defn star-form [objective ring-request]
  (html/at star-form-snippet
           [:.clj-star-form] (html/prepend (html/html-snippet (anti-forgery-field)))
           [:.clj-refer] (html/set-attr :value (:uri ring-request))
           [:.clj-star-on-uri] (html/set-attr :value (:uri ring-request))
           [:.clj-objective-star] (if (tf/starred? objective)
                                    (html/add-class "starred")
                                    identity)))

(defn objective-page [{:keys [translations data doc invitation-rsvp ring-request user] :as context}]
  (let [objective (:objective data)
        objective-id (:_id objective)
        flash (:flash doc)
        optionally-disable-voting (if (tf/in-drafting? objective)
                                    (pf/disable-voting-actions translations)
                                    identity)]
    (apply str
           (html/emit*
             (tf/translate context
                           (pf/add-google-analytics
                             (html/at objective-template
                                      [:title] (html/content (:title doc))
                                      [:.clj-masthead-signed-out] (html/substitute (pf/masthead context))
                                      [:.clj-status-bar] (html/substitute (pf/status-flash-bar context))
                                      [:.clj-modal-contents]
                                      (case (:type flash)
                                        :invitation (writer-invitation-modal context)
                                        :share-question (share-question-modal context)
                                        (when (invitation-rsvp-for-objective? objective invitation-rsvp)
                                          (invitation-rsvp-modal context)))

                                      [:.clj-objective-progress-indicator] nil
                                      [:.clj-guidance-buttons] nil
                                      [:.clj-guidance-heading] (html/content (translations :objective-guidance/heading))

                                      [:.clj-star-form] (if user
                                                          (star-form-when-signed-in context)
                                                          (star-form-when-not-signed-in context))

                                      [:.clj-objective-title] (html/content (:title objective))

                                      [:.clj-days-left] (when (tf/open? objective)
                                                          (drafting-begins objective))
                                      [:.clj-drafting-started-wrapper] (when (tf/in-drafting? objective)
                                                                         (html/substitute (drafting-message context)))
                                      [:.clj-replace-with-objective-detail] (html/substitute (tf/text->p-nodes (:description objective)))

                                      [:.clj-writer-item-list] (html/content (pf/writer-list context))
                                      [:.clj-invite-writer-link] (when (and 
                                                                         (permissions/writer-inviter-for? user objective-id)
                                                                         (tf/open? objective))
                                                                   (html/set-attr
                                                                     :href (str "/objectives/" (:_id objective) "/invite-writer")))

                                      [:.clj-writer-dashboard-link] (when (permissions/writer-for? user objective-id)
                                                                      (html/set-attr
                                                                        :href (str "/objectives/" (:_id objective) "/dashboard/questions")))

                                      [:.clj-objective-question-list] (html/content (objective-question-list context))
                                      [:.clj-community-question-list] (html/content (community-question-list context))
                                      [:.clj-ask-question-link] (when (tf/open? objective)
                                                                  (html/set-attr
                                                                    "href" (str "/objectives/" (:_id objective) "/add-question")))

                                      [:.clj-comment-list] (html/content
                                                             (optionally-disable-voting
                                                               (pf/comment-list context)))
                                      [:.clj-comment-create] (when (tf/open? objective)
                                                               (html/content (pf/comment-create context :objective))))))))))
