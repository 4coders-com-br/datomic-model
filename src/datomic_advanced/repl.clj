(ns datomic-advanced.repl
  "ADVANCED DATOMIC — Tuples, Schema Edge Cases, Queries & Transactions
   ════════════════════════════════════════════════════════════════════
   REPL session for the 2-hour advanced class. Four parts:

     §1 TUPLES        composite / heterogeneous / homogeneous, and the
                      catches nobody tells you about
     §2 SCHEMA        edge cases: indexing, uniqueness lifecycle,
                      alters, renames, components, noHistory,
                      attribute predicates and entity specs
     §3 QUERIES       recursive rules, aggregates (and the :with trap),
                      pull tricks, negation, multiple sources, time,
                      raw indexes, query-stats
     §4 TRANSACTIONS  tx metadata, CAS, transaction functions (both
                      kinds), speculative writes, the tx-report queue,
                      excision

   Assumes the vocabulary of the earlier classes (datom, entity id,
   tx, index, transactor, peer). Everything else is introduced.

   ── Setup ──────────────────────────────────────────────────────────
   Same deps.edn as the modeling class — Datomic Pro peer, in-memory:

     clj -M:repl        ;; then load this file / eval the ns form

   datomic:mem gives the FULL peer feature set in-process: rules, log,
   fulltext, speculative dbs, tx functions... The few places where mem
   behaves differently from a durable system (indexing, noHistory,
   excision) are exactly the edge cases — and are called out inline.

   ── Conventions ────────────────────────────────────────────────────
   * All evaluable code lives in (comment ...) blocks; nothing runs on
     load except the connection and a handful of helper fns that the
     TRANSACTOR will need to resolve (predicates, tx functions...).
   * Eval top to bottom. State accumulates linearly through the file;
     if you get lost, (fresh!) and re-run the seed blocks (marked SEED).
   * ;; => shows the expected result. Entity ids (17592186045xxx),
     t values and #inst values WILL differ on your machine.
   * Errors are part of the lesson. Forms expected to fail are wrapped
     in (anomaly ...), which returns the error message instead of
     throwing — so the class flows without stacktraces.
   * Verified against com.datomic/peer 1.0.7705. Some edges here are
     version-specific and are flagged as such."
  (:require [datomic.api :as d]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]))

;; ═════════════════════════════════════════════════════════════════════
;; §0 · SETUP
;; ═════════════════════════════════════════════════════════════════════

(def uri "datomic:mem://ada-air")

(defonce conn
  (do (d/create-database uri)
      (d/connect uri)))

