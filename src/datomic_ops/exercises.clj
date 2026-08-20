(ns datomic-ops.exercises
  "THE INCIDENT — fill the gaps                    (after the break, ~45 min)
   ════════════════════════════════════════════════════════════════════
   Four exercises built on the operations class (datomic-ops.labs
   §1–§6). Every block has holes marked ___ . Replace each ___ so the
   form produces the result in its `;; =>` comment.

   Forgot one? Outside a quoted query it throws `Unable to resolve
   symbol: ___`; inside a quoted query the quote protects it and you
   get a Datomic error naming ___ instead. Either way the cue is loud.

   Solutions are at the very bottom (SOLUTIONS — scroll blind).

   ── Everything here runs on datomic:mem:// ────────────────────────
   No Docker, no transactor, no $DATOMIC — so the exercise does not
   depend on what each laptop has installed.

     clj -M:repl
     (require 'datomic-ops.exercises)
     (datomic-ops.exercises/start!)

   ── Tonight's incident ─────────────────────────────────────────────
   The readings service was fine at 08:00 and unusable at 09:10. You
   have the database and the REPL. In order:

     E1  ~8 min   READ THE EVIDENCE — what actually hit the writer
     E2 ~10 min   FIX THE WRITER    — the shape of a transaction
     E3 ~12 min   FIX THE READER    — cut the index, spend the cores
     E4 ~15 min   SHIP THE FIX      — warm, sync, and a rollout order

   Timings come from one rehearsal on a 10-core laptop, peer 1.0.7705,
   and will differ per machine. Where an exercise checks a duration it
   checks a ratio or a boolean rather than a millisecond count."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [___]}}}}
  (:require [datomic.api :as d]
            [datomic-ops.labs :as ops]
            [clojure.pprint :refer [print-table]]))

(def uri "datomic:mem://incident")

(defonce ^{:doc "The connection every exercise uses."} state (atom nil))

(defn conn [] @state)
(defn db   [] (d/db (conn)))

(defn start!
  "Rebuild the scene of the incident from scratch.

   Twelve normal batches of readings — the service running through the
   morning — and then the transaction that arrived at 09:00. Do not
   read that last transact closely; identifying what it did is E1."
  []
  (d/delete-database uri)
  (d/create-database uri)
  (let [c (d/connect uri)]
    (reset! state c)
    @(d/transact c ops/schema)
    (doseq [from (range 0 60000 5000)]
      @(d/transact c (ops/batch from (+ from 5000))))
    @(d/transact c (ops/batch 200000 220000))     ;; 09:00
    [:incident :ready :basis-t (d/basis-t (d/db c))]))

;; ═════════════════════════════════════════════════════════════════════
;; E1 · READ THE EVIDENCE — what actually hit the writer      (~8 min)
;; ═════════════════════════════════════════════════════════════════════
;;
;; Before tuning anything, establish what happened. In Datomic the log
;; is part of the database: every transaction that committed is still
;; there, in order, with its datoms.
;;
;; Two things you need:
;;   (d/log conn)               the log component of this database
;;   (d/tx-range log start end) transactions in [start, end); nil,nil
;;                              means "all of them". Each element is
;;                              {:t <basis-t> :data [<datoms>]}.
;;
;; Sizes are predictable here: one reading is four attributes, and each
;; transaction also asserts its own :db/txInstant.

(comment

  (start!)
  ;; => [:incident :ready :basis-t 61013]

  ;; ── E1.1 · How many transactions has this database ever seen? ─────
  (def entries
    (->> (d/tx-range (d/log (conn)) ___ ___)
         (mapv (fn [tx] [(:t tx) (count (:data tx))]))))

  (count entries)
  ;; => 14
  ;;    Twelve batches, one schema transaction, and one more. That last
  ;;    one is the finding; the next step identifies it precisely.

  ;; ── E1.2 · The transaction that broke the morning ─────────────────
  ;; Find the entry with the most datoms. `entries` is a seq of
  ;; [t datom-count] pairs, so you want the one whose SECOND element is
  ;; largest. There is a core function that does exactly this in one
  ;; pass, without sorting the whole collection — and, because it takes
  ;; its arguments one at a time rather than as a collection, it needs
  ;; one more core function in front of it. Three holes, three symbols.
  (def worst (___ ___ ___ entries))

  worst
  ;; => [61013 80001]

  ;; ── E1.3 · Put a number on it ─────────────────────────────────────
  ;; 80001 datoms. How many readings is that? Fill in the arithmetic —
  ;; four attributes per reading, plus the transaction's own txInstant.
  (let [[_ datoms] worst]
    (/ (- datoms ___) ___))
  ;; => 20000
  ;;
  ;; A normal batch is 5,000 readings; this one carried 20,000 in a
  ;; single transaction. Compare the profile:
  (print-table (map (fn [[t n]] {:t t :datoms n}) entries))

  ;; ── E1.4 · Which component absorbed it ────────────────────────────
  ;; A transaction is atomic, so the transactor holds all of it in the
  ;; memory index before any of it commits, and writers behind it wait.
  ;; Which component was affected?
  ;;   (fill in :peer, :transactor, :storage or :memcached)
  (def e1-answer ___)
  ;; => :transactor
  ;;
  ;; The peers were unaffected, and storage wrote the same bytes either
  ;; way. Oversized transactions are a write-path problem — the
  ;; memory-index loop from §5.

  )

;; ═════════════════════════════════════════════════════════════════════
;; E2 · FIX THE WRITER — the shape of a transaction          (~10 min)
;; ═════════════════════════════════════════════════════════════════════
;;
;; The importer has two failure modes, and this exercise measures both.
;;
;;   TOO BIG    one transaction of 20,000 — E1's incident.
;;   TOO SMALL  20,000 transactions of one reading each. Every
;;              transaction pays the full commit cost, and on real
;;              storage a round trip as well.
;;
;; Both come from the shape of the write, and both are controlled by the
;; same parameter: batch size.

(comment

  ;; ── E2.1 · The drip ───────────────────────────────────────────────
  ;; Write `n` readings as `n` separate transactions. Fill in the two
  ;; holes so each transaction carries exactly one reading.
  (defn drip! [base n]
    (first (ops/ms #(doseq [i (range n)]
                      @(d/transact (conn) (ops/batch ___ ___))))))

  ;; ── E2.2 · The bulk ───────────────────────────────────────────────
  ;; Same readings, `per` at a time.
  (defn bulk! [base n per]
    (first (ops/ms #(doseq [from (range 0 n per)]
                      @(d/transact (conn) (ops/batch (+ base from)
                                                     (+ base from ___)))))))

  ;; Warm the JIT before measuring, so the first measurement is not
  ;; simply the uncompiled one.
  (drip! 300000 200)
  (bulk! 310000 200 100)

  ;; ── E2.3 · The measurement ────────────────────────────────────────
  (def drip-ms (drip! 320000 2000))
  (def bulk-ms (bulk! 330000 2000 500))
  [drip-ms bulk-ms]
  ;; => [111.05 34.72]      ms — yours will differ, the direction won't

  (> (/ drip-ms bulk-ms) 2.0)
  ;; => true
  ;;
  ;; 2,000 transactions versus 4. On `datomic:mem://` there is no
  ;; network, so what this measures is per-transaction overhead alone.
  ;; Against a transactor over a socket, each of those 2,000 also pays a
  ;; round trip and the gap widens accordingly.

  ;; ── E2.4 · Choosing a size ────────────────────────────────────────
  ;; The right batch size depends on the data, but both bounds have now
  ;; been measured. Fill in the two ends of the trade-off:
  ;;   :latency :throughput :memory-index :round-trips
  (def e2-answer
    {:cost-of-batches-too-small ___      ;; what the drip wasted
     :cost-of-batches-too-large ___})    ;; what E1's 20,000 filled
  ;; => {:cost-of-batches-too-small :round-trips
  ;;     :cost-of-batches-too-large :memory-index}
  ;;
  ;; Between them is a size that keeps the transactor busy without
  ;; letting a single transaction dominate the memory index. For most
  ;; importers that falls between hundreds and a few thousand datoms.

  )

