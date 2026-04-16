(ns thorold.cli
  "CLI commands: pure fns + thin I/O shell.

   Every command is a pure function that takes args and db,
   returns a result map. The -main function handles I/O only:
   parsing args, loading the DB, dispatching, and printing."
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.json :as json]
            [thorold.db :as db]
            [thorold.query :as query]))

;; ---------------------------------------------------------------------------
;; Pure command functions — no I/O, no side effects
;; ---------------------------------------------------------------------------

(defn cmd-search
  "Search entities by name. Returns {:results [...] :count N}."
  [args db]
  (let [results (query/search db (:name args)
                              {:type  (:type args)
                               :limit (or (:limit args) 10)})]
    {:results results
     :count   (count results)}))

(defn cmd-resolve
  "Resolve a provider ID to the full entity."
  [args db]
  (let [provider (keyword (:provider args))
        id       (:id args)
        entity   (query/resolve db provider id)]
    (if entity
      {:results [entity] :count 1}
      {:results [] :count 0 :message (str "No entity found for " (name provider) "=" id)})))

(defn cmd-translate
  "Translate from one provider ID to another."
  [args db]
  (let [source-provider (keyword (:source args))
        source-id       (:id args)
        target-provider (keyword (:target args))
        entity          (query/resolve db source-provider source-id)]
    (if entity
      (let [target-id (get (:providers entity) target-provider)]
        (if target-id
          {:result target-id :found true}
          {:result nil :found false
           :message (str "No " (name target-provider) " ID found for entity")}))
      {:result nil :found false
       :message (str "No entity found for " (name source-provider) "=" source-id)})))

(defn cmd-lookup
  "Look up by Reep ID or Wikidata QID."
  [args db]
  (let [id     (:id args)
        entity (query/lookup db id)]
    (if entity
      {:results [entity] :count 1}
      {:results [] :count 0 :message (str "No entity found for " id)})))

(defn cmd-stats
  "Show database statistics."
  [_args db]
  (db/db-stats db))

(defn cmd-download
  "Download command — placeholder. In Thorold, data is loaded from local CSV files."
  [args _db]
  {:message "Data is loaded from local CSV files in the data/ directory."
   :data-dir (or (:data-dir args) "data/")})

;; ---------------------------------------------------------------------------
;; Output formatting — all output goes through print-result
;; ---------------------------------------------------------------------------

(defn- format-entity-human
  "Formats an entity for human-readable terminal output."
  [entity]
  (let [ename   (or (:person/name entity) (:team/name entity)
                    (:competition/name entity) (:season/name entity) "?")
        type    (name (:reep/type entity))
        reep-id (:reep/id entity)
        qid     (get-in entity [:providers :wikidata] "—")]
    (str "\033[1m" ename "\033[0m  (" type ")  " reep-id "  " qid "\n"
         (when-let [dob (:person/dob entity)]
           (str "  DOB: " dob "\n"))
         (when-let [nat (:person/nationality entity)]
           (str "  Nationality: " nat "\n"))
         (when-let [pos (:person/position-detail entity)]
           (str "  Position: " pos "\n"))
         (when-let [country (:team/country entity)]
           (str "  Country: " country "\n"))
         (let [providers (:providers entity)]
           (when (seq providers)
             (let [max-key (apply max (map (comp count name) (keys providers)))]
               (str/join "\n"
                         (map (fn [[k v]]
                                (str "  " (format (str "%-" max-key "s") (name k)) "  " v))
                              (sort-by (comp name key) providers)))))))))

(defn- entity->json-map
  "Converts an entity to a JSON-serializable map matching the API response format."
  [entity]
  (let [base {:reep_id (:reep/id entity)
              :type    (name (:reep/type entity))
              :providers (:providers entity)}]
    (cond-> base
      (:person/name entity)
      (assoc :name (:person/name entity)
             :full_name (:person/full-name entity)
             :date_of_birth (:person/dob entity)
             :nationality (:person/nationality entity)
             :position (:person/position entity)
             :position_detail (:person/position-detail entity)
             :height_cm (:person/height-cm entity))

      (:team/name entity)
      (assoc :name (:team/name entity)
             :country (:team/country entity)
             :founded (:team/founded entity)
             :stadium (:team/stadium entity))

      (get-in entity [:providers :wikidata])
      (assoc :qid (get-in entity [:providers :wikidata])))))

