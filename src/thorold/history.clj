(ns thorold.history
  "Time-travel queries over immutable database snapshots.

   The database is a plain value, so multiple versions can be held
   simultaneously and compared structurally with no event-sourcing
   infrastructure.

   SCOPE: All functions operate on (:entities db) — the {reep-id → entity}
   map — never on the raw db map itself. This avoids duplicated diffs from
   derived indexes (by-provider, by-name, etc.) that are rebuilt from
   entities on every load."
  (:require [clojure.data :as data]))

;; ---------------------------------------------------------------------------
;; Snapshot diffing
;; ---------------------------------------------------------------------------

(defn diff-snapshots
  "Compares two database snapshots by their :entities maps.
   Returns {:added {reep-id entity} :removed {reep-id entity} :changed {reep-id entity}}.

   :added   — entities present in db-after but not db-before
   :removed — entities present in db-before but not db-after
   :changed — entities present in both but structurally different

   Uses clojure.data/diff on the :entities maps."
  [db-before db-after]
  (let [before (:entities db-before)
        after  (:entities db-after)
        [only-before only-after _both] (data/diff before after)]
    {:added   (reduce-kv (fn [acc id entity]
                           (if (contains? before id)
                             acc
                             (assoc acc id entity)))
                         {}
                         (or only-after {}))
     :removed (reduce-kv (fn [acc id entity]
                           (if (contains? after id)
                             acc
                             (assoc acc id entity)))
                         {}
                         (or only-before {}))
     :changed (reduce-kv (fn [acc id _]
                           (if (and (contains? before id)
                                    (contains? after id)
                                    (not= (get before id) (get after id)))
                             (assoc acc id (get after id))
                             acc))
                         {}
                         (or only-after {}))}))

;; ---------------------------------------------------------------------------
;; Provider ID tracking
;; ---------------------------------------------------------------------------

(defn new-provider-ids
  "Returns entities that gained a provider ID between two snapshots.
   Result: seq of entities from db-after that have the given provider
   but did not have it in db-before."
  [db-before db-after provider]
  (let [before (:entities db-before)
        after  (:entities db-after)]
    (into []
          (keep (fn [[reep-id entity]]
                  (let [had-before (some? (get-in before [reep-id :providers provider]))
                        has-after  (some? (get-in entity [:providers provider]))]
                    (when (and has-after (not had-before))
                      entity))))
          after)))

(defn lost-provider-ids
  "Returns entities that lost a provider ID between two snapshots.
   Result: seq of entities from db-before that had the given provider
   but do not have it in db-after."
  [db-before db-after provider]
  (let [before (:entities db-before)
        after  (:entities db-after)]
    (into []
          (keep (fn [[reep-id entity]]
                  (let [had-before (some? (get-in entity [:providers provider]))
                        has-after  (some? (get-in after [reep-id :providers provider]))]
                    (when (and had-before (not has-after))
                      entity))))
          before)))

;; ---------------------------------------------------------------------------
;; Entity history
;; ---------------------------------------------------------------------------

(defn entity-history
  "Given a seq of [timestamp db] pairs (snapshots) and a reep-id,
   returns the entity's state at each timestamp.
   Result: seq of {:timestamp t :entity entity-or-nil}."
  [snapshots reep-id]
  (mapv (fn [[timestamp db]]
          {:timestamp timestamp
           :entity    (get (:entities db) reep-id)})
        snapshots))

;; ---------------------------------------------------------------------------
;; Coverage delta
;; ---------------------------------------------------------------------------

(defn- count-provider
  "Counts how many entities have a given provider key set."
  [entities provider]
  (reduce-kv
   (fn [n _id entity]
     (if (some? (get (:providers entity) provider))
       (inc n)
       n))
   0
   entities))

(defn- all-provider-keys
  "Returns the set of all provider keywords across all entities."
  [entities]
  (reduce-kv
   (fn [acc _id entity]
     (into acc (keys (:providers entity))))
   #{}
   entities))

(defn coverage-delta
  "Per-provider before/after/delta counts.
   Returns {provider-kw {:before N :after M :delta (- M N)}}."
  [db-before db-after]
  (let [before (:entities db-before)
        after  (:entities db-after)
        all-providers (into (all-provider-keys before)
                            (all-provider-keys after))]
    (into {}
          (map (fn [provider]
                 (let [b (count-provider before provider)
                       a (count-provider after provider)]
                   [provider {:before b :after a :delta (- a b)}])))
          all-providers)))
