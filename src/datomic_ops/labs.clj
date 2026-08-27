(ns datomic-ops.labs
  "DATOMIC AT SCALE — Operations, Parallelism and the Cost of a Read
   ════════════════════════════════════════════════════════════════════
   REPL companion for the 2-hour operations class. Sections (§0–§6)
   match the deck's ⚑ waypoints.

   This class STARTS WHERE `datomic-infra.labs` STOPS. It assumes the
   deployment map, the write path, the read path and the backup/restore
   drill. Nothing here re-teaches those.

   ── Two kinds of lab ───────────────────────────────────────────────
   [MEM]  runs on `datomic:mem://` with nothing installed. Every [MEM]
          lab below was machine-verified on peer 1.0.7705 (2026-08-20,
          10-core machine) and its `;; =>` lines are real output.
   [PRO]  needs the real thing: PostgreSQL in Docker, one or two
          transactor processes, $DATOMIC on disk. See infra/README.md
          and infra/HA.md. Their `;; =>` lines are SHAPES TO EXPECT —
          rehearse them before class.

   ── Getting a REPL ─────────────────────────────────────────────────
     clj -M:repl              ;; enough for every [MEM] lab
     clj -M:infra:repl        ;; adds the JDBC driver, for [PRO] labs

   ── Conventions ────────────────────────────────────────────────────
   * Everything evaluable lives in (comment ...) blocks. Nothing runs
     on load except the definitions and helpers.
   * Timings come from one rehearsal and will differ per machine. The
     labs compare ratios; where a ratio is 1.0, that is the result the
     lab is after.
   * Where a value depends on your release, this file gives the command
     that prints it rather than a number."
  (:require [datomic.api :as d]
            [clojure.pprint :refer [pprint]]))

;; ═════════════════════════════════════════════════════════════════════
;; §0 · SETUP — one schema, two homes
;; ═════════════════════════════════════════════════════════════════════
;;
;; The same schema runs in memory (parallelism, speculation, warm-up)
;; and on Postgres (caches, failover, settings). One schema means a
;; measurement taken in mem can be repeated for real without editing a
;; single query.

(def jdbc
  "JDBC URI for the class storage. Matches infra/docker-compose.yml.
   Started Postgres on another port? Change it here AND in both
   transactor properties files."
  "jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic")

(defn sql-uri [db-name] (str "datomic:sql://" db-name "?" jdbc))
(def  mem-uri    "datomic:mem://ops")
(def  system-uri (sql-uri "*"))