(defn print-result
  "Formats and prints a command result. All output goes through here.
   format: :human (default), :edn, :json"
  [result args]
  (let [fmt (keyword (or (:format args) "human"))]
    (case fmt
      :edn  (prn result)
      :json (println (json/write-str
                      (cond
                        (:results result)
                        {:results (mapv entity->json-map (:results result))
                         :count   (:count result)}

                        (:result result)
                        {:result (:result result)}

                        :else result)
                      :escape-slash false))
      ;; :human
      (cond
        (:results result)
        (if (empty? (:results result))
          (println (or (:message result) "No results found."))
          (doseq [entity (:results result)]
            (println (format-entity-human entity))
            (println)))

        (:result result)
        (println (:result result))

        (:total-entities result)
        (do
          (println (str "Total entities: " (format "%,d" (:total-entities result))))
          (println)
          (println "By type:")
          (doseq [[t c] (sort (:by-type result))]
            (println (format "  %-15s %,10d" t c)))
          (println)
          (println "By provider:")
          (doseq [[p c] (:by-provider result)]
            (println (format "  %-25s %,10d" p c))))

        (:message result)
        (println (:message result))

        :else
        (prn result)))))

;; ---------------------------------------------------------------------------
;; Argument parsing
;; ---------------------------------------------------------------------------

(def ^:private cli-options
  [["-t" "--type TYPE" "Entity type filter"
    :parse-fn (fn [s]
                (case s
                  "player" :person/player
                  "coach"  :person/coach
                  "team"   :team
                  "competition" :competition
                  "season" :season
                  nil))]
   ["-l" "--limit N" "Max results"
    :default 10
    :parse-fn #(Integer/parseInt %)]
   ["-f" "--format FORMAT" "Output format: human, edn, json"
    :default "human"]
   ["-d" "--data-dir DIR" "Data directory"
    :default "data/"]
   ["-v" "--verbose" "Verbose output"]
   ["-h" "--help" "Show help"]])

(defn parse-args
  "Parses CLI arguments. Returns a map with :command, :args, and any flags."
  [argv]
  (let [{:keys [options arguments errors]} (parse-opts argv cli-options)
        command (first arguments)
        rest-args (rest arguments)]
    (merge options
           {:command   command
            :arguments rest-args
            :errors    errors})))

(defn dispatch
  "Dispatches to the appropriate command function. Pure."
  [args db]
  (case (:command args)
    "search"    (cmd-search (assoc args :name (str/join " " (:arguments args))) db)
    "resolve"   (cmd-resolve (assoc args
                                    :provider (first (:arguments args))
                                    :id (second (:arguments args))) db)
    "translate" (cmd-translate (assoc args
                                      :source (first (:arguments args))
                                      :id (second (:arguments args))
                                      :target (nth (:arguments args) 2)) db)
    "lookup"    (cmd-lookup (assoc args :id (first (:arguments args))) db)
    "stats"     (cmd-stats args db)
    "download"  (cmd-download args db)
    {:error (str "Unknown command: " (:command args))
     :usage "Usage: thorold <search|resolve|translate|lookup|stats|download> [args]"}))

;; ---------------------------------------------------------------------------
;; Entry point — thin I/O shell
;; ---------------------------------------------------------------------------

(defn -main
  [& argv]
  (let [args (parse-args argv)]
    (cond
      (:help args)
      (do
        (println "thorold — The Football Entity Register")
        (println)
        (println "Commands:")
        (println "  search <name>                    Search entities by name")
        (println "  resolve <provider> <id>          Resolve a provider ID")
        (println "  translate <source> <id> <target> Translate between providers")
        (println "  lookup <id>                      Look up by Reep ID or QID")
        (println "  stats                            Show database statistics")
        (println "  download                         Download latest data")
        (println)
        (println "Options:")
        (println "  -t, --type TYPE     Filter by type (player, coach, team)")
        (println "  -l, --limit N       Max results (default 10)")
        (println "  -f, --format FMT    Output format: human, edn, json")
        (println "  -d, --data-dir DIR  Data directory (default data/)")
        (System/exit 0))

      (nil? (:command args))
      (do
        (println "Usage: thorold <command> [args]")
        (println "Run 'thorold --help' for usage information.")
        (System/exit 2))

      :else
      (try
        (let [db     (db/load-db-cached (:data-dir args))
              result (dispatch args db)]
          (print-result result args)
          (System/exit (if (or (:error result)
                               (and (:results result) (zero? (:count result))))
                         1 0)))
        (catch Exception e
          (binding [*out* *err*]
            (println (str "Error: " (.getMessage e))))
          (System/exit 3))))))
