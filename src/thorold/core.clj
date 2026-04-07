(ns thorold.core
  "Entry point: wires together db load, CLI dispatch, API start.

   Usage:
     clj -M -m thorold.core search \"Lionel Messi\"
     clj -M -m thorold.core api
     clj -M:api"
  (:require [thorold.cli :as cli]
            [thorold.api :as api]
            [thorold.db :as db])
  (:gen-class))

(defn -main
  [& argv]
  (let [command (first argv)]
    (if (= command "api")
      ;; Start API server
      (let [data-dir (or (System/getenv "THOROLD_DATA_DIR") "data/")
            port     (Integer/parseInt (or (System/getenv "PORT") "8080"))
            db       (db/load-db data-dir)
            server   (api/start-server db :port port)]
        ;; Block the main thread
        (.join (Thread/currentThread)))
      ;; CLI mode
      (apply cli/-main argv))))
