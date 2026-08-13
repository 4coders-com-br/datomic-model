(ns datomic-infra.labs
  "DATOMIC IN PRODUCTION — Infrastructure, Storage and Caches
   ════════════════════════════════════════════════════════════════════
   REPL companion for the four 2-hour sessions. Sections (§1–§4) match
   the deck's sessions; every slide tagged `◆ lab` has its code here.

   Unlike the domain-modeling class, this one needs real infrastructure:
   a PostgreSQL container and a transactor process. See infra/README.md.

     docker compose -f infra/docker-compose.yml up -d
     $DATOMIC/bin/transactor infra/pg-transactor.properties
     clj -M:infra:repl

   ── Conventions ────────────────────────────────────────────────────
   * Everything evaluable lives in (comment ...) blocks — nothing runs
     on load except the URI definitions.
   * `;; =>` shows what one rehearsal produced. Entity ids, t values and
     segment counts WILL differ on your machine; the SHAPES won't.
   * Several labs want a second terminal on psql. Keep one open:
       docker exec -it datomic-pg psql -U datomic -d datomic"
  (:require [datomic.api :as d]
            [clojure.pprint :refer [pprint]]))

;; ═════════════════════════════════════════════════════════════════════
;; SETUP — the URIs
;; ═════════════════════════════════════════════════════════════════════

(def jdbc
  "The JDBC URI for our storage. One PostgreSQL database holds MANY
   Datomic databases — this string names the storage, not the db."
  "jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic")

(defn uri
  "The Datomic connection URI for one database in that storage.
   Note the two question marks: the second one belongs to JDBC."
  [db-name]
  (str "datomic:sql://" db-name "?" jdbc))

(def system-uri
  "Storage URI with * for the database name — used to list databases."
  (uri "*"))

;; ═════════════════════════════════════════════════════════════════════
;; §1 · THE DEPLOYMENT MAP — the database lifecycle          (session 1)
;; ═════════════════════════════════════════════════════════════════════

