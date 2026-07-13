(ns thorold.api
  "Ring/Reitit REST API handlers.

   Every handler is a pure function taking [request db] and returning
   a Ring response map. The db value is injected via middleware at startup.

   Endpoint contracts match the original Reep API:
     GET /search?name=...&type=...
     GET /resolve?provider=...&id=...
     GET /lookup?id=...
     GET /stats"
  (:require [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :refer [wrap-params]]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [thorold.query :as query]
            [thorold.db :as db]
            [thorold.export :as export]))

;; ---------------------------------------------------------------------------
;; Response helpers
;; ---------------------------------------------------------------------------

(defn- json-response
  "Creates a Ring response with JSON content type."
  ([body] (json-response body 200))
  ([body status]
   {:status  status
    :headers {"Content-Type" "application/json"
              "Access-Control-Allow-Origin" "*"}
    :body    (json/generate-string body {:pretty false})}))

(defn- error-response
  "Creates a JSON error response. Never exposes stack traces."
  [message code status]
  (json-response {:error message :code code} status))

(defn- entity->api-map
  "Converts a Thorold entity map to the API response format."
  [entity]
  (let [providers    (:providers entity)
        external-ids (into {} (map (fn [[k v]] [(name k) v]) providers))]
    (cond-> {:reep_id      (:reep/id entity)
             :qid          (get providers :wikidata)
             :type         (name (:reep/type entity))
             :external_ids external-ids}

      (:person/name entity)
      (assoc :name_en         (:person/name entity)
             :full_name       (:person/full-name entity)
             :date_of_birth   (:person/dob entity)
             :nationality     (:person/nationality entity)
             :position        (:person/position entity)
             :position_detail (:person/position-detail entity)
             :height_cm       (:person/height-cm entity))

      (:team/name entity)
      (assoc :name_en (:team/name entity)
             :country (:team/country entity)
             :founded (:team/founded entity)
             :stadium (:team/stadium entity))

      (:competition/name entity)
      (assoc :name_en (:competition/name entity))

      (:season/name entity)
      (assoc :name_en (:season/name entity)))))

;; ---------------------------------------------------------------------------
;; Pure handler functions — take [request db], return Ring response
;; ---------------------------------------------------------------------------

(defn handle-search
  "GET /search?name=...&type=...&limit=..."
  [request db]
  (let [params (:query-params request)
        name   (get params "name")]
    (if (str/blank? name)
      (error-response "Required: ?name=Cole Palmer" "missing_param" 400)
      (let [type-str (get params "type")
            type-kw  (when type-str
                       (case type-str
                         "player" :person/player
                         "coach"  :person/coach
                         "team"   :team
                         "competition" :competition
                         "season" :season
                         nil))
            limit    (min (or (some-> (get params "limit")
                                     Integer/parseInt)
                              25)
                         100)
            results  (query/search db name {:type type-kw :limit limit})
            api-results (mapv entity->api-map results)]
        (json-response {:results api-results :count (count api-results)})))))

(defn handle-resolve
  "GET /resolve?provider=...&id=..."
  [request db]
  (let [params   (:query-params request)
        provider (get params "provider")
        id       (get params "id")]
    (if (or (str/blank? provider) (str/blank? id))
      (error-response "Required: ?provider=transfermarkt&id=568177" "missing_param" 400)
      (let [entity (query/resolve db (keyword provider) id)]
        (if entity
          (json-response {:results [(entity->api-map entity)] :count 1})
          (json-response {:results [] :count 0}))))))

(defn handle-lookup
  "GET /lookup?id=..."
  [request db]
  (let [params (:query-params request)
        id     (or (get params "id") (get params "qid"))]
    (if (str/blank? id)
      (error-response "Required: ?id=reep_p2804f5db" "missing_param" 400)
      (let [entity (query/lookup db id)]
        (if entity
          (json-response {:results [(entity->api-map entity)] :count 1})
          (json-response {:results [] :count 0}))))))

(defn handle-stats
  "Returns database statistics."
  [_req db]
  (json-response (db/db-stats db)))

(defn- parse-json-body
  "Parses a JSON request body. Returns nil on failure."
  [request]
  (try
    (when-let [body (:body request)]
      (let [s (if (string? body)
                body
                (slurp body))]
        (when (seq s)
          (json/parse-string s))))
    (catch Exception _ nil)))

(defn handle-batch-lookup
  "POST /batch/lookup  body: {\"ids\": [\"reep_p...\", \"Q615\", ...]}
   Max 100 items. Returns {:results [...] :count N :not_found N}."
  [request db]
  (let [body (parse-json-body request)
        ids  (get body "ids")]
    (cond
      (not (sequential? ids))
      (error-response "Required: {\"ids\": [...]}" "missing_param" 400)

      (> (count ids) 100)
      (error-response "Maximum 100 items per batch request" "batch_limit_exceeded" 400)

      :else
      (let [result   (query/batch-lookup db ids)
            api-results (mapv entity->api-map (:results result))]
        (json-response {:results   api-results
                        :count     (count api-results)
                        :not_found (:not_found result)})))))

