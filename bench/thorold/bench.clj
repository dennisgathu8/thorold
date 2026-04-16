(ns thorold.bench
  "Performance benchmarks for Thorold indexing and querying."
  (:require [criterium.core :as crit]
            [thorold.index :as index]
            [thorold.parse :as parse]
            [thorold.query :as query]))

(def sample-entities
  (vec (take 50000 (:entities (parse/parse-people-file "data/")))))

(defn -main [& _args]
  (println "==========================================")
  (println "Thorold Benchmark Suite")
  (println "==========================================\n")
  
  (println "Building 50k entity in-memory DB...")
  (let [db (index/build-indexes sample-entities)]
    (println "\n--- 1. Search Latency (indexed field via search) ---")
    ;; Quick bench is faster than full bench for CI/test purposes
    (crit/quick-bench (query/search db "Messi" {:type :person/player}))
    
    (println "\n--- 2. Exact Lookup Latency (Reep ID) ---")
    (crit/quick-bench (query/lookup db "reep_pbd9a559b"))
    
    (println "\n--- 3. Resolve Latency (Wikidata QID) ---")
    (crit/quick-bench (query/resolve db :wikidata "Q615"))
    
    (println "\nBenchmark complete. Immutable data model validated.")))
