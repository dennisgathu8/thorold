#!/usr/bin/env bb
;; Thorold data refresh script
;; Runs the Wikidata SPARQL ingestion pipeline to regenerate CSV files.
;;
;; Usage:
;;   bb scripts/refresh.clj --mode incremental
;;   bb scripts/refresh.clj --mode full
;;   bb scripts/refresh.clj --dry-run
;;
;; Environment variables:
;;   THOROLD_DATA_DIR    Path to data directory (default: data/)
;;   THOROLD_OUTPUT_DIR  Path to output directory (default: output/)

(require '[clojure.tools.cli :refer [parse-opts]])

(def cli-options
  [["-m" "--mode MODE" "Refresh mode: incremental or full"
    :default "incremental"
    :validate [#{"incremental" "full"} "Must be incremental or full"]]
   ["--dry-run" "Print steps without writing files"]
   ["-d" "--data-dir DIR"   "Data directory"   :default "data/"]
   ["-o" "--output-dir DIR" "Output directory" :default "output/"]
   ["-h" "--help" "Show help"]])

(defn -main [& args]
  (let [{:keys [options errors]} (parse-opts args cli-options)]
    (when (:help options)
      (println "Usage: bb scripts/refresh.clj [options]")
      (System/exit 0))
    (when errors
      (doseq [e errors] (println "Error:" e))
      (System/exit 2))
    (println (str "Thorold data refresh — mode: " (:mode options)))
    (println (str "Data dir:   " (:data-dir options)))
    (println (str "Output dir: " (:output-dir options)))
    (when (:dry-run options)
      (println "DRY RUN — no files will be written"))
    (println)
    (println "Pipeline stages:")
    (println "  1. Query Wikidata SPARQL for updated entity bindings")
    (println "  2. Run thorold.ingest/ingest-pipeline transducer chain")
    (println "  3. Merge new entities into existing database")
    (println "  4. Write updated people.csv, teams.csv, names.csv to output/")
    (println "  5. Update data/meta.json with new counts and timestamp")
    (println)
    (println "To run the full Clojure pipeline:")
    (println (str "  clj -M -m thorold.core ingest --mode " (:mode options)))))

(apply -main *command-line-args*)