(comment

  ;; LAB — your first database.
  (d/create-database (uri "inventory"))
  (d/create-database (uri "payments"))
  ;; => true          (false means it already existed — it's idempotent)

  (def conn (d/connect (uri "inventory")))

  (d/get-database-names system-uri)
  ;; => ("inventory")

  ;; Now look at it from the storage side. In psql:
  ;;   select count(*) from datomic_kvs;
  ;; An empty database is NOT zero rows — that's the bootstrap schema.

  ;; Make a second database and count again. Same table.
  (d/create-database (uri "scratch"))
  (d/get-database-names system-uri)
  ;; => ("inventory" "scratch")

  (d/delete-database (uri "scratch"))
  ;; => true
  ;; ...and count the rows once more. delete-database does NOT reclaim
  ;; the space — that's `bin/datomic gc-deleted-dbs`, in session 4.

  )

;; ─────────────────────────────────────────────────────────────────────
;; §1b · THE CLIENT API — the same database, through a Peer Server
;; ─────────────────────────────────────────────────────────────────────
;;
;; Start the Peer Server in a THIRD terminal. -d and -a are both
;; repeatable; -c/--concurrency defaults to 16. There is no --help —
;; run it with no arguments to see the option table.
;;
;;   $DATOMIC/bin/run -m datomic.peer-server \
;;     -h localhost -p 8998 \
;;     -a myaccesskey,mysecret \
;;     -d inventory,"datomic:sql://inventory?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"
;;
;;   curl -k https://localhost:8998/health     # 200 means alive
;;
;; Then start the REPL with the client on the classpath:
;;   clj -M:infra:client:repl

(comment

  ;; NOTE the different namespace. This is a different library.
  (require '[datomic.client.api :as c])

  (def client
    (c/client {:server-type        :peer-server
               :endpoint           "localhost:8998"
               :access-key         "myaccesskey"
               :secret             "mysecret"
               ;; the Peer Server's cert is self-signed — run both
               ;; inside a trusted network
               :validate-hostnames false}))
  ;; c/client makes NO network call; it returns immediately.

  (c/list-databases client {})
  ;; => ["inventory"]

  (def cconn (c/connect client {:db-name "inventory"}))

  ;; Session 1 leaves `inventory` empty — the schema does not arrive
  ;; until session 2. Install it HERE, through the client, so there is
  ;; something to query. Note that transact is arg-map only.
  (c/transact cconn {:tx-data schema})
  (c/transact cconn {:tx-data [{:inventory/sku "SKU-CLIENT"}]})

  ;; A db is a VALUE — take a fresh one after transacting.
  (def cdb (c/db cconn))

  ;; Same query, arg-map shape instead of positional:
  (c/q {:query '[:find ?sku :where [_ :inventory/sku ?sku]]
        :args  [cdb]})

  ;; Same query, positional shape — this works too. The Client API is
  ;; NOT arg-map-only; q and pull have both arities. It's connect,
  ;; transact and datoms that are arg-map only.
  (c/q '[:find ?sku :where [_ :inventory/sku ?sku]] cdb)

  (def eid (ffirst (c/q {:query '[:find ?e :where [?e :inventory/sku]]
                         :args  [cdb]})))
  (c/pull cdb {:selector [:inventory/sku] :eid eid})
  (c/pull cdb [:inventory/sku] eid)          ;; identical result

  ;; Peer Servers do not OWN databases — this refuses, and not politely:
  (c/create-database client {:db-name "nope"})
  ;; => AbstractMethodError: Receiver class
  ;;    datomic.client.impl.shared.Client does not define or inherit an
  ;;    implementation of ... create_database
  ;; Not a nice message. Recognising it is the point.

  ;; And the entity API simply is not here — no c/entity, no c/filter,
  ;; no c/tx-report-queue. Compare the two namespaces:
  (count (ns-publics 'datomic.api))          ;; => 62
  (count (ns-publics 'datomic.client.api))   ;; => 22

  )

;; ═════════════════════════════════════════════════════════════════════
;; §2 · THE WRITE PATH — watch a transaction become bytes    (session 2)
;; ═════════════════════════════════════════════════════════════════════

(def schema
  [{:db/ident       :inventory/sku
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :inventory/count
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one}])

(comment

  ;; LAB — watch a transaction land.
  (def conn (d/connect (uri "inventory")))

  ;; Subscribe BEFORE transacting. The queue receives a report for every
  ;; transaction while this peer is connected — including other peers'.
  (def q (d/tx-report-queue conn))

  (def report @(d/transact conn schema))

  (keys report)
  ;; => (:db-before :db-after :tx-data :tempids)

  (count (:tx-data report))     ;; the datoms actually written
  (d/basis-t (d/db conn))       ;; the t you are now reading at

  (:tx-data (.poll q))          ;; the same datoms, via the broadcast

  ;; Count rows in psql before and after this next one:
  (def bulk
    @(d/transact conn (for [i (range 5000)]
                        {:inventory/sku   (str "SKU-" i)
                         :inventory/count i})))

  (count (:tx-data bulk))
  ;; => 10001        (5000 skus + 5000 counts + 1 tx datom)

  ;; In psql:  select count(*) from datomic_kvs;
  ;; One rehearsal: ~77 rows for 5,000 entities, ~108 kB total, with
  ;; single blobs over 25 kB. Five thousand entities, seventy-seven rows.
  ;; That is a SEGMENT — thousands of datoms in one opaque value.
  ;;
  ;;   select id, rev, length(val) from datomic_kvs
  ;;    order by length(val) desc limit 3;
  ;;
  ;; The ids are UUIDs. There is no Datomic semantics visible in SQL.

  ;; LAB — force an index job.
  (pprint (d/db-stats (d/db conn)))

  (d/request-index conn)
  ;; => true         (asynchronous — watch the transactor's stdout)

  ;; Count rows again: the index job WROTE new segments. The old ones
  ;; are orphaned, not deleted. Only gc-storage reclaims them, and only
  ;; those older than the date you pass:
  #_(d/gc-storage conn (java.util.Date.))
  ;; In production `older-than` must be at least a month old.

  ;; LAB — a timeout is not a failure.
  ;; -Ddatomic.txTimeoutMsec=10000 is the peer default. When it fires,
  ;; the peer does not know whether the write landed. Find out from the
  ;; log rather than guessing:
  (->> (d/tx-range (d/log conn) (- (d/basis-t (d/db conn)) 3) nil)
       (map (juxt :t #(count (:data %))))
       pprint)

  )

;; ═════════════════════════════════════════════════════════════════════
;; §3 · THE READ PATH — measuring the cache tiers            (session 3)
;; ═════════════════════════════════════════════════════════════════════

(def all-skus '[:find ?e :where [?e :inventory/sku]])

(comment

  ;; LAB — cold versus warm.
  (def conn (d/connect (uri "inventory")))

  ;; Cold: nothing of this database is in this process's object cache.
  (time (count (d/q all-skus (d/db conn))))

  ;; Warm: same query, same process, segments now cached in the heap.
  (time (count (d/q all-skus (d/db conn))))
  ;; Expect one to two orders of magnitude. The difference is storage
  ;; round-trips and decompression, not query work.

  ;; Watch the storage side of the same experiment, in psql:
  ;;   select blks_read, blks_hit from pg_stat_database
  ;;    where datname = 'datomic';

  ;; Now restart this REPL and run the cold query again — with no
  ;; valcache configured you are cold all over again. That is what
  ;; every deploy of your application does to your storage.

  ;; LAB — make the working set not fit.
  ;; Restart the REPL with a deliberately tiny object cache:
  ;;   clj -J-Ddatomic.objectCacheMax=32m -M:infra:repl
  ;; ...then run the warm case again. It is slow now: you have made the
  ;; cache smaller than the working set. This is exactly what happens in
  ;; production when the data grows and nobody re-sizes anything.

  ;; LAB — add the shared memcached tier.
  ;;   docker compose -f infra/docker-compose.yml --profile cache up -d
  ;;   # uncomment `memcached=localhost:11211` in pg-transactor.properties
  ;;   # restart the transactor, then start this REPL with:
  ;;   clj -J-Ddatomic.memcachedServers=localhost:11211 -M:infra:repl
  ;;
  ;; In a third terminal:
  ;;   watch -n1 'echo stats | nc localhost 11211 \
  ;;              | grep -E "get_hits|get_misses|curr_items"'
  ;;
  ;; Run the cold query. curr_items climbs by hundreds, not by 5,000 —
  ;; because the unit of caching is a segment, not an entity.
  ;; Restart the peer and query again: object cache misses, memcached
  ;; hits. That is the shared tier earning its keep.

  ;; LAB — reads do not need the transactor.
  ;; Stop the transactor process, then:
  (d/basis-t (d/db conn))              ;; still works
  (count (d/q all-skus (d/db conn)))   ;; still works
  #_@(d/transact conn [])              ;; this is what fails
  ;; A dead transactor is a WRITE outage, not a read outage. Restart it.

  ;; Coordinating reads across processes: pass the t, don't sleep.
  (let [t (d/basis-t (d/db conn))]
    (d/basis-t (deref (d/sync conn t) 5000 nil)))

  )

;; ═════════════════════════════════════════════════════════════════════
;; §4 · OPERATIONS — the disaster-recovery drill             (session 4)
;; ═════════════════════════════════════════════════════════════════════

(comment

  ;; LAB — back up, delete, restore. All of the backup tooling is CLI,
  ;; from the Datomic distribution root. $URI is the inventory URI:
  ;;
  ;;   export URI='datomic:sql://inventory?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic'
  ;;
  ;; 1. back it up (runs live, no downtime needed)
  ;;   bin/datomic -Xmx2g backup-db "$URI" file:/tmp/inv-backup
  ;;   => Copied 46 segments, skipped 0 segments.
  ;;      :succeeded

  ;; 2. transact something more, then back up AGAIN — differential:
  @(d/transact (d/connect (uri "inventory"))
               (for [i (range 5000 5200)] {:inventory/sku (str "SKU-" i)}))
  ;;   bin/datomic -Xmx2g backup-db "$URI" file:/tmp/inv-backup
  ;;   => Copied 0 segments, skipped 2 segments.

  ;; 3. what is in the backup?
  ;;   bin/datomic list-backups file:/tmp/inv-backup
  ;;   => (6002 1001)          two points in time

  ;; 4. THE DISASTER. Do it for real:
  (d/delete-database (uri "inventory"))
  ;; => true
  (d/get-database-names system-uri)
  ;; => nil

  ;; 5. stop the transactor. SQL storage requires transactors DOWN for a
  ;;    restore. (dev/H2 storage is the opposite — storage lives inside
  ;;    the transactor, so it must be RUNNING. This inversion is the
  ;;    number one reason a first restore attempt fails.)

  ;; 6. restore under the SAME name:
  ;;   bin/datomic -Xmx2g restore-db file:/tmp/inv-backup "$URI"
  ;;   => Copied 0 segments, skipped 46 segments.
  ;;      :succeeded
  ;;      {:event :restore, :db inventory, :basis-t 6002, :inst #inst "..."}
  ;;
  ;; Restoring into a DIFFERENT name in the same storage fails with
  ;;   :restore/collision The database already exists under the name ...
  ;; Backup/restore is disaster recovery, not cloning.

  ;; 7. restart the transactor, then confirm:
  (let [c (d/connect (uri "inventory"))]
    [(d/basis-t (d/db c))
     (count (d/q all-skus (d/db c)))])
  ;; => [6002 5200]

  ;; LAB — point-in-time recovery. Delete again, then pass an earlier t
  ;; from list-backups:
  ;;   bin/datomic -Xmx2g restore-db file:/tmp/inv-backup "$URI" 1001

  ;; LAB — reclaim the space from deleted databases. Note the URI has NO
  ;; database name: this is a storage-level operation.
  ;;   bin/datomic gc-deleted-dbs 'datomic:sql://?jdbc:postgresql://...'
  ;;
  ;; For dev and staging the docs prefer the blunt instrument: drop and
  ;; recreate datomic_kvs. It is instant.

  )