;; ═════════════════════════════════════════════════════════════════════
;; E3 · FIX THE READER — cut the index, spend the cores      (~12 min)
;; ═════════════════════════════════════════════════════════════════════
;;
;; The dashboard aggregates temperature per sensor across the whole
;; history, and has become slow. A single query is single-threaded
;; inside the peer, so parallelising this work means cutting it up
;; along the index.
;;
;; :reading/t is indexed, so d/index-range hands you any slice of AVET.
;; As noted in §0: index-range returns an Iterable, not a collection,
;; so `count` on it throws.

(comment

  ;; ── E3.1 · The unit of work ───────────────────────────────────────
  ;; Aggregate one slice: walk the range, resolve each datom's ENTITY
  ;; id, sum celsius per sensor. Fill in the accessor for a datom's
  ;; entity id, and the two attributes.
  (defn hot-sensors [db lo hi]
    (->> (d/index-range db :reading/t lo hi)
         (map #(d/entity db (___ %)))
         (reduce (fn [m e] (update m (___ e) (fnil + 0.0) (___ e)))
                 {})))

  (select-keys (hot-sensors (db) 0 7500) ["sensor-0"])
  ;; => {"sensor-0" 12671.0}

  ;; ── E3.2 · Cut the range into slices ──────────────────────────────
  ;; Eight slices over [0, 60000]. `partition 2 1` turns a sequence of
  ;; boundaries into overlapping pairs — fill in the step so you get
  ;; exactly eight slices.
  (def slices (partition 2 1 (range 0 60001 ___)))
  (count slices)
  ;; => 8

  ;; ── E3.3 · Serial, then parallel ──────────────────────────────────
  ;; Warm first, same reason as E2.
  (doall (map (fn [[lo hi]] (hot-sensors (db) lo hi)) slices))

  (def base-db (db))       ;; one immutable VALUE, shared by every slice
  (def serial-ms (first (ops/ms #(doall (map (fn [[lo hi]] (hot-sensors base-db lo hi)) slices)))))
  (def par-ms    (first (ops/ms #(doall (___ (fn [[lo hi]] (hot-sensors base-db lo hi)) slices)))))
  [serial-ms par-ms]
  ;; => [52.33 23.32]       ms — compare the ratio, not the values

  (> (/ serial-ms par-ms) 1.5)
  ;; => true

  ;; ── E3.4 · Why this is safe, and where it stops ───────────────────
  ;; Every slice read `base-db`. Fill in what that is:
  ;;   :a-connection :an-immutable-value :a-snapshot-transaction
  (def e3-answer ___)
  ;; => :an-immutable-value
  ;;
  ;; No snapshot to hold open, no repeatable-read flag, and no
  ;; transaction to leak.
  ;;
  ;; Now find the floor. Re-cut the same range into more, smaller slices
  ;; and measure each — 600, then 60, then 6 as the step:
  (let [tiny (partition 2 1 (range 0 60001 ___))]
    (doall (pmap (fn [[lo hi]] (hot-sensors base-db lo hi)) tiny))   ;; warm
    [(count tiny)
     (first (ops/ms #(doall (pmap (fn [[lo hi]] (hot-sensors base-db lo hi)) tiny))))])
  ;; => [10000 49.9]        with step 6
  ;;
  ;; Rehearsal, median of five runs (serial baseline: 47.9 ms):
  ;;
  ;;     slices        8      100     1000    10000
  ;;     pmap ms    16.7     18.7     20.9     49.9
  ;;
  ;; Coordination has a cost, so there is a minimum useful amount of
  ;; work per slice. Note the shape: gradual degradation rather than a
  ;; cliff, and only at 10,000 slices does the parallel version reach
  ;; the serial baseline. A single comparison would not have shown this.

  )

;; ═════════════════════════════════════════════════════════════════════
;; E4 · SHIP THE FIX — warm, sync, and a rollout order       (~15 min)
;; ═════════════════════════════════════════════════════════════════════
;;
;; The importer is batched and the dashboard is sliced. What remains is
;; deploying the change.

(comment

  ;; ── E4.1 · Warm before ready ──────────────────────────────────────
  ;; A readiness probe that succeeds at process start reports a peer
  ;; that is up but cold. Touch the ranges this service reads, then
  ;; report ready. Fill in the wrapper that makes an Iterable
  ;; countable.
  (defn warm! [conn]
    (let [d (d/db conn)]
      (doall (pmap (fn [[lo hi]] (count (___ (d/index-range d :reading/t lo hi))))
                   (partition 2 1 (range 0 60001 7500))))
      {:ready? true :basis-t (d/basis-t d)}))

  (second (ops/ms #(warm! (conn))))
  ;; => {:ready? true, :basis-t 87119}      (basis-t depends on E2/E3)
  ;;
  ;; ~1 ms in mem, because mem has no storage to wait on. On a real
  ;; database with a cold peer this takes seconds; warming moves that
  ;; cost ahead of the first request.

  ;; ── E4.2 · Do not read behind your own write ──────────────────────
  ;; A peer that just started sits at whatever t storage gave it. If a
  ;; request arrives right after a write that went through a DIFFERENT
  ;; peer, this one can serve a database that does not contain it.
  ;;
  ;; A fixed sleep does not solve this. Fill in the function that waits
  ;; for a specific t, and the one that reports where a db is.
  (let [t (d/basis-t (:db-after @(d/transact (conn) [{:reading/t 999999}])))]
    [t (___ (deref (d/___ (conn) t) 5000 nil))])
  ;; => [87620 87620]
  ;;
  ;; Two equal numbers: the request read exactly the database its own
  ;; write produced. Passing the t is the consistency contract of a
  ;; multi-peer deployment.
  ;;
  ;; Bonus, for anyone who finishes early: which function would you use
  ;; instead if the read needed the INDEX, not just the log? (§5.)

  ;; ── E4.3 · The rollout order ──────────────────────────────────────
  ;; Put these four steps in the order you would actually run them.
  ;; Replace each ___ with 1, 2, 3 or 4.
  (def e4-rollout
    {:roll-peers-in-waves        ___
     :ship-the-new-schema        ___
     :warm-then-report-ready     ___
     :roll-the-transactor        ___})
  ;; => {:ship-the-new-schema    1
  ;;     :roll-peers-in-waves    2
  ;;     :warm-then-report-ready 3
  ;;     :roll-the-transactor    4}
  ;;
  ;; Schema first, because Datomic schema is additive: old peers ignore
  ;; attributes they do not know, so there is no lock, no ALTER and no
  ;; migration window. Peers in waves, to avoid a simultaneous cold-cache
  ;; burst against storage. Warm before ready, so a live wave is useful.
  ;; The transactor last, since rolling it costs a bounded write pause
  ;; and the rest of the system should be stable first.

  ;; ── E4.4 · The page you would write ───────────────────────────────
  ;; From §1's failure map: for each symptom, which component is it?
  ;;   :peer :transactor :storage :memcached
  (def e4-triage
    {"writes fail, reads fine"                    ___
     "everything is down"                         ___
     "one instance slow, the others fine"         ___
     "nothing failed but storage load doubled"    ___})
  ;; => {"writes fail, reads fine"                 :transactor
  ;;     "everything is down"                      :storage
  ;;     "one instance slow, the others fine"      :peer
  ;;     "nothing failed but storage load doubled" :memcached}
  ;;
  ;; The settings in §5 are lookups; this table is the model they
  ;; operate on.

  ;; Done. Clean up:
  (d/delete-database uri)

  )

;; ═════════════════════════════════════════════════════════════════════
;; ═════════════════════════════════════════════════════════════════════
;;
;;   SOLUTIONS — read these after attempting every ___ above. Timings
;;   are from one rehearsal; only the ratios and the structural results
;;   should match.
;;
;; ═════════════════════════════════════════════════════════════════════
;; ═════════════════════════════════════════════════════════════════════

(comment

  ;; ── E1 ────────────────────────────────────────────────────────────
  (def entries
    (->> (d/tx-range (d/log (conn)) nil nil)
         (mapv (fn [tx] [(:t tx) (count (:data tx))]))))
  (count entries)                       ;; => 14
  (def worst (apply max-key second entries))
  worst                                 ;; => [61013 80001]
  (let [[_ datoms] worst]
    (/ (- datoms 1) 4))                 ;; => 20000
  (def e1-answer :transactor)

  ;; ── E2 ────────────────────────────────────────────────────────────
  (defn drip! [base n]
    (first (ops/ms #(doseq [i (range n)]
                      @(d/transact (conn) (ops/batch (+ base i) (+ base i 1)))))))
  (defn bulk! [base n per]
    (first (ops/ms #(doseq [from (range 0 n per)]
                      @(d/transact (conn) (ops/batch (+ base from)
                                                     (+ base from per)))))))
  (drip! 300000 200) (bulk! 310000 200 100)
  (def drip-ms (drip! 320000 2000))     ;; => 111.05
  (def bulk-ms (bulk! 330000 2000 500)) ;; => 34.72
  (> (/ drip-ms bulk-ms) 2.0)           ;; => true
  (def e2-answer {:cost-of-batches-too-small :round-trips
                  :cost-of-batches-too-large :memory-index})

  ;; ── E3 ────────────────────────────────────────────────────────────
  (defn hot-sensors [db lo hi]
    (->> (d/index-range db :reading/t lo hi)
         (map #(d/entity db (:e %)))
         (reduce (fn [m e]
                   (update m (:reading/sensor e) (fnil + 0.0) (:reading/celsius e)))
                 {})))
  (select-keys (hot-sensors (db) 0 7500) ["sensor-0"])  ;; => {"sensor-0" 12671.0}
  (def slices (partition 2 1 (range 0 60001 7500)))
  (count slices)                        ;; => 8
  (doall (map (fn [[lo hi]] (hot-sensors (db) lo hi)) slices))
  (def base-db (db))
  (def serial-ms (first (ops/ms #(doall (map  (fn [[lo hi]] (hot-sensors base-db lo hi)) slices)))))
  (def par-ms    (first (ops/ms #(doall (pmap (fn [[lo hi]] (hot-sensors base-db lo hi)) slices)))))
  (> (/ serial-ms par-ms) 1.5)          ;; => true
  (def e3-answer :an-immutable-value)
  (let [tiny (partition 2 1 (range 0 60001 6))]   ;; the floor: 10,000 slices
    (doall (pmap (fn [[lo hi]] (hot-sensors base-db lo hi)) tiny))
    [(count tiny)
     (first (ops/ms #(doall (pmap (fn [[lo hi]] (hot-sensors base-db lo hi)) tiny))))])

  ;; ── E4 ────────────────────────────────────────────────────────────
  (defn warm! [conn]
    (let [d (d/db conn)]
      (doall (pmap (fn [[lo hi]] (count (seq (d/index-range d :reading/t lo hi))))
                   (partition 2 1 (range 0 60001 7500))))
      {:ready? true :basis-t (d/basis-t d)}))
  (second (ops/ms #(warm! (conn))))

  (let [t (d/basis-t (:db-after @(d/transact (conn) [{:reading/t 999999}])))]
    [t (d/basis-t (deref (d/sync (conn) t) 5000 nil))])
  ;; Bonus: d/sync-index, when the read must see the INDEX at that t.

  (def e4-rollout {:ship-the-new-schema    1
                   :roll-peers-in-waves    2
                   :warm-then-report-ready 3
                   :roll-the-transactor    4})

  (def e4-triage
    {"writes fail, reads fine"                    :transactor
     "everything is down"                         :storage
     "one instance slow, the others fine"         :peer
     "nothing failed but storage load doubled"    :memcached})

  )
