(ns objective8.integration.api.drafting
  (:require [midje.sweet :refer :all]
            [peridot.core :as p]
            [cheshire.core :as json]
            [objective8.utils :as utils]
            [objective8.integration.integration-helpers :as helpers]
            [objective8.integration.storage-helpers :as sh]
            [objective8.drafts :as drafts]
           [objective8.middleware :as m]))

(def app (helpers/test-context))

(facts "POST /api/v1/objectives/:id/drafts"
  (against-background
    (m/valid-credentials? anything anything anything) => true)
  (against-background
    [(before :contents (do (helpers/db-connection)
                           (helpers/truncate-tables)))
     (after :facts (helpers/truncate-tables))]
    
    (fact "creates a draft when submitter id is a writer for the objective and drafting has started"
          (let [{objective-id :objective-id submitter-id :user-id} (sh/store-a-writer)
                _ (sh/start-drafting! objective-id)
                draft-data {:objective-id objective-id
                            :submitter-id submitter-id
                            :content "Some content"}
                {response :response} (p/request app (utils/path-for :api/post-draft :id objective-id)
                                            :request-method :post
                                            :content-type "application/json"
                                            :body (json/generate-string draft-data))]
            (:body response) => (helpers/json-contains {:_id anything
                                                        :objective-id objective-id
                                                        :submitter-id submitter-id
                                                        :content "Some content"})
            (:status response) => 201))

    (fact "a draft is not created when drafting has not started"
          (let [{objective-id :objective-id submitter-id :user-id} (sh/store-a-writer)
                the-draft {:objective-id objective-id
                           :submitter-id submitter-id
                           :content "Some content"}
                {response :response} (p/request app (utils/path-for :api/post-draft :id objective-id)
                                                :request-method :post
                                                :content-type "application/json"
                                                :body (json/generate-string the-draft))]
            (:status response) => 404))

    (fact "a draft is not created when submitter id is not a writer for the objective"
          (let [{objective-id :_id :as objective} (sh/store-an-open-objective)
                {submitter-id :_id} (sh/store-a-user)
                the-draft {:objective-id objective-id
                           :submitter-id submitter-id 
                           :content "Some content"}
                {response :response} (p/request app (utils/path-for :api/post-draft :id objective-id)
                                                :request-method :post
                                                :content-type "application/json"
                                                :body (json/generate-string the-draft))]
            (:status response) => 404)))) 

(facts "GET /api/v1/objectives/:id/drafts/:d-id"
       (against-background
        [(before :contents (do (helpers/db-connection)
                               (helpers/truncate-tables)))
         (after :facts (helpers/truncate-tables))]

         (fact "gets a draft for an objective"
               (let [{objective-id :objective-id draft-id :_id} (sh/store-a-draft)
                     draft (drafts/retrieve-draft draft-id)
                     {response :response} (p/request app (utils/path-for :api/get-draft :id objective-id 
                                                                         :d-id draft-id))]
                 (:status response) => 200
                 (:body response) => (helpers/json-contains draft)))))

(facts "GET /api/v1/objectives/:id/drafts"
       (against-background
         [(before :contents (do (helpers/db-connection)
                                (helpers/truncate-tables)))
          (after :facts (helpers/truncate-tables))]

         (fact "gets drafts for an objective"
               (let [objective (sh/store-an-objective-in-draft)
                     stored-drafts (doall (->> (repeat {:objective objective})
                                               (take 5)
                                               (map sh/store-a-draft)
                                               (map #(dissoc % :_created_at_sql_time))))
                     {response :response} (p/request app (utils/path-for :api/get-drafts-for-objective :id (:_id objective)))]
                 (:status response) => 200
                 (:body response) => (helpers/json-contains (map contains (-> stored-drafts :_id reverse)))))

         (fact "returns 403 if objective not in drafting"
               (let [objective (sh/store-an-open-objective)]
                 (get-in (p/request app (utils/path-for :api/get-drafts-for-objective :id (:_id objective)))
                         [:response :status]) => 403))))

(facts "GET /api/v1/objectives/:id/drafts/latest"
       (against-background
         [(before :contents (do (helpers/db-connection)
                                (helpers/truncate-tables)))
          (after :facts (helpers/truncate-tables))]

         (fact "gets the latest draft for an objective"
               (let [{objective-id :_id :as objective} (sh/store-an-objective-in-draft)

                     first-draft (sh/store-a-draft {:objective objective})
                     {second-draft-id :draft-id} (sh/store-a-draft {:objective objective})

                     latest-draft (drafts/retrieve-draft second-draft-id)
                     
                     {response :response} (p/request app (utils/path-for :api/get-draft :id objective-id
                                                                         :d-id "latest"))]
                 (:status response) => 200
                 (:body response) => (helpers/json-contains latest-draft)))
         
         (fact "returns draft-id for previous draft"
               (let [objective (sh/store-an-objective-in-draft)
                     {first-draft-id :_id} (sh/store-a-draft {:objective objective})
                     {second-draft-id :_id :as second-draft} (sh/store-a-draft {:objective objective})]
                 
                 (get-in (p/request app (utils/path-for :api/get-draft
                                                        :id (:_id objective)
                                                        :d-id "latest"))
                         [:response :body]) => (helpers/json-contains {:previous-draft-id first-draft-id})))))