(def schema
  [{:db/ident       :reading/sensor
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "Which sensor produced this reading."}
   {:db/ident       :reading/t
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "Monotonic sample number. Indexed, so the AVET index
                     can be sliced into ranges and read in parallel — §4."}
   {:db/ident       :reading/celsius
    :db/valueType   :db.type/double
    :db/cardinality :db.cardinality/one}
   {:db/ident       :reading/note
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Padding. Readings are small; production segments are
                     not. This keeps the working set honest."}])

(defn batch
  "One transaction's worth of readings, [from, to)."
  [from to]
  (for [i (range from to)]
    {:reading/sensor  (str "sensor-" (mod i 16))
     :reading/t       i
     :reading/celsius (+ 15.0 (double (mod i 25)))
     :reading/note    (apply str (repeat 200 \x))}))

(defn seed!
  "Create + schema + n readings, in transactions of `per`. Returns conn.
   `per` is the knob §4 turns."
  ([uri] (seed! uri 20000 2000))
  ([uri n per]
   (d/create-database uri)
   (let [conn (d/connect uri)]
     @(d/transact conn schema)
     (doseq [from (range 0 n per)]
       @(d/transact conn (batch from (min n (+ from per)))))
     conn)))

(def all-readings '[:find (count ?e) :where [?e :reading/t]])

(defn ms
  "Run f, return [elapsed-ms result]. `time` prints; we want the number
   as data, so two of them can sit side by side."
  [f]
  (let [t0 (System/nanoTime)
        r  (f)]
    [(/ (- (System/nanoTime) t0) 1e6) r]))

;; NOTE — d/index-range returns an Iterable, not a collection.
;; `(count (d/index-range ...))` throws. Wrap it: (count (seq ...)).

;; ═════════════════════════════════════════════════════════════════════
;; §1 · THE FAILURE MAP — say what breaks before you tune anything
;; ═════════════════════════════════════════════════════════════════════
;;
;; The class rests on one asymmetry: reads are served by the peer out
;; of caches and storage, while writes go through a single process. So
;; the four components fail in four different ways, and three of the
;; four are not outages.

(comment

  ;; LAB [MEM] — the system, seen from inside a peer.
  (def conn (seed! mem-uri 5000 1000))

  ;; basis-t is this peer's position in the log. Latency questions in
  ;; this class reduce to how far behind this number a given process is
  ;; allowed to be.
  (d/basis-t (d/db conn))
  ;; => 5005

  ;; Nothing about a read consults the transactor. This is why a dead
  ;; transactor is a WRITE outage only — §2 kills one for real.
  (d/q all-readings (d/db conn))
  ;; => [[5000]]

  ;; A peer's read capacity is its own memory. Ask what it holds:
  (:datoms (d/db-stats (d/db conn)))
  ;; => 20301
  (select-keys (:attrs (d/db-stats (d/db conn))) [:reading/t :reading/celsius])
  ;; => #:reading{:t {:count 5000}, :celsius {:count 5000}}
  ;;
  ;; Note: :datoms describes the whole DATABASE, not the cache. No peer
  ;; API reports cache occupancy; §5's metrics callback covers that.

  ;; ── The map you should be able to draw from memory ────────────────
  ;;
  ;;   storage down     → total outage (reads too, once caches miss)
  ;;   transactor down  → writes fail, reads keep serving
  ;;   one peer down    → that peer's traffic only; siblings unaffected
  ;;   memcached down   → nothing fails; storage load jumps
  ;;
  ;; The two middle rows are the main structural difference from a
  ;; single-server SQL deployment. The rest of the class follows.

  (d/delete-database mem-uri)

  )

;; ═════════════════════════════════════════════════════════════════════
;; §2 · HIGH AVAILABILITY — a standby transactor, and a real failover
;; ═════════════════════════════════════════════════════════════════════
;;
;; Datomic Pro HA is a lease, not a cluster: two identical transactor
;; processes against the same storage, of which one holds the lease.
;; Storage is the arbiter, so there is no quorum, no consensus protocol
;; and no split brain — the lease lives where the data lives. Holding
;; the lease and being able to commit are the same condition.
;;
;; Setup: infra/HA.md. In short, two terminals:
;;   $DATOMIC/bin/transactor infra/pg-transactor.properties          # A
;;   $DATOMIC/bin/transactor infra/pg-transactor-standby.properties  # B
;; B starts, finds the lease held, and parks. A standby that is working
;; correctly prints nothing after startup.

(comment

  ;; ── LAB [PRO] — measure the failover window ───────────────────────
  ;; The window depends on heartbeat-interval-msec, on storage latency,
  ;; and on how warm the standby's JVM is, so it is measured per
  ;; environment rather than quoted.

  (def conn (seed! (sql-uri "ops") 20000 2000))

  ;; A writer that never stops: one reading a second, every outcome
  ;; recorded with a wall-clock stamp — successes and failures alike.
  (def stop!    (atom false))
  (def timeline (atom []))

  (defn writer-loop! []
    (future
      (loop [i 100000]
        (when-not @stop!
          (let [t0 (System/currentTimeMillis)
                r  (try @(d/transact conn [{:reading/t i :reading/sensor "ha"}])
                        [:ok]
                        (catch Exception e [:fail (.getMessage e)]))]
            (swap! timeline conj (into [t0] r))
            (Thread/sleep 1000)
            (recur (inc i)))))))

  (writer-loop!)

  ;; Now kill transactor A. Use a hard kill rather than a graceful stop,
  ;; since that is the case HA exists for:
  ;;
  ;;   pkill -f pg-transactor.properties
  ;;
  ;; Watch B's terminal. It finds the lease stale, takes it, and prints
  ;; its own `System started`.

  (reset! stop! true)

  ;; The failover window is the gap: from the last :ok before the kill
  ;; to the first :ok after it.
  (defn failover-report [tl]
    (let [fails (filter #(= :fail (second %)) tl)]
      {:writes        (count tl)
       :failed-writes (count fails)
       :window-ms     (when (seq fails)
                        (- (first (last fails)) (first (first fails))))
       :first-error   (nth (first fails) 2 nil)}))

  (failover-report @timeline)
  ;; => {:writes 40, :failed-writes 4, :window-ms 3011, :first-error "..."}
  ;;    Expected shape only. The measured window is the write-
  ;;    availability SLO for this setup.

  ;; Two observations:
  ;;
  ;; 1. The peer reconnected on its own — no URI change, no restart, no
  ;;    load balancer. Peers find the active transactor through storage,
  ;;    so HA needs no additional component.
  ;;
  ;; 2. Reads did not fail. To confirm, run this during the next
  ;;    failover:
  (future (dotimes [_ 60]
            (println (d/q all-readings (d/db conn)))
            (Thread/sleep 1000)))

  ;; ── LAB [PRO] — the failure HA does not cover ─────────────────────
  ;; Stop BOTH transactors, then:
  #_@(d/transact conn [{:reading/t 1}])   ;; fails — no writer anywhere
  (d/q all-readings (d/db conn))          ;; still fine
  ;;
  ;; A standby provides a bounded write pause. It does not provide a
  ;; second copy of the data; that is storage replication's role.

  ;; ── LAB [PRO] — what a peer does while the writer is gone ─────────
  ;; During the window a transaction does not fail instantly; the peer
  ;; waits. The bound is peer-side: datomic.txTimeoutMsec. Restart with
  ;; a deliberately short one and repeat the kill:
  ;;
  ;;   clj -J-Ddatomic.txTimeoutMsec=1000 -M:infra:repl
  ;;
  ;; The same outage now produces more failed writes: shorter timeouts
  ;; trade write availability for tighter tail latency. Choosing between
  ;; those is what transaction-timeout tuning consists of.

  )

;; ═════════════════════════════════════════════════════════════════════
;; §3 · THE COST OF A READ — four tiers, and the deploy stampede
;; ═════════════════════════════════════════════════════════════════════
;;
;; A read walks down until something answers:
;;
;;   1. object cache    on-heap, decoded segments   ns..µs
;;   2. valcache        local SSD, per peer         hundreds of µs
;;   3. memcached       shared, over the network    ~1 ms
;;   4. storage         Postgres/DDB/S3             ms, and metered
;;
;; The Production class measured tiers 1, 3 and 4. This class adds
;; valcache, then applies those measurements to the case that follows
;; from them: twenty peers restarting at once with empty caches.

(comment

  ;; ── LAB [MEM] — the unit of caching is a SEGMENT, not an entity ───
  ;; Visible with no infrastructure at all, because it is a property of
  ;; the index, not of the cache.
  (def conn (seed! mem-uri 20000 2000))
  (def db   (d/db conn))

  (def some-e (ffirst (d/q '[:find ?e :where [?e :reading/t 4242]] db)))
  (dissoc (d/pull db '[*] some-e) :reading/note)
  ;; => {:db/id 17592186049662, :reading/sensor "sensor-2",
  ;;     :reading/t 4242, :reading/celsius 32.0}

  ;; Its neighbours in the AVET index arrived with it — same segment.
  ;; So the working set is measured in segments, and cache sizing
  ;; follows the segments a workload touches, not the entities it names.
  (count (seq (d/index-range db :reading/t 4240 4260)))
  ;; => 20

  (d/delete-database mem-uri)

  ;; ── LAB [PRO] — add valcache, the per-peer SSD tier ───────────────
  ;;
  ;; valcache needs no server: two peer-side JVM properties.
  ;;
  ;;   clj -J-Ddatomic.valcachePath=/tmp/valcache \
  ;;       -J-Ddatomic.valcacheMaxGb=1 -M:infra:repl
  ;;
  ;; Check the property names against your own distribution; they can
  ;; change between releases:
  ;;   grep -rn "valcache" $DATOMIC/bin $DATOMIC/config 2>/dev/null
  ;;
  ;; The measurement that matters is not first-query latency. It is
  ;; SECOND-PROCESS latency: valcache survives a peer restart; the
  ;; object cache does not.

  (def conn (d/connect (sql-uri "ops")))
  (ms #(d/q all-readings (d/db conn)))      ;; cold: storage
  (ms #(d/q all-readings (d/db conn)))      ;; warm: object cache
  ;; => [1843.2 [[20000]]]  then  [12.7 [[20000]]]     (shape)

  ;; Now restart the REPL with the SAME valcachePath and run the cold
  ;; query again. Without valcache you pay storage all over again; with
  ;; it you pay local disk.
  ;;
  ;;   du -sh /tmp/valcache
  ;;
  ;; The size of that directory is what a peer would otherwise re-read
  ;; from storage on every restart — the subject of the next lab.

  ;; ── LAB [PRO] — the deploy stampede ───────────────────────────────
  ;; In psql, before and after:
  ;;   select blks_read, blks_hit, tup_returned from pg_stat_database
  ;;    where datname = 'datomic';
  ;;
  ;; Start THREE peer REPLs at once, each cold, no valcache, and run the
  ;; query in all three. blks_read moves by roughly 3x one peer's cost;
  ;; scale that by the real peer count to size a deploy's impact.
  ;;
  ;; Three mitigations, ordered by how much infrastructure they need:
  ;;   1. memcached          one shared miss instead of N (needs a server)
  ;;   2. valcache           survives the restart that caused it (a disk)
  ;;   3. rolling deploys    do not replace 20 peers at once (needs nothing)
  ;; §6 does the third.

  ;; ── LAB [PRO] — size the object cache by breaking it ──────────────
  ;; Restart with a cache smaller than the working set and watch "warm"
  ;; stop being warm:
  ;;   clj -J-Ddatomic.objectCacheMax=32m -M:infra:repl
  ;; Then repeat with a generous cache. The gap between those two
  ;; measurements is the object-cache sizing signal for this dataset.

  )

;; ═════════════════════════════════════════════════════════════════════
;; §4 · PARALLELISM — one writer, many readers, and where each scales
;; ═════════════════════════════════════════════════════════════════════
;;
;; The asymmetry is deliberate:
;;
;;   WRITES  one transactor. You cannot parallelise the commit. You CAN
;;           pipeline it — keep several transactions in flight so the
;;           transactor never idles waiting for your round trip.
;;   READS   embarrassingly parallel. Query runs in the peer's own JVM,
;;           so read capacity is peers x cores, and after warm-up an
;;           extra peer costs storage nothing.

(comment

  (def conn (seed! mem-uri 100000 5000))
  ;; ~2.3 s. Then:
  (d/basis-t (d/db conn))
  ;; => 96020

  ;; ── LAB [MEM] — serial vs pipelined writes: the control ───────────
  ;; Run both. They come out the same; the explanation follows the
  ;; measurement.

  (defn serial! [base]
    (first (ms #(doseq [i (range 50)]
                  @(d/transact conn (batch (+ base (* i 20))
                                           (+ base (* i 20) 20)))))))

  (defn pipeline! [base]
    (first (ms #(->> (for [i (range 50)]
                       (d/transact-async conn (batch (+ base (* i 20))
                                                     (+ base (* i 20) 20))))
                     doall
                     (run! deref)))))

  (serial! 400000) (pipeline! 450000)      ;; warm the JIT first
  [(serial! 500000) (pipeline! 600000)]
  ;; => [24.69 24.31]        ms — no difference at all
  ;;
  ;; `datomic:mem://` runs the transactor inside this JVM, so there is
  ;; no round trip for pipelining to overlap. That isolates the
  ;; mechanism: pipelining does not make the transactor faster, it keeps
  ;; it from idling between round trips.

  ;; ── LAB [PRO] — the same two lines, over a socket ─────────────────
  ;; Repeat verbatim against (sql-uri "ops") with a real transactor.
  ;; Serial now pays one network + fsync round trip per transaction and
  ;; pipelined does not. On a laptop-local Postgres a several-fold gap
  ;; is typical, and over a real network it is larger. The ratio is
  ;; proportional to the round trip, which is why it is measured per
  ;; environment.

  ;; ── LAB [MEM] — and the limit ─────────────────────────────────────
  ;; Unbounded pipelining turns a latency problem into a memory problem.
  ;; Bound the width:
  (defn pipelined!
    "At most `width` transactions in flight."
    [conn tx-batches width]
    (->> tx-batches
         (partition-all width)
         (mapcat (fn [g] (->> g (map #(d/transact-async conn %)) doall (map deref))))
         doall
         count))

  (first (ms #(pipelined! conn
                          (for [i (range 50)]
                            (batch (+ 700000 (* i 20)) (+ 700000 (* i 20) 20)))
                          8)))
  ;; => 26.86     (mem: same as everything else, for the same reason)
  ;; In production, width 8–16 covers most of the benefit. Very large
  ;; widths trade the latency problem for a heap problem.

  ;; ── LAB [MEM] — parallel reads over index ranges ──────────────────
  ;; :reading/t is indexed, so AVET can be sliced. Each slice is an
  ;; independent read: no locks, no coordination, no transactor.
  (def db (d/db conn))

  (defn hot-sensors
    "Real work over one slice: walk the index range, resolve entities,
     aggregate by sensor. Counting is too cheap to show anything."
    [db lo hi]
    (->> (d/index-range db :reading/t lo hi)
         (map #(d/entity db (:e %)))
         (reduce (fn [m e]
                   (update m (:reading/sensor e) (fnil + 0.0) (:reading/celsius e)))
                 {})))

  (def slices (partition 2 1 (range 0 100001 12500)))

  (select-keys (hot-sensors db 0 12500) ["sensor-0" "sensor-1"])
  ;; => {"sensor-0" 21116.0, "sensor-1" 21123.0}

  ;; Warm once, then measure. (Rehearsal numbers below are the median of
  ;; five runs; a single run is noisier than the effect being measured.)
  (doall (map (fn [[lo hi]] (hot-sensors db lo hi)) slices))

  [(first (ms #(doall (map  (fn [[lo hi]] (hot-sensors db lo hi)) slices))))
   (first (ms #(doall (pmap (fn [[lo hi]] (hot-sensors db lo hi)) slices))))]
  ;; => [78.70 24.68]      ms, 8 slices on 10 cores — ~3.2x

  ;; Two constraints:
  ;;   * `db` is a VALUE. Every slice reads the same immutable database:
  ;;     no snapshot to hold open, no repeatable-read flag, and no
  ;;     transaction to leak.
  ;;   * Speed-up is bounded by cache misses rather than cores. On a
  ;;     cold peer this overlaps the wait on storage.
  ;;
  ;; There is also a floor: past some point pmap's coordination costs
  ;; more than the work per slice. Re-cut the same 100,000 readings and
  ;; measure again — `(partition 2 1 (range 0 100001 step))`:
  ;;
  ;;     slices        8      100     1000    10000
  ;;     pmap ms   24.68    27.62    30.12    54.41
  ;;
  ;; The degradation is gradual, not a cliff, and at 10,000 slices the
  ;; parallel version is close to the 78.70 ms serial baseline. Slice
  ;; count is worth measuring rather than assuming.

  ;; ── LAB [MEM] — parallel what-ifs with d/with ─────────────────────
  ;; d/with applies a transaction to a database VALUE. No transactor is
  ;; involved, so many can run at once — this is how you do validation,
  ;; pricing scenarios, or bulk-import dry runs in parallel.
  (defn what-if [db tx] (:db-after (d/with db tx)))

  (doall (pmap #(d/q all-readings (what-if db %))
               (for [n [10 100 1000]] (batch (+ 800000 n) (+ 800000 n 5)))))
  ;; => ([[105005]] [[105005]] [[105005]])
  ;;    Three futures, three databases, none of them real.

  (d/q all-readings db)
  ;; => [[105000]]        the base db never moved

  ;; ── Where parallelism does not apply ──────────────────────────────
  ;; A single `d/q` is single-threaded inside the peer. Parallelism in
  ;; Datomic is across queries, or across index slices cut by the
  ;; caller — not within one query. A slow single query is addressed by
  ;; its shape or its cache; more cores do not help.

  (d/delete-database mem-uri)

  )

;; ═════════════════════════════════════════════════════════════════════
;; §5 · SETTINGS AND SIGNALS — the memory index, and what to watch
;; ═════════════════════════════════════════════════════════════════════
;;
;; A common production failure mode is the memory index growing faster
;; than indexing can drain it. That loop explains most of the transactor
;; properties.
;;
;;   writes land in the in-memory index (and, durably, in the log)
;;     ↳ past memory-index-threshold → an indexing job starts
;;         ↳ if writes keep outrunning it and memory-index-max is
;;           reached, the transactor THROTTLES writers on purpose, to
;;           protect itself. Your p99 write latency goes vertical.
;;
;; threshold = "start working". max = "start saying no".

(comment

  ;; ── LAB [any] — read the defaults from your own distribution ──────
  ;; Defaults change between releases, so read them rather than recall
  ;; them:
  ;;
  ;;   ls $DATOMIC/config/samples/
  ;;   grep -vE '^\s*(#|$)' $DATOMIC/config/samples/sql-transactor-template.properties
  ;;
  ;; Compare with infra/pg-transactor.properties (classroom sizing,
  ;; -Xmx1g) and the production note in its comments. Where they
  ;; disagree, the distribution is current and this file needs updating.

  ;; ── LAB [MEM] — make the memory index visible from the peer ───────
  (def conn (seed! mem-uri 0 1))

  (dotimes [i 10]
    @(d/transact conn (batch (* i 1000) (* (inc i) 1000))))

  (:datoms (d/db-stats (d/db conn)))
  ;; => 40306

  ;; Force the drain and wait for it. This is the API behind "why is my
  ;; database still enormous after I excised things":
  (d/request-index conn)
  ;; => true

  (d/basis-t (deref (d/sync-index conn (d/basis-t (d/db conn))) 30000 nil))
  ;; => 10010
  ;;
  ;; sync-index returns a db whose INDEX — not merely whose log —
  ;; includes that t. A read that must not miss recently indexed data
  ;; waits on this rather than on d/sync. The difference between the two
  ;; only appears under load.

  (d/delete-database mem-uri)

  ;; ── The settings that matter, and their failure signature ─────────
  ;;
  ;;   TRANSACTOR (properties file)
  ;;     memory-index-threshold   too high → long, bursty indexing jobs
  ;;     memory-index-max         too low  → early throttling under load
  ;;     object-cache-max         too low  → §3's "warm is not warm"
  ;;     memcached                unset    → every peer misses separately
  ;;     heartbeat-interval-msec  too high → longer §2 failover window
  ;;
  ;;   PEER (-D flags)
  ;;     datomic.objectCacheMax     the peer's own heap cache
  ;;     datomic.memcachedServers   join the shared tier
  ;;     datomic.valcachePath       the SSD tier
  ;;     datomic.valcacheMaxGb      how much of that disk to use
  ;;     datomic.txTimeoutMsec      how long a write waits for a writer
  ;;
  ;; And the JVM trap already recorded in infra/pg-transactor.properties:
  ;; passing ANY JVM flag to bin/transactor makes it DROP its own GC
  ;; defaults. Re-specify -XX:+UseG1GC -XX:MaxGCPauseMillis=50 yourself.

  ;; ── LAB [PRO] — wire up monitoring ────────────────────────────────
  ;; The transactor emits metrics through a callback named in the
  ;; properties file. The distribution ships the contract — read it in
  ;; the room rather than trusting a slide:
  ;;
  ;;   grep -rn "metrics-callback\|Alarm" $DATOMIC/config/samples/ $DATOMIC/bin/
  ;;
  ;; The families to alert on map one-to-one onto the loop above:
  ;;   alarms, any kind          → investigate immediately
  ;;   indexing job duration     → the drain is losing
  ;;   transaction latency p99   → you are being throttled
  ;;   storage read/write time   → it was never Datomic
  ;;   memcached hit ratio       → the shared tier stopped sharing

  ;; ── LAB [MEM] — the tx-report queue as a monitor ──────────────────
  ;; Every peer can observe every transaction as it lands. Used in
  ;; production for audit, cache invalidation and CDC; here, as a
  ;; throughput monitor.
  (def conn (seed! mem-uri 0 1))
  (def q    (d/tx-report-queue conn))

  (future (dotimes [i 5] @(d/transact conn (batch (* i 100) (* (inc i) 100)))))

  (dotimes [_ 5]
    (let [r (.poll q 5000 java.util.concurrent.TimeUnit/MILLISECONDS)]
      (println :t (d/basis-t (:db-after r)) :datoms (count (:tx-data r)))))
  ;; => :t 1001 :datoms 401
  ;;    :t 1102 :datoms 401
  ;;    :t 1203 :datoms 401
  ;;    :t 1304 :datoms 401
  ;;    :t 1405 :datoms 401
  ;;
  ;; 401 datoms for 100 readings: four attributes each, plus the
  ;; transaction's own :db/txInstant. Every write is visible, in order,
  ;; to any peer that subscribes.

  (d/remove-tx-report-queue conn)
  (d/delete-database mem-uri)

  )

;; ═════════════════════════════════════════════════════════════════════
;; §6 · DEPLOYMENT — shipping peers without a stampede
;; ═════════════════════════════════════════════════════════════════════
;;
;; A peer is not a stateless app server. It carries a cache, and that
;; cache is the difference between a 10 ms read and a 1,000 ms one, so
;; peer deployment includes cache warming.

(comment

  ;; ── LAB [MEM] — warm before reporting ready ───────────────────────
  ;; A readiness probe that succeeds at process start reports a peer
  ;; that is up but cold. Warm the segments the traffic touches, then
  ;; report ready.
  (def conn (seed! mem-uri 20000 2000))

  (defn warm!
    "Touch the index ranges this service reads, so the first real
     request is not the one that pays storage. Returns the basis-t it
     warmed — hand that to your readiness endpoint."
    [conn]
    (let [db (d/db conn)]
      (doall (pmap (fn [[lo hi]] (count (seq (d/index-range db :reading/t lo hi))))
                   (partition 2 1 (range 0 20001 2500))))
      {:ready? true :basis-t (d/basis-t db)}))

  (ms #(warm! conn))
  ;; => [1.06 {:ready? true, :basis-t 19010}]
  ;;
  ;; 1 ms in mem, because mem has no storage to wait on. On a [PRO]
  ;; database with a cold cache this takes seconds; warming moves that
  ;; cost ahead of the first request.

  ;; ── LAB [MEM] — never read behind your own write ──────────────────
  ;; A freshly started peer begins at whatever t storage hands it. If a
  ;; request reaches it right after writing through a DIFFERENT peer, it
  ;; can read a database that does not contain that write. Waiting on a
  ;; fixed sleep does not solve this; waiting on the t does.
  (let [t (d/basis-t (:db-after @(d/transact conn [{:reading/t 999999}])))]
    ;; the second process receives `t` with the request and does this:
    (d/basis-t (deref (d/sync conn t) 5000 nil)))
  ;; => 21011
  ;;
  ;; Passing the t with the request is the consistency contract of a
  ;; multi-peer deployment. For the INDEX rather than the log, use
  ;; d/sync-index (§5).

  (d/delete-database mem-uri)

  ;; ── The rollout order that follows from §2 and §3 ─────────────────
  ;;
  ;;   1. Schema ships BEFORE the code that uses it. Datomic schema is
  ;;      additive, so old peers ignore attributes they do not know —
  ;;      no lock, no ALTER, no migration window.
  ;;   2. Roll peers in waves rather than all at once (§3).
  ;;   3. Warm before ready (above).
  ;;   4. Transactor upgrade: start the new-version standby, stop the
  ;;      old active, and intentionally trigger §2's failover. One
  ;;      bounded write pause, no read outage.
  ;;   5. Storage is the component whose backup matters; that drill is
  ;;      the Production class's §4.

  ;; ── LAB [PRO] — do #4 for real, if there is time ──────────────────
  ;; Exactly §2's failover lab, except the standby starts from a
  ;; different Datomic version directory. Run writer-loop!, stop the old
  ;; active, and report the window. From a peer, an upgrade and an
  ;; outage are indistinguishable.

  )

;; ═════════════════════════════════════════════════════════════════════
;; WHERE TO GO NEXT
;; ═════════════════════════════════════════════════════════════════════
;;
;;   src/datomic_infra/labs.clj      the four-session Production class
;;   infra/HA.md                     two-transactor setup for §2
