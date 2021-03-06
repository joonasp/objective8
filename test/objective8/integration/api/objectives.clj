(ns objective8.integration.api.objectives
  (:require [midje.sweet :refer :all]
            [peridot.core :as p]
            [cheshire.core :as json]
            [objective8.utils :as utils]
            [objective8.core :as core]
            [objective8.integration.integration-helpers :as helpers]
            [objective8.integration.storage-helpers :as sh]
            [objective8.objectives :as objectives]
            [objective8.users :as users]
            [objective8.middleware :as m]))

(def app (helpers/test-context))

(def OBJECTIVE_ID 10)

(def the-objective {:title "my objective title"
                    :goal-1 "my first objective goal"
                    :goal-2 "my second objective goal"
                    :description "my objective description"
                    :end-date "2015-01-01"
                    :created-by-id 1})

(def the-invalid-objective {:title "my objective title"
                            :goal-1 "my first objective goal"
                            :goal-2 "my second objective goal"
                            :description "my objective description"
                            :end-date "2015-01-01"})

(def stored-objective (assoc the-objective :_id OBJECTIVE_ID))

(defn gen-user-with-id
  "Make a user and return the ID for use in creating other content"
  []
  (:_id (users/store-user! {:twitter-id "anything" :username "username"})))

(facts "objectives"
       (against-background
        (m/valid-credentials? anything anything anything) => true)
       (against-background
        [(before :contents (do (helpers/db-connection)
                               (helpers/truncate-tables)))
         (after :facts (helpers/truncate-tables))]

        (facts "GET /api/v1/objectives returns a list of objectives in reverse chronological order"
               (fact "objectives are returned as a list"
                     (let [stored-objectives (doall (repeatedly 5 sh/store-an-open-objective))
                           {response :response} (p/request app "/api/v1/objectives")]
                       (:body response) => (helpers/json-contains (map contains (->> stored-objectives
                                                                                     (map #(dissoc % :global-id))
                                                                                     reverse)))))

               (fact "returns an empty list if there are no objectives"
                     (do
                       (helpers/truncate-tables)
                       (helpers/peridot-response-json-body->map (p/request app "/api/v1/objectives")))
                     => empty?))

         (facts "GET /api/v1/objectives?user-id=<user-id>"
                (fact "returns a list of objectives with meta information for signed-in user"
                      (let [unstarred-objective (sh/store-an-open-objective)
                            {user-id :_id :as user} (sh/store-a-user)
                            starred-objective (sh/store-an-open-objective)
                            stored-star (sh/store-a-star {:user user :objective starred-objective})
                            {response :response} (p/request app (str "/api/v1/objectives?user-id=" user-id))] 
                        (:body response) => (helpers/json-contains [(contains {:meta {:starred true}})
                                                                    (contains {:meta {:starred false}})] :in-any-order))))

         (facts "GET /api/v1/objectives?starred=true&user-id=<user-id>"
                (fact "retrieves objectives in reverse chronological order that have been starred by user with given user-id"
                      (let [{user-id :_id :as user} (sh/store-a-user)
                            stored-objectives [(sh/store-an-open-objective)
                                               (sh/store-an-objective-in-draft)]

                            stored-stars (doall (map sh/store-a-star
                                                     [{:user user :objective (first stored-objectives)}
                                                      {:user user :objective (second stored-objectives)}])) ]
                        (get-in (p/request app (str "/api/v1/objectives?starred=true&user-id=" user-id)) [:response :body])
                        => (helpers/json-contains (map contains (->> stored-objectives
                                                                     reverse
                                                                     (map #(select-keys % [:_id]))))))))

         (facts "GET /api/v1/objectives/:id"
               (fact "can retrieve an objective using its id"
                     (let [{user-id :_id username :username} (sh/store-a-user) 
                           stored-objective (objectives/store-objective! (assoc the-objective :created-by-id user-id))
                           objective-url (str "/api/v1/objectives/" (:_id stored-objective))]
                       (get-in (p/request app objective-url)
                               [:response :body]) => (helpers/json-contains (assoc stored-objective :username username))))

               (fact "returns a 404 if an objective does not exist"
                     (p/request app (str "/api/v1/objectives/" 123456))
                     => (contains {:response (contains {:status 404})})) 

               (fact "returns an error if objective id is not an integer"
                     (p/request app "/api/v1/objectives/NOT-AN-INTEGER")
                     => (contains {:response (contains {:status 404})}))) 

         (facts "GET /api/v1/objectives/:id?signed-in-id=<user-id>"
                (fact "retrieves the objective by its id, along with meta-information relevant for the signed in user"
                      (let [objective-creator (sh/store-a-user)
                            {o-id :_id :as starred-objective} (sh/store-an-open-objective {:user objective-creator})
                            {user-id :_id :as user} (sh/store-a-user)
                            _ (sh/store-a-star {:user user :objective starred-objective})

                            {response :response} (p/request app (str "/api/v1/objectives/" o-id "?signed-in-id=" user-id))
                            retrieved-objective (-> starred-objective
                                                    (select-keys [:_id :description :_created_at :created-by-id
                                                                  :end-date :entity :goals :status :title])
                                                    (assoc :username (:username objective-creator))
                                                    (assoc :meta {:starred true})
                                                    (assoc :uri (str "/objectives/" o-id)))]
                        (:body response) => (helpers/json-contains retrieved-objective))))

         (facts "GET /api/v1/objectives/:id?with-stars-count=true"
                (fact "retrieves the objective by its id, along with star-count for objective"
                      (let [{username :username :as objective-creator} (sh/store-a-user)
                            {objective-id :_id :as objective} (sh/store-an-open-objective {:user objective-creator})
                            retrieved-objective (-> objective
                                                    (assoc :username username
                                                           :uri (str "/objectives/" objective-id))
                                                    (dissoc :global-id :meta)) 
                            _ (sh/store-a-star {:objective objective}) 
                            _ (sh/store-a-star {:objective objective}) 

                            {response :response} (p/request app (str "/api/v1/objectives/" objective-id "?with-stars-count=true"))] 
                        (:body response) => (helpers/json-contains retrieved-objective)
                        (:body response) => (helpers/json-contains {:meta (contains {:starred false :stars-count 2})}))))

         (facts "about posting objectives"
               (against-background
                (m/valid-credentials? anything anything anything) => true)

               (fact "the posted objective is stored"
                     (let [{user-id :_id} (sh/store-a-user)
                           the-objective {:title "my objective title"
                                          :goal-1 "my first objective goal"
                                          :end-date "2015-01-01"
                                          :created-by-id user-id}
                           {response :response} (p/request app "/api/v1/objectives"
                                                           :request-method :post
                                                           :content-type "application/json"
                                                           :body (json/generate-string the-objective))]
                       (:body response) => (helpers/json-contains
                                            (assoc the-objective
                                                   :uri (contains "/objectives/")
                                                   :end-date "2015-01-01T00:00:00.000Z"))
                       (:body response) =not=> (helpers/json-contains {:global-id anything})
                       (:headers response) => (helpers/location-contains (str "/api/v1/objectives/"))
                       (:status response) => 201))

               (fact "a 400 status is returned if a PSQLException is raised"
                     (against-background
                      (objectives/store-objective! anything) =throws=> (org.postgresql.util.PSQLException.
                                                                        (org.postgresql.util.ServerErrorMessage. "" 0)))
                     (:response (p/request app "/api/v1/objectives"
                                           :request-method :post
                                           :content-type "application/json"
                                           :body (json/generate-string the-objective))) => (contains {:status 400}))

               (fact "a 400 status is returned if a map->objective exception is raised"
                     (:response (p/request app "/api/v1/objectives"
                                           :request-method :post
                                           :content-type "application/json"
                                           :body (json/generate-string the-invalid-objective))) => (contains {:status 400})))))