(defn handle-batch-resolve
  "POST /batch/resolve  body: {\"items\": [{\"provider\": \"transfermarkt\", \"id\": \"28003\"}]}
   Max 100 items. Returns {:results [...] :count N :not_found N}."
  [request db]
  (let [body  (parse-json-body request)
        items (get body "items")]
    (cond
      (not (sequential? items))
      (error-response "Required: {\"items\": [{\"provider\": ..., \"id\": ...}]}" "missing_param" 400)

      (> (count items) 100)
      (error-response "Maximum 100 items per batch request" "batch_limit_exceeded" 400)

      :else
      (let [parsed-items (mapv (fn [item]
                                {:provider (get item "provider")
                                 :id       (get item "id")})
                              items)
            result       (query/batch-resolve db parsed-items)
            api-results  (mapv entity->api-map (:results result))]
        (json-response {:results   api-results
                        :count     (count api-results)
                        :not_found (:not_found result)})))))

;; ---------------------------------------------------------------------------
;; Middleware
;; ---------------------------------------------------------------------------

(defn wrap-db
  "Middleware that injects the db value into handlers."
  [handler db]
  (fn [request]
    (handler request db)))

(defn wrap-exceptions
  "Middleware that catches exceptions and returns JSON error responses.
   Never exposes stack traces to clients."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (error-response (.getMessage e)
                          (name (or (:thorold/error-type data) :internal-error))
                          400)))
      (catch Exception e
        (log/error e "Unhandled exception in API handler")
        (error-response "Internal server error" "internal_error" 500)))))

(defn wrap-content-negotiation
  "Middleware: read the Accept header, serve application/edn (via pr-str)
   when requested. Otherwise return JSON as today.
   If the response body is already a string (from json-response), and the
   client wants EDN, re-parse the JSON and pr-str it. This is not ideal
   but avoids changing every handler's return contract."
  [handler]
  (fn [request]
    (let [response (handler request)
          accept   (get-in request [:headers "accept"] "")]
      (if (and (str/includes? accept "application/edn")
               (= "application/json"
                  (get-in response [:headers "Content-Type"])))
        (try
          (let [body-str (:body response)
                data     (json/parse-string body-str true)]
            (assoc response
                   :headers (assoc (:headers response)
                                   "Content-Type" "application/edn")
                   :body (pr-str data)))
          (catch Exception _
            response))
        response))))


;; ---------------------------------------------------------------------------
;; Router and app
;; ---------------------------------------------------------------------------

(defn create-app
  "Creates the Ring handler with all routes and middleware.
   db is injected at startup — never a global var."
  [db]
  (let [handler
        (ring/ring-handler
         (ring/router
          [["/" {:get (fn [_req] (json-response
                                 {:name    "Thorold — The Football Entity Register"
                                  :version "1.0.0"
                                  :docs    "https://github.com/dennisgathu8/thorold"}))}]
           ["/search"  {:get (fn [req] (handle-search req db))}]
           ["/resolve" {:get (fn [req] (handle-resolve req db))}]
           ["/lookup"  {:get (fn [req] (handle-lookup req db))}]
           ["/stats"   {:get (fn [req] (handle-stats req db))}]
           ["/batch/lookup"  {:post (fn [req] (handle-batch-lookup req db))}]
           ["/batch/resolve" {:post (fn [req] (handle-batch-resolve req db))}]
           ["/schema/person" {:get (fn [_req] (json-response (export/person-json-schema)))}]
           ["/schema/team"   {:get (fn [_req] (json-response (export/team-json-schema)))}]])
         (ring/create-default-handler
          {:not-found (fn [_] (error-response "Not found" "not_found" 404))}))]
    (-> handler
        wrap-params
        wrap-content-negotiation
        wrap-exceptions)))

;; ---------------------------------------------------------------------------
;; Server lifecycle
;; ---------------------------------------------------------------------------

(defn start-server
  "Starts the Jetty server. Returns the server instance."
  [db & {:keys [port] :or {port 8080}}]
  (log/info (str "Starting Thorold API on port " port))
  (let [app    (create-app db)
        server (jetty/run-jetty app {:port port :join? false})]
    (log/info (str "Thorold API running at http://localhost:" port))
    server))

(defn stop-server
  "Stops the Jetty server."
  [server]
  (when server
    (.stop server)
    (log/info "Thorold API stopped")))