(defn fresh!
  "Nuke and recreate the db, e.g. to re-run the class from the top."
  []
  (d/delete-database uri)
  (d/create-database uri)
  (alter-var-root #'conn (constantly (d/connect uri)))
  :ok)

(defn db
  "Current database VALUE — grab a fresh one after every transact."
  []
  (d/db conn))

(defmacro anomaly
  "Eval body EXPECTING it to throw; return the root cause's message.
   Lets the failure demos read as data instead of stacktraces."
  [& body]
  `(try ~@body
        :unexpected-success!
        (catch Throwable t#
          (let [root# (loop [e# t#]
                        (if-let [c# (.getCause e#)] (recur c#) e#))]
            (.getMessage root#)))))

;; ── Functions the TRANSACTOR will call ───────────────────────────────
;; Attribute predicates, entity predicates, classpath tx functions and
;; custom aggregates are all referenced BY FULLY QUALIFIED SYMBOL and
;; resolved at transaction/query time. With datomic:mem the transactor
;; lives in OUR process, so defs in this loaded namespace just work.
;; On a real system, this namespace must be on the TRANSACTOR's
;; classpath (deployed jar) for the tx-time ones, and on each PEER's
;; classpath for the query-time ones. That deployment story is the
;; main reason many teams prefer :db/fn functions — §4.3.

(defn valid-email?
  "Attribute predicate (§2.9): value -> boolean."
  [s]
  (boolean (re-matches #"[^@\s]+@[^@\s]+\.[^@\s]+" s)))

(defn well-formed-seat?
  "Entity predicate (§2.10): takes the candidate db-after AND the eid.
   You can navigate anywhere from here — cross-attribute, cross-entity."
  [db eid]
  (let [seat (d/entity db eid)]
    (boolean (re-matches #"\d{1,2}[A-F]" (or (:seat/number seat) "")))))

(defn median
  "Custom aggregate (§3.3): receives the collection of values."
  [xs]
  (let [s (vec (sort xs)) n (count s)]
    (if (odd? n)
      (nth s (quot n 2))
      (/ (+ (nth s (dec (quot n 2))) (nth s (quot n 2))) 2.0))))

(defn hold-seat
  "Classpath transaction function (§4.4): first arg is the in-transaction
   db value, rest are the call's args. Returns tx data (which may itself
   contain more tx functions — expansion is recursive)."
  [db seat-id]
  (let [status (:seat/status (d/entity db seat-id))]
    (when (not= :available status)
      (throw (ex-info "seat not available" {:seat seat-id :status status})))
    [[:db/cas seat-id :seat/status :available :held]]))


;; ═════════════════════════════════════════════════════════════════════
;; §1 · TUPLES
;; ═════════════════════════════════════════════════════════════════════
;; One attribute, up to 8 scalar values. Three flavors:
;;   composite     :db/tupleAttrs  — DERIVED from other attrs, managed
;;                                   entirely by the transactor
;;   heterogeneous :db/tupleTypes  — fixed slots, mixed types, you write
;;   homogeneous   :db/tupleType   — 2..8 values of one type, you write
;; The killer app: multi-attribute uniqueness and composite-key range
;; scans — things that pre-tuples needed fabricated string keys.

(comment

  ;; ── 1.1 SEED · base schema ─────────────────────────────────────────
  @(d/transact conn
     [;; airports
      {:db/ident :airport/code     :db/valueType :db.type/string  :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
      {:db/ident :airport/name     :db/valueType :db.type/string  :db/cardinality :db.cardinality/one}  ; NOT indexed — §2.1 uses this
      {:db/ident :airport/city     :db/valueType :db.type/string  :db/cardinality :db.cardinality/one :db/fulltext true} ; install-time only — §2.5
      {:db/ident :airport/location :db/valueType :db.type/tuple   :db/tupleTypes [:db.type/double :db.type/double] ; [lat lon]
       :db/cardinality :db.cardinality/one}
      ;; flights
      {:db/ident :flight/number    :db/valueType :db.type/string  :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
      {:db/ident :flight/origin    :db/valueType :db.type/ref     :db/cardinality :db.cardinality/one}
      {:db/ident :flight/dest      :db/valueType :db.type/ref     :db/cardinality :db.cardinality/one}
      {:db/ident :flight/price     :db/valueType :db.type/long    :db/cardinality :db.cardinality/one :db/index true} ; §3.8 range-scans this
      {:db/ident :flight/gate      :db/valueType :db.type/string  :db/cardinality :db.cardinality/one}
      {:db/ident :flight/days      :db/valueType :db.type/tuple   :db/tupleType :db.type/keyword ; homogeneous — 1.4
       :db/cardinality :db.cardinality/one}
      ;; seats
      {:db/ident :seat/flight      :db/valueType :db.type/ref     :db/cardinality :db.cardinality/one}
      {:db/ident :seat/number      :db/valueType :db.type/string  :db/cardinality :db.cardinality/one}
      {:db/ident :seat/status      :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one :db/noHistory true} ; churny — §2.8
      ;; THE COMPOSITE: physical seat identity = (flight, number).
      ;; Note it has :db/valueType tuple but NO value you ever write.
      {:db/ident :seat/flight+number
       :db/valueType :db.type/tuple
       :db/tupleAttrs [:seat/flight :seat/number]
       :db/cardinality :db.cardinality/one
       :db/unique :db.unique/identity}])

  ;; Airports first, flights second — ON PURPOSE. A lookup ref like
  ;; [:airport/code "REC"] resolves against the db BEFORE the tx, so
  ;; it cannot see entities created in the same tx (use string tempids
  ;; for that). Two transactions, or one tx with tempids everywhere:
  @(d/transact conn
     [{:airport/code "GRU" :airport/name "Guarulhos Intl" :airport/city "São Paulo" :airport/location [-23.43 -46.47]}
      {:airport/code "REC" :airport/name "Guararapes"     :airport/city "Recife"    :airport/location [-8.13 -34.92]}])
  @(d/transact conn
     [{:db/id "f" :flight/number "AD100" :flight/origin [:airport/code "REC"] :flight/dest [:airport/code "GRU"]
       :flight/price 620 :flight/gate "B12" :flight/days [:mon :wed :fri]}
      {:seat/flight "f" :seat/number "12A" :seat/status :available}
      {:seat/flight "f" :seat/number "12B" :seat/status :available}
      {:seat/flight "f" :seat/number "12C" :seat/status :available}])


  ;; ── 1.2 Composite tuples ───────────────────────────────────────────
  ;; Pull a SEAT — the composite lives on seats (its members are seat
  ;; attributes):
  (d/pull (db) '[*] (d/q '[:find ?s . :where [?s :seat/number "12A"]] (db)))
  ;; => {:db/id ...,
  ;;     :seat/flight #:db{:id 17592186045xxx},
  ;;     :seat/number "12A",
  ;;     :seat/status :available,
  ;;     :seat/flight+number [17592186045xxx "12A"]}   <- we never wrote this!
  ;;
  ;; The transactor computed :seat/flight+number from the members and
  ;; will keep it in sync whenever a member attribute changes.

  ;; The flight's eid, used inside tuple values below. (The FLIGHT
  ;; entity has no tuple of its own — yet; CATCH 4 gives it one.)
  (def AD100 (d/entid (db) [:flight/number "AD100"]))

  ;; Unique composite = LOOKUP BY PAIR:
  (d/pull (db) [:seat/number :seat/flight+number]
          [:seat/flight+number [AD100 "12A"]])
  ;; => {:seat/number "12A", :seat/flight+number [17592186045xxx "12A"]}


  ;; CATCH 1 — no nesting: inside a tuple lookup you must use the raw
  ;; entity id. A nested lookup ref does not resolve — and it does not
  ;; error either, it just quietly finds nothing:
  (d/entid (db) [:seat/flight+number [[:flight/number "AD100"] "12A"]])
  ;; => nil                       (silent! not an exception)


  ;; CATCH 2 — you cannot write a composite... and Datomic will not
  ;; even do you the favor of throwing. The assertion is silently
  ;; DISCARDED and the value recomputed from the members:
  (let [sid (d/entid (db) [:seat/flight+number [AD100 "12A"]])
        r   @(d/transact conn [[:db/add sid :seat/flight+number [999 "HACKED"]]])]
    {:datoms-in-tx (count (:tx-data r))          ; just the txInstant datom
     :value-now    (:seat/flight+number (d/pull (db) '[*] sid))})
  ;; => {:datoms-in-tx 1, :value-now [17592186045xxx "12A"]}


  ;; Members move, the tuple follows — atomically, same transaction:
  (let [sid (d/entid (db) [:seat/flight+number [AD100 "12C"]])]
    @(d/transact conn [[:db/add sid :seat/number "14C"]])
    (d/pull (db) [:seat/number :seat/flight+number] sid))
  ;; => {:seat/number "14C", :seat/flight+number [17592186045xxx "14C"]}


  ;; CATCH 3 — partial tuples: retract ONE member and the slot goes
  ;; nil; the tuple itself remains, and remains UNIQUE:
  (let [sid (d/entid (db) [:seat/flight+number [AD100 "14C"]])]
    @(d/transact conn [[:db/retract sid :seat/number "14C"]])
    (d/pull (db) [:seat/flight+number] sid))
  ;; => {:seat/flight+number [17592186045xxx nil]}

  ;; ...so a SECOND seat with only :seat/flight computes the same
  ;; [flight nil] tuple and collides. Composites are computed AFTER
  ;; upsert resolution — identity does not merge them, it conflicts:
  (anomaly @(d/transact conn [{:seat/flight AD100}]))
  ;; => ":db.error/unique-conflict Unique conflict: :seat/flight+number,
  ;;     value: [17592186045xxx nil] already held by: ..."

  ;; repair the seat back to 12C before moving on — and notice the
  ;; lookup: the partial [fid nil] tuple is itself a valid lookup value
  (let [sid (d/entid (db) [:seat/flight+number [AD100 nil]])]
    @(d/transact conn [[:db/add sid :seat/number "12C"]]))


  ;; CATCH 4 — retroactive composites. Add a composite over attrs that
  ;; ALREADY have data, and existing entities do NOT get the tuple:
  ;; it is only computed when a member attr is transacted.
  @(d/transact conn
     [{:db/ident :flight/origin+number
       :db/valueType :db.type/tuple
       :db/tupleAttrs [:flight/origin :flight/number]
       :db/cardinality :db.cardinality/one}])

  (d/pull (db) [:flight/number :flight/origin+number] AD100)
  ;; => {:flight/number "AD100"}          — no tuple!

  ;; The standard migration: re-assert one member — the SAME value is
  ;; fine. (Peek at :tx-data: the no-op re-assert dedupes away and the
  ;; tx ends up holding ONLY the freshly computed tuple datom.)
  @(d/transact conn [[:db/add AD100 :flight/number "AD100"]])
  (d/pull (db) [:flight/number :flight/origin+number] AD100)
  ;; => {:flight/number "AD100", :flight/origin+number [17592186045xxx "AD100"]}
  ;;
  ;; Rules of composites: 2–8 member attrs, every member must be
  ;; cardinality-one (card-many members are rejected at install with
  ;; :db.error/invalid-tuple-attrs).


  ;; ── 1.3 Heterogeneous tuples (:db/tupleTypes) ──────────────────────
  ;; Fixed slots, mixed types, written by YOU. Perfect for units that
  ;; only make sense together — a coordinate is not a lat plus a lon:
  (d/pull (db) [:airport/location] [:airport/code "GRU"])
  ;; => {:airport/location [-23.43 -46.47]}

  ;; Query them with untuple (tuple does the reverse):
  (d/q '[:find ?code ?lat
         :where [?a :airport/location ?loc]
                [(untuple ?loc) [?lat ?lon]]
                [(< ?lat -20.0)]
                [?a :airport/code ?code]]
       (db))
  ;; => #{["GRU" -23.43]}                 — south of the 20th parallel

  ;; nil slots are legal in written tuples too — [-8.13 nil] is fine.
  ;; What is NOT fine is arity: the value must match the declared slots.


  ;; ── 1.4 Homogeneous tuples (:db/tupleType) ─────────────────────────
  ;; 2..8 values of ONE type. Compare with cardinality-many:
  ;;   card-many  = a SET  (no order, no duplicates, no fixed size)
  ;;   tuple      = a VECTOR (order preserved, up to 8)
  ;; :flight/days keeps weekday ORDER — a card-many keyword attr would
  ;; shuffle [:mon :wed :fri] into set order and lose you nothing here,
  ;; but try modeling "outbound then return" legs without order...
  (d/q '[:find ?days . :in $ ?f :where [?f :flight/days ?days]]
       (db) [:flight/number "AD100"])
  ;; => [:mon :wed :fri]
  ;;
  ;; CATCH (bonus): why the :in? Lookup refs belong in tx data and in
  ;; :in ARGS — inside a :where pattern the E slot takes only an eid,
  ;; ident or variable. Written inline there, the vector is read as a
  ;; binding form: ":db.error/not-a-binding-form". (In the V slot of a
  ;; ref attr an inline lookup ref happens to resolve on today's peer,
  ;; but the docs bless neither — bind through :in and sleep well.)

  (anomaly @(d/transact conn
              [{:flight/number "AD999" :flight/days [:a :b :c :d :e :f :g :h :i]}]))
  ;; => ":db.error/invalid-tuple-value Invalid tuple value" — 9 > 8.


  ;; CATCH 5 — refs inside tuples are NOT refs. They are stored as
  ;; longs in a vector: no VAET entry, no reverse navigation, no
  ;; cascading retract, nothing stops them from dangling.
  @(d/transact conn
     [{:db/ident :probe/pair :db/valueType :db.type/tuple
       :db/tupleTypes [:db.type/ref :db.type/ref]
       :db/cardinality :db.cardinality/one}])
  (let [gru (d/entid (db) [:airport/code "GRU"])]
    @(d/transact conn [{:db/id "x" :probe/pair [gru gru]}])
    {:vaet-entries (count (seq (d/datoms (db) :vaet gru :probe/pair)))
     :reverse-nav  (d/q '[:find ?e :in $ ?gru :where [?e :probe/pair ?gru]] (db) gru)})
  ;; => {:vaet-entries 0, :reverse-nav #{}}
  ;;    Use real ref attrs for graph edges; tuples only for opaque pairs.


  ;; ── 1.5 The payoff: composite-key RANGE SCANS ──────────────────────
  ;; :seat/flight+number is unique => it lives in AVET. Tuples sort
  ;; slot-by-slot, and nil sorts first — so [flight nil] opens the range
  ;; and [flight+1 nil] closes it: "all seats of this flight, in order,
  ;; straight off the index", no query engine involved:
  (mapv :v (d/index-range (db) :seat/flight+number [AD100 nil] [(inc AD100) nil]))
  ;; => [[17592186045xxx "12A"] [17592186045xxx "12B"] [17592186045xxx "12C"]]
  ;;
  ;; This is the tuple version of a SQL compound index — and the reason
  ;; composites replaced the old "concatenate strings into a fake key"
  ;; trick from the days before tuples existed.
  )


;; ═════════════════════════════════════════════════════════════════════
;; §2 · SCHEMA EDGE CASES
;; ═════════════════════════════════════════════════════════════════════

(comment

  ;; ── 2.1 Not everything is indexed (on Pro) ─────────────────────────
  ;; Datomic Cloud indexes every attribute. Datomic PRO does not:
  ;; without :db/index or :db/unique there is no AVET for the attr.
  (anomaly (seq (d/datoms (db) :avet :airport/name)))
  ;; => ":db.error/attribute-not-indexed attribute: :airport/name is not indexed"

  ;; :db/index IS alterable after the fact:
  @(d/transact conn [{:db/id :airport/name :db/index true}])
  (mapv :v (d/datoms (db) :avet :airport/name))
  ;; => ["Guararapes" "Guarulhos Intl"]        — sorted, straight off AVET
  ;;
  ;; On a durable system the new index materializes at the next indexing
  ;; job; on mem it is immediate. EAVT/AEVT/VAET always exist regardless.


  ;; ── 2.2 The uniqueness LIFECYCLE ───────────────────────────────────
  ;; Uniqueness is enforced through AVET. Install passenger attrs the
  ;; lazy way — no index, no unique, "we will fix it later" — and
  ;; write a passenger:
  @(d/transact conn
     [{:db/ident :passenger/name  :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
      {:db/ident :passenger/email :db/valueType :db.type/string :db/cardinality :db.cardinality/one
       :db.attr/preds 'datomic-advanced.repl/valid-email?}      ; ambush for §2.9
      {:db/ident :passenger/document :db/valueType :db.type/string :db/cardinality :db.cardinality/one}])
  @(d/transact conn [{:passenger/name "Ana" :passenger/email "ana@ada.dev"}])

  ;; "Later" arrives. On Pro, an attr that already HAS DATA refuses
  ;; :db/unique while unindexed:
  (anomaly @(d/transact conn [{:db/id :passenger/email :db/unique :db.unique/identity}]))
  ;; => ":db.error/invalid-alter-attribute Error: {:db/error :db.error/unique-without-index, ...}"
  ;;
  ;; (Edge inside the edge: on an attr with NO datoms yet, the same
  ;;  one-step alter sails through — nothing to index, nothing to
  ;;  validate. The refusal is about existing data, not ceremony.)

  ;; The two-step: index first (on a durable system, WAIT for the
  ;; index job — d/sync-index — between the steps), then unique:
  @(d/transact conn [{:db/id :passenger/email :db/index true}])
  @(d/transact conn [{:db/id :passenger/email :db/unique :db.unique/identity}])

  ;; And Datomic CHECKS YOUR DATA on the way in. With duplicates
  ;; already present, the alteration is refused — and names the datoms:
  @(d/transact conn
     [{:db/ident :probe/tag :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/index true}])
  @(d/transact conn [{:probe/tag "dup"} {:probe/tag "dup"}])
  (anomaly @(d/transact conn [{:db/id :probe/tag :db/unique :db.unique/value}]))
  ;; => ":db.error/invalid-alter-attribute Error: {:db/error :db.error/unique-violation,
  ;;     :datoms [#datom[... \"dup\" ...] #datom[... \"dup\" ...]]}"

  ;; Related gotcha: lookup refs demand uniqueness —
  (anomaly (d/entid (db) [:probe/tag "dup"]))
  ;; => ":db.error/lookup-ref-attr-not-unique Attribute values not unique: :probe/tag"


  ;; ── 2.3 identity vs value ──────────────────────────────────────────
  ;; :db.unique/identity = "same value MEANS same entity" -> UPSERT.
  ;; Ana exists from §2.2; assert "someone" with her email:
  @(d/transact conn [{:passenger/email "ana@ada.dev" :passenger/name "Ana Souza"}])
  (d/q '[:find [?e ?n] :where [?e :passenger/email "ana@ada.dev"] [?e :passenger/name ?n]] (db))
  ;; => [17592186045xxx "Ana Souza"]     — ONE entity, renamed in place.

  ;; :db.unique/value = "duplicates are a BUG" -> conflict, no merging.
  ;; (:passenger/document has NO datoms yet, so §2.2's edge applies:
  ;;  the one-step alter is allowed — no index dance needed.)
  @(d/transact conn [{:db/id :passenger/document :db/unique :db.unique/value}])
  @(d/transact conn [{:passenger/name "Bia" :passenger/document "XY-123"}])
  (anomaly @(d/transact conn [{:passenger/name "Impostor" :passenger/document "XY-123"}]))
  ;; => ":db.error/unique-conflict Unique conflict: :passenger/document, ..."
  ;;
  ;; Choosing: external ids you RE-ASSERT on import -> identity.
  ;; Ids that must never collide silently (documents, serials) -> value.


  ;; ── 2.4 unique + cardinality-many: the alias table ─────────────────
  ;; The docs bless uniqueness on cardinality-ONE attrs. Pro 1.0.7705
  ;; happily installs it on card-MANY — giving each entity a SET of
  ;; upsertable identifiers:
  @(d/transact conn
     [{:db/ident :passenger/aliases :db/valueType :db.type/string
       :db/cardinality :db.cardinality/many :db/unique :db.unique/identity}])
  @(d/transact conn [{:passenger/name "Maria" :passenger/aliases #{"mari" "mmz"}}])
  ;; upsert through EITHER alias:
  (let [r @(d/transact conn [{:db/id "who" :passenger/aliases "mmz" :passenger/document "BR-777"}])]
    (d/pull (db) '[:passenger/name :passenger/aliases :passenger/document]
            (get (:tempids r) "who")))
  ;; => {:passenger/name "Maria", :passenger/aliases ["mari" "mmz"],
  ;;     :passenger/document "BR-777"}    — landed on Maria via her alias.
  ;;
  ;; EDGE, not a feature to lean on: it works here, but the docs only
  ;; promise card-one — and a map carrying aliases of TWO different
  ;; entities upserts through one of them (not yours to pick) and then
  ;; unique-conflicts on the other. Know that it exists; prefer a
  ;; proper alias entity when the model grows.


  ;; ── 2.5 What you can and cannot ALTER ──────────────────────────────
  ;; cardinality one->many: always fine.
  @(d/transact conn
     [{:db/ident :passenger/phone :db/valueType :db.type/string :db/cardinality :db.cardinality/one}])
  @(d/transact conn [{:passenger/email "ana@ada.dev" :passenger/phone "+55 11 91111-1111"}])
  @(d/transact conn [{:db/id :passenger/phone :db/cardinality :db.cardinality/many}])

  ;; many->one: only if the data already conforms — Datomic scans and
  ;; refuses otherwise, listing the offending datoms:
  @(d/transact conn [{:passenger/email "ana@ada.dev" :passenger/phone "+55 11 92222-2222"}])
  (anomaly @(d/transact conn [{:db/id :passenger/phone :db/cardinality :db.cardinality/one}]))
  ;; => ":db.error/invalid-alter-attribute Error: {:db/error :db.error/cardinality-violation,
  ;;     :datoms [#datom[...\"+55 11 91111-1111\"...] #datom[...\"+55 11 92222-2222\"...]]}"

  ;; And some things are install-time ONLY — :db/fulltext among them:
  (anomaly @(d/transact conn [{:db/id :airport/name :db/fulltext true}]))
  ;; => ":db.error/invalid-alter-attribute Error: {:db/error :db.error/unsupported-alter-schema,
  ;;     ... :attribute :db/fulltext, :from :disabled, :to true}"
  ;;
  ;; Also not alterable: :db/valueType, :db/tupleAttrs, :db/isComponent
  ;; on data already written against them. Plan those at install.


  ;; ── 2.6 Renaming idents (and the ghost that stays) ─────────────────
  ;; Idents are entities; renaming = asserting a new :db/ident on it.
  @(d/transact conn
     [{:db/ident :probe/nick :db/valueType :db.type/string :db/cardinality :db.cardinality/one}])
  @(d/transact conn [{:db/id :probe/nick :db/ident :probe/handle}])

  [(d/entid (db) :probe/nick)            ; the OLD name...
   (d/entid (db) :probe/handle)
   (d/ident (db) (d/entid (db) :probe/handle))]
  ;; => [95 95 :probe/handle]            — same entity id for both!
  ;;
  ;; The old ident resolves FOREVER (kept "in memoriam") so old code
  ;; and old EDN files keep working — even in transactions:
  @(d/transact conn [{:passenger/name "Zed" :probe/nick "zed"}])
  (d/q '[:find ?v . :where [?e :passenger/name "Zed"] [?e :probe/handle ?v]] (db))
  ;; => "zed"                   — written via the old name, read via the new.
  ;; Corollary: NEVER recycle a retired ident for a new attribute.

  ;; Bonus footgun: :db/ident is itself :db.unique/identity, so a map
  ;; tx that "creates" an entity with an EXISTING ident actually
  ;; UPSERTS into it. You cannot steal an ident by accident — but you
  ;; CAN decorate your schema entity with garbage by accident:
  (let [r @(d/transact conn [{:db/id "oops" :db/ident :probe/handle :passenger/name "not a person"}])]
    (get (:tempids r) "oops"))
  ;; => 95                       — "oops" resolved to the ATTRIBUTE entity.
  (d/pull (db) [:db/ident :passenger/name] :probe/handle)
  ;; => {:db/ident :probe/handle, :passenger/name "not a person"}
  @(d/transact conn [[:db/retract :probe/handle :passenger/name "not a person"]]) ; undo


  ;; ── 2.7 Component entities ─────────────────────────────────────────
  ;; :db/isComponent = the child's LIFECYCLE belongs to the parent.
  @(d/transact conn
     [{:db/ident :passenger/documents :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many :db/isComponent true}
      {:db/ident :document/kind   :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
      {:db/ident :document/number :db/valueType :db.type/string  :db/cardinality :db.cardinality/one}])

  @(d/transact conn
     [{:passenger/name "Caio"
       :passenger/email "caio@ada.dev"
       :passenger/documents [{:document/kind :passport :document/number "AB-11"}
                             {:document/kind :visa     :document/number "US-22"}]}])

  ;; pull '[*] walks INTO components (it stops at plain refs):
  (d/pull (db) '[*] [:passenger/email "caio@ada.dev"])
  ;; => {... :passenger/documents [{:db/id ... :document/kind :passport ...}
  ;;                               {:db/id ... :document/kind :visa ...}]}

  ;; :db/retractEntity CASCADES through components:
  (let [doc-ids (map :db/id (:passenger/documents (d/pull (db) '[*] [:passenger/email "caio@ada.dev"])))]
    @(d/transact conn [[:db/retractEntity [:passenger/email "caio@ada.dev"]]])
    (mapv #(d/pull (db) '[*] %) doc-ids))
  ;; => [#:db{:id ...} #:db{:id ...}]     — documents gone with Caio.

  ;; CATCH: a bare :db/retract of the REF does not cascade — it only
  ;; unlinks, and the component keeps existing, now orphaned:
  @(d/transact conn
     [{:db/id "d" :document/kind :visa :document/number "ORPHAN-1"}
      {:passenger/email "ana@ada.dev" :passenger/documents ["d"]}])
  (let [doc (d/q '[:find ?d . :where [?d :document/number "ORPHAN-1"]] (db))]
    @(d/transact conn [[:db/retract [:passenger/email "ana@ada.dev"] :passenger/documents doc]])
    (d/pull (db) '[*] doc))
  ;; => {:db/id ..., :document/kind :visa, :document/number "ORPHAN-1"}
  ;;    Orphan sweeps are on you. (Or always retract the entity, not the ref.)


  ;; ── 2.8 :db/noHistory is a PROMISE, not a guarantee ────────────────
  ;; :seat/status was installed with :db/noHistory true. Churn one:
  (def s12B (d/entid (db) [:seat/flight+number [AD100 "12B"]]))
  @(d/transact conn [[:db/add s12B :seat/status :held]])
  @(d/transact conn [[:db/add s12B :seat/status :available]])

  (d/q '[:find ?v ?added :in $ ?e :where [?e :seat/status ?v _ ?added]]
       (d/history (db)) s12B)
  ;; => #{[:available true] [:available false] [:held true] [:held false]}
  ;;    ... the full history. STILL THERE.
  ;;
  ;; noHistory trimming happens at INDEXING time, in storage. mem dbs
  ;; never index, so nothing is ever trimmed; on a durable system the
  ;; history exists until the next index job, and (d/history db) may
  ;; return it meanwhile. Treat noHistory as a storage-size hint —
  ;; never as a privacy tool. For "must be gone", see excision (§4.7).


  ;; ── 2.9 Attribute predicates ───────────────────────────────────────
  ;; :db.attr/preds — a fn of VALUE -> boolean, run by the transactor
  ;; on every assertion. We ambushed :passenger/email with one in §2.2:
  (anomaly @(d/transact conn [{:passenger/name "Eve" :passenger/email "not-an-email"}]))
  ;; => ":db.error/attr-pred Entity ... attribute :passenger/email value
  ;;     not-an-email failed pred datomic-advanced.repl/valid-email?"
  ;;
  ;; Runs in the TRANSACTOR: on a real system the fn's namespace must be
  ;; deployed to the transactor's classpath (see §0 note). Keep them
  ;; pure, total, and FAST — they run inside the write serialization.


  ;; ── 2.10 Entity specs: :db.entity/attrs + :db.entity/preds ────────
  ;; Cross-attribute validation, OPT-IN PER TRANSACTION via :db/ensure.
  @(d/transact conn
     [{:db/ident :spec/seat
       :db.entity/attrs [:seat/flight :seat/number :seat/status]     ; required
       :db.entity/preds 'datomic-advanced.repl/well-formed-seat?}])  ; fn [db eid] -> boolean

  ;; happy path — and note :db/ensure leaves NO datom behind:
  @(d/transact conn [{:seat/flight AD100 :seat/number "13A" :seat/status :available
                      :db/ensure :spec/seat}])
  (d/pull (db) '[*] [:seat/flight+number [AD100 "13A"]])
  ;; => {...}                            — no :db/ensure in there.

  ;; missing required attr:
  (anomaly @(d/transact conn [{:seat/flight AD100 :seat/number "13B"
                               :db/ensure :spec/seat}]))
  ;; => ":db.error/entity-attr Entity ... missing attributes
  ;;     clojure.lang.LazySeq@2c90f1a0 of spec :spec/seat"
  ;;     (yes, an unrealized LazySeq leaked into the message — even
  ;;      Datomic has edge cases)

  ;; failing predicate (the pred receives db-after + eid, so it judges
  ;; the entity's FINAL state, cross-attribute):
  (anomaly @(d/transact conn [{:seat/flight AD100 :seat/number "99Z" :seat/status :available
                               :db/ensure :spec/seat}]))
  ;; => ":db.error/entity-pred Entity ... failed pred
  ;;     #'datomic-advanced.repl/well-formed-seat? of spec :spec/seat"
  ;;
  ;; vs SQL constraints: not global, not always-on — each writer decides
  ;; which spec a tx must satisfy. Composable (ensure several), data-
  ;; driven, and queryable like all schema. Global invariants that must
  ;; hold for EVERY writer belong in a tx function instead (§4.3).
  )


;; ═════════════════════════════════════════════════════════════════════
;; §3 · ADVANCED QUERIES
;; ═════════════════════════════════════════════════════════════════════

(comment

  ;; ── 3.1 SEED · the route network ───────────────────────────────────
  @(d/transact conn
     [{:airport/code "GIG" :airport/name "Galeão"       :airport/city "Rio de Janeiro" :airport/location [-22.81 -43.25]}
      {:airport/code "BSB" :airport/name "JK Intl"      :airport/city "Brasília"       :airport/location [-15.86 -47.91]}
      {:airport/code "POA" :airport/name "Salgado Filho":airport/city "Porto Alegre"   :airport/location [-29.99 -51.17]}
      {:airport/code "MIA" :airport/name "Miami Intl"   :airport/city "Miami"          :airport/location [25.79 -80.29]}
      {:airport/code "LIS" :airport/name "Humberto Delgado" :airport/city "Lisboa"     :airport/location [38.77 -9.13]}])
  @(d/transact conn
     (for [[num from to price gate] [["AD101" "GRU" "REC" 620 "A03"]
                                     ["AD200" "GRU" "GIG" 199 nil]
                                     ["AD201" "GIG" "GRU" 199 "C11"]
                                     ["AD300" "GRU" "POA" 350 nil]
                                     ["AD301" "POA" "GRU" 340 nil]
                                     ["AD400" "BSB" "GRU" 280 nil]
                                     ["AD401" "GRU" "BSB" 275 "A09"]
                                     ["AD500" "GRU" "MIA" 2900 nil]
                                     ["AD501" "MIA" "GRU" 2850 nil]
                                     ["AD600" "GRU" "LIS" 3400 nil]
                                     ["AD700" "GIG" "BSB" 260 nil]]]
       (cond-> {:flight/number num
                :flight/origin [:airport/code from]
                :flight/dest   [:airport/code to]
                :flight/price  price}
         gate (assoc :flight/gate gate))))
  ;; 12 flights total (AD100 REC->GRU came from §1).


  ;; ── 3.2 Rules and RECURSION ────────────────────────────────────────
  ;; Rules = named, composable :where fragments. Two bodies = OR.
  ;; [?from] in the head = REQUIRED BINDING: callers must bind it —
  ;; this is how you stop a recursive rule from exploring the whole
  ;; cartesian universe.
  (def rules
    '[[(direct ?from ?to)
       [?f :flight/origin ?from]
       [?f :flight/dest ?to]]
      [(reachable [?from] ?to)
       (direct ?from ?to)]
      [(reachable [?from] ?to)
       (direct ?from ?mid)
       (reachable ?mid ?to)]])

  (sort
    (d/q '[:find [?code ...]
           :in $ % ?from-code
           :where [?from :airport/code ?from-code]
                  (reachable ?from ?to)
                  [?to :airport/code ?code]]
         (db) rules "REC"))
  ;; => ("BSB" "GIG" "GRU" "LIS" "MIA" "POA" "REC")
  ;;    REC included: REC->GRU->REC is a cycle — and recursion still
  ;;    TERMINATES, because Datalog computes a fixpoint over SETS
  ;;    (each fact derived once), not a tree walk that can loop.

  ;; Break the contract -> refused up front, not an infinite loop:
  (anomaly
    (d/q '[:find ?a ?b :in $ % :where (reachable ?a ?b)] (db) rules))
  ;; => ":db.error/insufficient-binding [?a] not bound in clause: (reachable ?a ?b)"


  ;; ── 3.3 Aggregates, and the :with trap ─────────────────────────────
  (d/q '[:find (min ?p) (max ?p) (count ?f) :where [?f :flight/price ?p]] (db))
  ;; => [[199 3400 12]]

  ;; Non-aggregated find vars group (SQL GROUP BY, implicit):
  (sort-by second
    (d/q '[:find ?code (min ?p)
           :where [?f :flight/origin ?o] [?o :airport/code ?code]
                  [?f :flight/price ?p]]
         (db)))
  ;; => (["GIG" 199] ["GRU" 199] ["BSB" 280] ["POA" 340] ["REC" 620] ["MIA" 2850])

  ;; THE TRAP: :find is SET semantics. Two flights cost 620 and two
  ;; cost 199 — collapse first, aggregate second = wrong answer:
  (d/q '[:find (sum ?p) . :where [?f :flight/price ?p]] (db))
  ;; => 11474                            — WRONG (each price counted once)

  ;; :with keeps ?f in the basis (without projecting it), preserving
  ;; duplicates of ?p:
  (d/q '[:find (sum ?p) . :with ?f :where [?f :flight/price ?p]] (db))
  ;; => 12293                            — right.

  ;; Same disease, count edition:
  (d/q '[:find [(count ?p) (count-distinct ?p)] :with ?f :where [?f :flight/price ?p]] (db))
  ;; => [12 10]

  ;; Collection aggregates take a limit arg:
  (d/q '[:find [(min 3 ?p) (max 2 ?p)] :with ?f :where [?f :flight/price ?p]] (db))
  ;; => [[199 199 260] [3400 2900]]

  ;; CUSTOM aggregates: any classpath fn of one collection arg —
  ;; peer-side, so no transactor deployment worries:
  (d/q '[:find (datomic-advanced.repl/median ?p) . :with ?f :where [?f :flight/price ?p]] (db))
  ;; => 345.0


  ;; ── 3.4 Pull: the projection language ──────────────────────────────
  ;; Pull inside :find — datalog picks WHICH entities, pull picks WHAT
  ;; of them. No more tuple-reassembly in application code:
  (d/q '[:find [(pull ?f [:flight/number
                          {:flight/origin [:airport/code]}
                          {:flight/dest   [:airport/code]}]) ...]
         :where [?f :flight/price ?p] [(< ?p 280)]]
       (db))
  ;; => [{:flight/number "AD200" :flight/origin {:airport/code "GRU"} ...}
  ;;     {:flight/number "AD201" ...} {:flight/number "AD401" ...}
  ;;     {:flight/number "AD700" ...}]

  ;; Pull tricks in one pattern: :as (rename), :default, reverse nav
  ;; with :limit — a departures board in ONE expression, no query:
  (d/pull (db)
          '[[:airport/code :as :hub]
            {[:flight/_origin :limit 2] [[:flight/number :as :nr]
                                         [:flight/gate :default "TBD"]
                                         {:flight/dest [:airport/code]}]}]
          [:airport/code "GRU"])
  ;; => {:hub "GRU", :flight/_origin [{:nr "AD101" :flight/gate "A03" ...}
  ;;                                  {:nr "AD200" :flight/gate "TBD" ...}]}
  ;;
  ;; (:xform exists in the grammar but Pro gates it behind an
  ;;  extensions.edn opt-in — without it you get a not-found anomaly.)


  ;; ── 3.5 Negation, disjunction, expression clauses ──────────────────
  ;; not — airports nobody departs from:
  (d/q '[:find [?code ...]
         :where [?a :airport/code ?code]
                (not [_ :flight/origin ?a])]
       (db))
  ;; => ["LIS"]

  ;; not-join — when the negated pattern needs PRIVATE variables.
  ;; "airports with no cheap departure": ?f ?p must not leak out.
  (sort
    (d/q '[:find [?code ...]
           :where [?a :airport/code ?code]
                  (not-join [?a]
                    [?f :flight/origin ?a]
                    [?f :flight/price ?p]
                    [(< ?p 300)])]
         (db)))
  ;; => ("LIS" "MIA" "POA" "REC")

  ;; or-join — unify on ?a from either side: flights TOUCHING GIG:
  (sort
    (d/q '[:find [?num ...]
           :in $ ?code
           :where [?a :airport/code ?code]
                  (or-join [?f ?a]
                    [?f :flight/origin ?a]
                    [?f :flight/dest ?a])
                  [?f :flight/number ?num]]
         (db) "GIG"))
  ;; => ("AD200" "AD201" "AD700")

  ;; get-else / missing? — optionality inside datalog:
  (d/q '[:find ?num ?gate
         :where [?f :flight/origin [:airport/code "GRU"]]
                [?f :flight/number ?num]
                [(get-else $ ?f :flight/gate "TBD") ?gate]]
       (db))
  ;; => #{["AD101" "A03"] ["AD200" "TBD"] ["AD300" "TBD"]
  ;;      ["AD401" "A09"] ["AD500" "TBD"] ["AD600" "TBD"]}

  ;; fulltext — a Lucene index as a query SOURCE (install-time flag,
  ;; remember §2.5). Returns [entity value tx score] tuples:
  (d/q '[:find ?code ?city
         :where [(fulltext $ :airport/city "rio") [[?a ?city]]]
                [?a :airport/code ?code]]
       (db))
  ;; => #{["GIG" "Rio de Janeiro"]}

  ;; Relation bindings — JOIN AGAINST DATA YOU NEVER STORED. Airport
  ;; taxes live in a config edn? Bind them as a relation:
  (d/q '[:find ?num ?total
         :in $ [[?code ?tax]]
         :where [?o :airport/code ?code]
                [?f :flight/origin ?o]
                [?f :flight/number ?num]
                [?f :flight/price ?p]
                [(+ ?p ?tax) ?total]]
       (db) [["REC" 43] ["MIA" 89]])
  ;; => #{["AD100" 663] ["AD501" 2939]}


  ;; ── 3.6 Multiple databases in ONE query ────────────────────────────
  ;; Any number of sources; each :where clause names whose datoms it
  ;; wants. Compare the db against its own past — price-rise detector:
  (def t-before-raise (d/basis-t (db)))
  @(d/transact conn [{:flight/number "AD200" :flight/price 249}])

  (d/q '[:find ?num ?was ?now
         :in $new $old
         :where [$new ?f :flight/price ?now]
                [$old ?f :flight/price ?was]
                [(< ?was ?now)]
                [$new ?f :flight/number ?num]]
       (db) (d/as-of (db) t-before-raise))
  ;; => #{["AD200" 199 249]}
  ;;
  ;; Same technique joins two DIFFERENT databases (staging vs prod),
  ;; or a db against d/since, or three of them. Sources are just args.


  ;; ── 3.7 Time as data: history, since, and the LOG ──────────────────
  ;; Every datom already carries its tx — even in the present db:
  (d/q '[:find [?when ?price]
         :where [?f :flight/number "AD200"]
                [?f :flight/price ?price ?tx]
                [?tx :db/txInstant ?when]]
       (db))
  ;; => [#inst "..." 249]        — "how much, and since when", no history db.

  ;; The full biography needs (d/history db) — every assert AND retract:
  (->> (d/q '[:find ?price ?t ?added
              :where [?f :flight/number "AD200"]
                     [?f :flight/price ?price ?tx ?added]
                     [(datomic.api/tx->t ?tx) ?t]]
            (d/history (db)))
       (sort-by second))
  ;; => ([199 tN true] [199 tM false] [249 tM true])
  ;;    assert 199 · then, in one tx: retract 199 + assert 249.

  ;; The LOG is the other time index — datoms BY TRANSACTION, queryable
  ;; via the tx-ids/tx-data source fns:
  (d/q '[:find (count ?tx) .
         :in $ ?log
         :where [(tx-ids ?log nil nil) [?tx ...]]]
       (db) (d/log conn))
  ;; => 47                      — every tx since creation (yours will vary).


  ;; ── 3.8 Below the query engine: raw index access ───────────────────
  ;; d/datoms = iterate an index directly. AVET on an indexed attr is
  ;; a sorted scan — top-3 cheapest flights with ZERO query overhead:
  (->> (d/datoms (db) :avet :flight/price)
       (take 3)
       (mapv (fn [dtm] [(:v dtm) (:flight/number (d/entity (db) (:e dtm)))])))
  ;; => [[199 "AD201"] [249 "AD200"] [260 "AD700"]]

  ;; VAET is the reverse-ref index — "who points at GRU?":
  (count (seq (d/datoms (db) :vaet (d/entid (db) [:airport/code "GRU"]))))
  ;; => 11                       — every :flight/origin|dest ref to GRU
  ;;    (...and remember CATCH 5: tuple-embedded refs are NOT among them.)

  ;; d/index-range: half-open value slice of AVET (see also §1.5):
  (mapv :v (d/index-range (db) :flight/price 260 400))
  ;; => [260 275 280 340 350]

  ;; The entity API is the lazy, associative view of EAVT — navigation
  ;; without projection; reverse keys work here too:
  (let [gru (d/entity (db) [:airport/code "GRU"])]
    {:printed (str gru)                        ; lazy: nothing realized yet
     :departures (count (:flight/_origin gru))
     :one-hop (-> gru :flight/_origin first :flight/dest :airport/city)})
  ;; => {:printed "#:db{:id ...}", :departures 6, :one-hop "Recife"}

  ;; d/qseq — query results as a LAZY seq: pay pull/marshalling costs
  ;; as you consume, not up front. Same query grammar, map form:
  (take 2 (d/qseq {:query '[:find ?n :where [_ :flight/number ?n]]
                   :args [(db)]}))
  ;; => (["AD700"] ["AD501"])    — order unspecified, arrival is lazy.


  ;; ── 3.9 query-stats: the query EXPLAIN you always wanted ──────────
  ;; Clauses run in YOUR order. Datomic will tell you what each clause
  ;; cost — :rows-in/:rows-out per clause. Same query, two orders:
  (def SLOPPY '[:find ?num
                :where [?f :flight/price ?p]            ; scans EVERY price
                       [(< ?p 300)]
                       [?f :flight/origin ?o]
                       [?o :airport/code "GIG"]         ; filters LAST
                       [?f :flight/number ?num]])
  (def SHARP  '[:find ?num
                :where [?o :airport/code "GIG"]         ; 1 row out
                       [?f :flight/origin ?o]
                       [?f :flight/price ?p]
                       [(< ?p 300)]
                       [?f :flight/number ?num]])

  (defn clause-flow [q]
    (->> (d/query {:query q :args [(db)] :query-stats true})
         :query-stats :phases (mapcat :clauses)
         (mapv (juxt :clause :rows-in :rows-out))))

  (clause-flow SLOPPY)
  ;; => [[[?f :flight/price ?p]    0 5]     ; 5 rows hauled...
  ;;     [[?f :flight/origin ?o]   5 5]     ; ...and hauled...
  ;;     [[?o :airport/code "GIG"] 5 2]     ; ...then 60% discarded
  ;;     [[?f :flight/number ?num] 2 2]]
  (clause-flow SHARP)
  ;; => [[[?o :airport/code "GIG"] 0 1]     ; 1 row from the start
  ;;     [[?f :flight/origin ?o]   1 2]
  ;;     [[?f :flight/price ?p]    2 2]     ; (< ?p 300) rides along here
  ;;     [[?f :flight/number ?num] 2 2]]
  ;;
  ;; Same result set, different intermediate row counts — on real data
  ;; that difference is your latency. Start selective, stay narrow.
  ;; (d/query also takes :timeout ms — self-defense for ad-hoc queries.)
  )


;; ═════════════════════════════════════════════════════════════════════
;; §4 · ADVANCED TRANSACTIONS
;; ═════════════════════════════════════════════════════════════════════

(comment

  ;; ── 4.1 SEED + reified transactions ────────────────────────────────
  @(d/transact conn
     [{:db/ident :booking/id        :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
      {:db/ident :booking/passenger :db/valueType :db.type/ref  :db/cardinality :db.cardinality/one}
      {:db/ident :booking/seat      :db/valueType :db.type/ref  :db/cardinality :db.cardinality/one}
      {:db/ident :booking/price     :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
      ;; attributes ABOUT TRANSACTIONS — nothing special about them:
      {:db/ident :audit/agent  :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
      {:db/ident :audit/reason :db/valueType :db.type/string :db/cardinality :db.cardinality/one}])

  ;; A transaction IS an entity (its id rides on every datom it wrote).
  ;; The tempid "datomic.tx" addresses it from INSIDE the tx — so facts
  ;; about the write travel with the write, atomically:
  (def booking-tx
    @(d/transact conn
       [{:db/id "b1"
         :booking/id (d/squuid)                 ; squuid: time-prefixed uuid, index-friendly
         :booking/passenger [:passenger/email "ana@ada.dev"]
         :booking/seat [:seat/flight+number [AD100 "12A"]]
         :booking/price 620}
        {:db/id "datomic.tx"
         :audit/agent "victor@counter-3"
         :audit/reason "phone booking"}]))

  ;; :tempids maps every string tempid to its resolved entity id:
  (:tempids booking-tx)
  ;; => {"b1" 17592186045xxx, "datomic.tx" 13194139534xxx}

  ;; Provenance query — who wrote this datom, and why? The tx variable
  ;; was always in position 4; now we USE it:
  (d/q '[:find [?agent ?reason ?when]
         :where [?b :booking/seat ?seat ?tx]
                [?seat :seat/number "12A"]
                [?tx :audit/agent ?agent]
                [?tx :audit/reason ?reason]
                [?tx :db/txInstant ?when]]
       (db))
  ;; => ["victor@counter-3" "phone booking" #inst "..."]
  ;;
  ;; This is the audit table you never had to build. Works for schema
  ;; txes too (annotate your migrations!).


  ;; ── 4.2 Compare-and-swap: :db/cas ──────────────────────────────────
  ;; Optimistic concurrency per datom: assert "old -> new", fail if old
  ;; is stale. The seat-hold flow, sans tx function:
  (def s12C (d/entid (db) [:seat/flight+number [AD100 "12C"]]))

  @(d/transact conn [[:db/cas s12C :seat/status :available :held]])   ; agent A wins
  (anomaly
    @(d/transact conn [[:db/cas s12C :seat/status :available :held]])) ; agent B lost
  ;; => ":db.error/cas-failed Compare failed: :available :held"

  ;; nil as expected = "only if absent" — create-once semantics.
  ;; AD200 was seeded without a gate:
  @(d/transact conn [[:db/cas [:flight/number "AD200"] :flight/gate nil "C2"]])
  (anomaly @(d/transact conn [[:db/cas [:flight/number "AD200"] :flight/gate nil "C9"]]))
  ;; => ":db.error/cas-failed Compare failed:  C2"   (nil prints as blank)
  @(d/transact conn [[:db/retract [:flight/number "AD200"] :flight/gate "C2"]]) ; tidy up

  ;; And the tx-level cousin of cas-failure — two values for one
  ;; cardinality-one attr in a single tx is refused outright:
  (anomaly @(d/transact conn [[:db/add s12C :seat/status :available]
                              [:db/add s12C :seat/status :booked]]))
  ;; => ":db.error/datoms-conflict Two datoms in the same transaction conflict ..."
  @(d/transact conn [[:db/add s12C :seat/status :available]])  ; release the hold


  ;; ── 4.3 Transaction functions, kind 1: :db/fn ──────────────────────
  ;; The transactor runs ONE tx at a time. A tx function runs INSIDE
  ;; that serialization with the current db value — so check-then-write
  ;; here has NO race, ever, by construction.
  ;;
  ;; :db/fn = the function is DATA, stored in the db, versioned and
  ;; deployed WITH your data. d/function compiles the map to a fn:
  (def book-seat-fn
    (d/function
      '{:lang :clojure
        :params [db flight-number seat-number email agent]
        :code
        (let [fid  (datomic.api/entid db [:flight/number flight-number])
              sid  (datomic.api/entid db [:seat/flight+number [fid seat-number]])
              seat (when sid (datomic.api/entity db sid))]
          (cond
            (nil? sid)
            (throw (ex-info "no such seat" {:flight flight-number :seat seat-number}))

            (not= :available (:seat/status seat))
            (throw (ex-info "seat taken" {:seat seat-number :status (:seat/status seat)}))

            :else
            [[:db/cas sid :seat/status :available :booked]     ; fns can emit fns
             {:booking/id (datomic.api/squuid)
              :booking/passenger [:passenger/email email]
              :booking/seat sid
              :booking/price (:flight/price (datomic.api/entity db fid))}
             {:db/id "datomic.tx" :audit/agent agent :audit/reason "web checkout"}]))}))

  ;; It's a real fn — TEST IT LOCALLY against a db value first:
  (book-seat-fn (db) "AD100" "12B" "ana@ada.dev" "test")
  ;; => [[:db/cas ...] {:booking/id #uuid "..." ...} {...}]   — pure data out.

  ;; Install = transact it, like everything else:
  @(d/transact conn [{:db/ident :air/book-seat :db/fn book-seat-fn}])

  ;; Call it BY IDENT in tx data. The transactor invokes it with the
  ;; in-tx db, splices the returned tx data, expands recursively:
  @(d/transact conn [[:air/book-seat "AD100" "12B" "ana@ada.dev" "kiosk-7"]])

  (:seat/status (d/entity (db) [:seat/flight+number [AD100 "12B"]]))
  ;; => :booked

  ;; The invariant holds against ANY writer — there is no code path
  ;; that can double-book, because every path goes through the fn:
  (anomaly @(d/transact conn [[:air/book-seat "AD100" "12B" "zed@ada.dev" "kiosk-9"]]))
  ;; => "seat taken"             — your ex-info, aborting the WHOLE tx.

  ;; d/invoke runs an installed fn without transacting (handy in tests):
  (anomaly (d/invoke (db) :air/book-seat (db) "AD100" "12B" "x@y.z" "t"))
  ;; => "seat taken"
  ;;
  ;; Ground rules for tx fns: PURE (they may be retried), fast (they
  ;; hold up every other writer — remember the at-scale class), throw
  ;; ex-info to abort. The db they see includes all prior tx data of
  ;; the same transaction already applied? NO — they see the db as of
  ;; tx START; composition happens by expansion, not by re-reading.


  ;; ── 4.4 Transaction functions, kind 2: classpath fns ───────────────
  ;; A plain Clojure fn named by fully-qualified symbol — hold-seat is
  ;; defined at the top of this file. No install step, no d/function:
  ;; the tx form is [fully.qualified/symbol arg1 arg2 ...] and the
  ;; transactor resolves + calls it with the in-tx db prepended:
  @(d/transact conn [['datomic-advanced.repl/hold-seat s12C]])

  (:seat/status (d/entity (db) s12C))
  ;; => :held
  (anomaly @(d/transact conn [['datomic-advanced.repl/hold-seat s12C]]))
  ;; => "seat not available"
  @(d/transact conn [[:db/add s12C :seat/status :available]])   ; release

  ;; :db/fn vs classpath — the real trade:
  ;;   :db/fn      lives in the DB    -> zero deploys, versioned with data,
  ;;                                     but awkward to unit test/review/reuse
  ;;   classpath   lives in your CODE -> normal namespaces, tests, libraries,
  ;;                                     but must be ON THE TRANSACTOR's
  ;;                                     classpath — a deploy + restart.
  ;; With mem both "just work" because the transactor is in-process.


  ;; ── 4.5 Speculation: d/with ────────────────────────────────────────
  ;; Apply tx data to a db VALUE — full tx machinery (tx fns, cas,
  ;; specs, composites all run) — commit NOTHING. The what-if machine:
  (let [bump      (for [[f p] (d/q '[:find ?f ?p :where [?f :flight/price ?p]] (db))]
                    [:db/add f :flight/price (long (* 1.10 p))])
        {spec-db :db-after} (d/with (db) bump)]
    {:revenue-now  (d/q '[:find (sum ?p) . :with ?f :where [?f :flight/price ?p]] (db))
     :revenue-then (d/q '[:find (sum ?p) . :with ?f :where [?f :flight/price ?p]] spec-db)})
  ;; => {:revenue-now 12343, :revenue-then 13575}
  ;;    (now = §3.3's 12293 + AD200's raise from §3.6; then = now +10%,
  ;;     and the durable db never felt a thing.)

  ;; Speculative dbs CHAIN — with on a with — an in-memory branch of
  ;; reality, N migrations deep, that nobody else ever sees:
  (let [w1 (d/with (db)            [{:airport/code "SSA" :airport/name "Deputado LEM"}])
        w2 (d/with (:db-after w1)  [{:flight/number "AD800"
                                     :flight/origin [:airport/code "GRU"]
                                     :flight/dest   [:airport/code "SSA"]
                                     :flight/price  380}])]
    {:speculative (d/q '[:find (count ?f) . :where [?f :flight/number]] (:db-after w2))
     :durable     (d/q '[:find (count ?f) . :where [?f :flight/number]] (db))})
  ;; => {:speculative 13, :durable 12}
  ;;
  ;; Uses: dry-run migrations, "would this tx fn throw?", test suites
  ;; that never touch a transactor, branchy UIs (forked app state).


  ;; ── 4.6 The tx-report-queue: reacting to writes ────────────────────
  ;; Every peer can subscribe to the firehose of committed txes —
  ;; :tx-data of each report is the actual datoms, ready to drive
  ;; caches, search indexes, websockets... (It is the app-facing side of
  ;; the novelty feed the at-scale class described peers living on.)
  (def txq (d/tx-report-queue conn))

  @(d/transact conn [[:db/add s12C :seat/status :held]])

  (let [report (.poll txq 500 java.util.concurrent.TimeUnit/MILLISECONDS)]
    {:keys    (sort (keys report))
     :datoms  (count (:tx-data report))
     :basis-t (d/basis-t (:db-after report))})
  ;; => {:keys (:db-after :db-before :tempids :tx-data), :datoms 3, :basis-t ...}

  (d/remove-tx-report-queue conn)     ; always unhook when done
  @(d/transact conn [[:db/add s12C :seat/status :available]])

  ;; Related plumbing, one line each:
  ;;   (d/transact-async conn tx) — returns immediately; deref = ack.
  ;;   @(d/sync conn t)           — block until THIS peer has seen t
  ;;                                (read-your-writes across peers).


  ;; ── 4.7 Excision: the one true eraser ──────────────────────────────
  ;; History is immutable — except for the legal-grade escape hatch.
  ;; :db/excise removes datoms FROM HISTORY (GDPR, PII spills):
  (def zed (d/q '[:find ?e . :where [?e :passenger/name "Zed"]] (db)))
  @(d/transact conn [{:db/excise zed}])

  (:passenger/name (d/entity (db) zed))
  ;; => "Zed"                    — STILL VISIBLE?!
  ;;
  ;; By design, excision is ASYNCHRONOUS: the tx above only RECORDS the
  ;; request (auditable forever: "something was removed here, by tx T");
  ;; the actual scrubbing happens at the next INDEXING job, in storage.
  ;; And mem dbs never index — on mem, excision never lands. On a
  ;; durable system you'd (d/sync-excise conn t) to await it. Limits:
  ;; can't excise schema idents or your own excision records; fulltext
  ;; segments lag until reindex. Use for compliance, never for undo —
  ;; for undo, retract; for "never happened", excise.


  ;; ── 4.8 Where to go from here ──────────────────────────────────────
  ;; docs.datomic.com — reference/schema (tuples, preds, specs),
  ;; reference/transaction-functions, query/query (rules, aggregates,
  ;; query-stats), reference/log, reference/excision.
  ;; The at-scale deck covers WHY these knobs matter under load.
  )
