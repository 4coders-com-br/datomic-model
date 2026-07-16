(ns domain-modeling.time-exercises
  "FILL THE GAPS — the Tx column earns its keep           (last 30 min)
   ════════════════════════════════════════════════════════════════════
   Four exercises built on Part 4 (Time — REPL §4). Every block below
   has holes marked ___ . Replace each ___ so the form evaluates to
   the result in its `;; =>` comment. Forgot one? Outside a quoted
   query it throws `Unable to resolve symbol: ___`; INSIDE a quoted
   query the quote protects it, so you get a Datomic error naming
   ___ instead — either way the cue is loud. Solutions are at the
   very bottom (SOLUTIONS — scroll blind, no spoilers on the way).

   Setup: eval (start!) once. It rebuilds the class db through Part 4
   and stages tonight's incident.

   Theme: four problems that are projects-with-meetings in a
   mutable-row database, and one query here — because transactions
   are ENTITIES with attributes, and datoms never stop carrying
   their Tx.

   Timing: T1 ~5 min · T2 ~7 min · T3 ~8 min · T4 ~10 min."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [___]}}}}
  (:require [datomic.api :as d]
            [clojure.pprint :refer [print-table]]
            [domain-modeling.repl :as class]))

(defn db
  "Current db VALUE (always via the class connection — safe across
   fresh!/goto!)."
  []
  (d/db class/conn))

(def audit-schema
  ;; the class installed :audit/actor and :audit/reason in §4.2;
  ;; tonight we add the third leg of provenance — the ticket:
  [{:db/ident       :audit/ticket
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Issue-tracker id for the change. Lives ON the tx."}])

;; ─────────────────────────────────────────────────────────────────────
;; ONE DETAIL THE CLASS ONLY WAVED AT — the "datomic.tx" tempid
;; ─────────────────────────────────────────────────────────────────────
;; Every transaction you ran tonight created one entity you didn't ask
;; for: the TRANSACTION ITSELF. That's where :db/txInstant lives — it
;; was the first datom of every receipt you read in §4.1.
;;
;; Inside tx-data, the string "datomic.tx" is a RESERVED tempid that
;; resolves to that about-to-be-created tx entity. So the map
;;
;;   {:db/id "datomic.tx" :audit/actor "…" :audit/reason "…"}
;;
;; attaches facts TO THE VERY TRANSACTION THAT CARRIES IT. Note what
;; that buys you over an audit table: the change and its annotation
;; are ONE atomic write — they commit together or fail together, so
;; an audit row can never be missing, late, or pointing at the wrong
;; change. (Ordinary string tempids — §2.7's "clj"/"ds" — name NEW
;; domain entities; this reserved one names the tx.) Every exercise
;; below leans on this one move.

(defn start!
  "Class db through Part 4 (slide 41 state), plus tonight's incident:
   at some point an unattended bot imported emails from a stale CSV
   export. Nobody noticed. (Don't read the transact below too closely —
   discovering WHAT it did, and undoing it, IS the exercise.)"
  []
  (class/goto! 41)
  @(d/transact class/conn audit-schema)
  @(d/transact class/conn
     [{:db/id "datomic.tx"                ;; ← reserved tempid = this
       :audit/actor  "intern-bot"          ;;   very tx (note above)
       :audit/reason "backfill emails from crm-export.csv"
       :audit/ticket "JIRA-1337"}
      {:db/id [:user/name "pithyless"] :user/email "norbert@oldcorp.example"}
      {:db/id [:user/name "tonsky"]    :user/email "nikita@oldcorp.example"}])
  :ready)

(comment
























  ;; ══════════════════════════════════════════════════ SETUP ═══
  (start!)   ;; => :ready   (re-eval any time to reset the incident)

























  ;; ══════════════════════════════════════════ T1 · ~5 min ═══
  ;; WHAT DID TICKET JIRA-1337 TOUCH?
  ;;
  ;; Support says users are complaining about wrong emails, and the
  ;; only lead is a ticket number someone saw in a Slack thread.
  ;;
  ;; Why this is misery elsewhere: an audit TABLE only records what
  ;; you remembered to trigger, in the schema you designed last year.
  ;; Here the ticket lives on the tx, every datom carries its tx —
  ;; so one ticket number indexes ANY change to ANY entity, forever.

  ;; 1a. Which transaction was it? (Parameterized, §2.4-style — note
  ;;     the blank sits OUTSIDE the quoted query, where Clojure
  ;;     actually evaluates it.)
  (def bad-tx
    (d/q '[:find ?tx .
           :in $ ?ticket
           :where [?tx :audit/ticket ?ticket]]
         (db) ___))         ;; ← the ticket number from the Slack thread
  bad-tx
  ;; => 13194139534343          (a plain entity id — yours will differ)
  ;; Got nil? The query matched nothing — check the ticket string.

  ;; 1b. Everything that tx did — across all entities, ops included.
  ;;     Two holes: WHERE does the tx go in the datom pattern, and
  ;;     which db value can still see the retractions?
  (->> (d/q '[:find ?who ?aname ?v ?op
              :in $ ?tx
              :where [?e ?a ?v ___ ?op]
                     [(not= ?e ?tx)]      ;; skip the tx's own datoms —
                                          ;; the clock + audit trio
                     [?a :db/ident ?aname]
                     [?e :user/name ?who]]
            (___ (db)) bad-tx)
       (sort-by (juxt first (comp str last))))
  ;; => (["pithyless" :user/email "norbert@oldcorp.example" true ]
  ;;     ["tonsky"    :user/email "tonsky@example.com"      false]
  ;;     ["tonsky"    :user/email "nikita@oldcorp.example"  true ])
  ;;
  ;; THREE datoms, not four. Read the incident report straight off
  ;; the ops: pithyless was net-new (assert only); tonsky was an
  ;; OVERWRITE (assert + auto-retract of the old value, same tx).
  ;; Nobody wrote that report — the log is the report.

























  ;; ══════════════════════════════════════════ T2 · ~7 min ═══
  ;; BLAME THE CELL
  ;;
  ;; For every user that has an email TODAY: who put that exact
  ;; value there, and when? git blame, but for a database cell.
  ;;
  ;; Why this is misery elsewhere: UPDATE erased the author with the
  ;; value. Here the current datom still carries the tx that wrote
  ;; it — join the present against its own history. (Same two-source
  ;; $/$hist move as §4.2's provenance query — more inputs, same $.)
  ;;
  ;; One hole: which db must the SECOND source be?

  (->> (d/q '[:find ?who ?email ?actor ?inst
              :in $ $hist
              :where [$ ?e :user/name ?who]
                     [$ ?e :user/email ?email]               ;; current value
                     [$hist ?e :user/email ?email ?tx true]  ;; ← true = "the
                     ;; moment it became true"; false would find who DELETED it
                     [$ ?tx :db/txInstant ?inst]
                     [(get-else $ ?tx :audit/actor "unattributed") ?actor]]
            (db) (___ (db)))
       (sort-by first)
       (map (fn [[who email actor _]] [who email actor])))
  ;; => (["pithyless"  "norbert@oldcorp.example" "intern-bot"]
  ;;     ["richhickey" "rich@example.com"        "unattributed"]
  ;;     ["tonsky"     "nikita@oldcorp.example"  "intern-bot"]
  ;;     ["victor"     "v@4coders.com.br"        "unattributed"])
  ;;
  ;; The two "unattributed" rows are the class transacts from Parts
  ;; 2 and 4 — nobody annotated them. That is heuristic #7 biting in
  ;; real time: annotate from day one, or 2am-you gets "unattributed".
  ;;
  ;; ⚠ Bonus gotcha to discuss: if the SAME value was asserted, then
  ;; retracted, then asserted again, this query returns one row per
  ;; assertion. Want only the latest? aggregate with (max ?tx).

























  ;; ══════════════════════════════════════════ T3 · ~8 min ═══
  ;; REVERT A TRANSACTION — AND AUDIT THE REVERT
  ;;
  ;; The CTO: "Undo JIRA-1337. No backup restore, no downtime, and I
  ;; want the revert itself to show up in the audit."
  ;;
  ;; Why this is misery elsewhere: restore-snapshot-to-second-
  ;; cluster, diff, hand-write compensating UPDATEs, and the mistake
  ;; itself vanishes from the record. Here an undo is DATA: take the
  ;; tx's datoms, flip every op, transact — in a new, annotated tx.
  ;;
  ;; Where do you get "the tx's datoms"? The LOG — you met it as
  ;; shape 6. It is the tx-first index: d/tx-range hands you each
  ;; transaction as {:t … :data [datoms…]}. And t ↔ tx is just the
  ;; arithmetic from §4.2.

  (defn tx-datoms
    "Every [e a v op] the given tx wrote — minus the tx's OWN datoms
     (the clock + audit trio have e = the tx itself; we invert facts,
     not bookkeeping)."
    [tx]
    (let [t (d/tx->t tx)]
      (->> (d/tx-range (d/log class/conn) t (___ t))  ;; half-open range:
           first                                      ;; [t, ?) — one tx
           :data
           (remove (fn [dtm] (= (:e dtm) ___)))       ;; whose datoms to skip?
           (map (fn [dtm] [(:e dtm) (:a dtm) (:v dtm) (:added dtm)])))))

  (defn invert
    "An assertion is undone by a retraction; a retraction by an assertion."
    [[e a v op]]
    (if op
      [___ e a v]     ;; it was asserted  ⇒ take it back
      [___ e a v]))   ;; it was retracted ⇒ put it back

  ;; the compensating transaction — inverted datoms + its OWN audit.
  ;; (Look at what invert emits: the attribute slot holds a NUMBER,
  ;; the attr's entity id straight off the log, not a keyword. Tx
  ;; data takes either — attributes are entities (§3) and the
  ;; keyword is just their :db/ident name.)
  @(d/transact class/conn
     (conj (mapv invert (tx-datoms bad-tx))
           {:db/id "datomic.tx"          ;; the revert annotates itself
            :audit/actor  "victor"
            :audit/reason "revert JIRA-1337 — stale CSV"
            :audit/ticket "JIRA-1338"}))

  ;; Present tense healed — tonsky restored, pithyless email-less again:
  (d/q '[:find ?who ?email
         :where [?e :user/name ?who]
                [?e :user/email ?email]]
       (db))
  ;; => #{["richhickey" "rich@example.com"]
  ;;      ["tonsky"     "tonsky@example.com"]
  ;;      ["victor"     "v@4coders.com.br"]}

  ;; And NOTHING was erased. The mistake and its revert are both
  ;; permanent history — T1's query still answers, and JIRA-1338
  ;; answers symmetrically. Try it:
  (d/q '[:find ?who ?aname ?v ?op
         :in $ ?ticket
         :where [?tx :audit/ticket ?ticket]
                [?e ?a ?v ?tx ?op]
                [(not= ?e ?tx)]
                [?a :db/ident ?aname]
                [?e :user/name ?who]]
       (d/history (db)) "JIRA-1338")
  ;; => three rows again — same shapes, ops flipped.

























  ;; ══════════════════════════════════════════ T4 · ~10 min ═══
  ;; SCHEMA EVOLVES, AUDIT SURVIVES
  ;;
  ;; Two product changes land the same week Legal asks for a full
  ;; data dossier. In migration-world that's a quarter. Here:
  ;;
  ;; 4a. Product: "twitter is dead, call it handle." — RENAME.
  ;;     Idents are just datoms on the attribute entity; rename it
  ;;     like any other fact (annotated, of course). Two mechanics
  ;;     the class only implied:
  ;;      · :db/id below is a KEYWORD. An ident resolves to its
  ;;        entity anywhere an entity id is expected — :user/twitter
  ;;        IS an entity id, spelled by name (§3).
  ;;      · asserting a new :db/ident on that entity IS the rename.
  ;;        The old name is kept as an "obsolete ident": it still
  ;;        resolves (proof two forms down), so code deployed against
  ;;        the old name doesn't break at flip time.
  (def t-before-rename (d/basis-t (db)))

  @(d/transact class/conn
     [{:db/id :user/twitter :db/ident ___}     ;; the new name
      {:db/id "datomic.tx"
       :audit/actor "victor" :audit/reason "product: twitter → handle"
       :audit/ticket "JIRA-1340"}])

  ;; the datom written in §4.2 under the OLD name, pulled by the new:
  (d/pull (db) [:user/handle] [:user/name "richhickey"])
  ;; => {:user/handle "@richhickey"}
  ;; No rewrite happened. The FACT never moved; only the attribute
  ;; entity's :db/ident datom changed. And the old name still
  ;; resolves (an "obsolete ident" — old code keeps working):
  (= (d/entid (db) :user/twitter) (d/entid (db) :user/handle))
  ;; => true

  ;; 4b. Product: "people have several handles now." — CARDINALITY.
  ;;     Alter the attribute by transacting the new value at it —
  ;;     installing and ALTERING schema are the same operation, and
  ;;     it takes effect immediately. (Datomic validates what may
  ;;     change: cardinality, uniqueness, index, isComponent,
  ;;     noHistory… but never :db/valueType — values already written
  ;;     under a type can't be reinterpreted.)
  @(d/transact class/conn
     [{:db/id :user/handle :db/cardinality ___}
      {:db/id "datomic.tx"
       :audit/actor "victor" :audit/reason "handles go multi-platform"
       :audit/ticket "JIRA-1341"}])

  @(d/transact class/conn
     [{:db/id [:user/name "richhickey"]
       :user/handle "@richhickey@mastodon.example"}
      {:db/id "datomic.tx"
       :audit/actor "victor" :audit/reason "add mastodon handle"
       :audit/ticket "JIRA-1341"}])

  (d/pull (db) [:user/handle] [:user/name "richhickey"])
  ;; => {:user/handle ["@richhickey" "@richhickey@mastodon.example"]}
  ;; One-to-many "migration": zero rows touched, zero downtime, and
  ;; the old datom is still the same datom.

  ;; 4c. Legal: "Everything you have EVER recorded about Rich Hickey.
  ;;     Values, timestamps, author, and business reason. By Friday."
  ;;     One query. Two holes: the source that remembers everything,
  ;;     and the clause that names each attribute.
  (->> (d/q '[:find ?tx ?inst ?aname ?v ?op ?actor ?why
              :in $ $hist ?e
              :where [$hist ?e ?a ?v ?tx ?op]
                     [$ ?a ___ ?aname]
                     [$ ?tx :db/txInstant ?inst]
                     [(get-else $ ?tx :audit/actor  "unattributed") ?actor]
                     [(get-else $ ?tx :audit/reason "—")            ?why]]
            (db) (___ (db)) [:user/name "richhickey"])
       (sort-by first)              ;; tx ids ARE the total order of time
       (map (fn [[_ inst a v op actor why]]
              {:when (str inst) :attr a :value v
               :op (if op "assert" "retract") :who actor :why why}))
       print-table)
  ;; =>
  ;; | :attr        | :value                        | :op    | :who          | :why                       |
  ;; |--------------+-------------------------------+--------+---------------+----------------------------|
  ;; | :user/name   | richhickey                    | assert | unattributed  | —                          |
  ;; | :user/email  | rich@example.com              | assert | unattributed  | —                          |
  ;; | :user/handle | @richhickey                   | assert | bruna         | COI import batch #42       |
  ;; | :user/handle | @richhickey@mastodon.example  | assert | victor        | add mastodon handle        |
  ;; (:when column elided here — you'll have real timestamps)
  ;;
  ;; Look at row three: that fact was WRITTEN as :user/twitter and
  ;; the dossier reports it as :user/handle. The audit answers in
  ;; today's vocabulary because names are idents — data, not DDL —
  ;; while the facts themselves never moved. No migration script was
  ;; consulted in the making of this report.

  ;; 4d. Epilogue — time-travel across the schema change:
  (d/q '[:find ?v .
         :in $ ?e
         :where [?e :user/handle ?v]]
       (d/as-of (db) t-before-rename) [:user/name "richhickey"])
  ;; => "@richhickey"
  ;; The as-of db predates the RENAME — yet the current ident finds
  ;; the old datom. Idents are resolved outside of time; only FACTS
  ;; are temporal.

  (d/pull (d/as-of (db) t-before-rename) '[*] [:user/name "richhickey"])
  ;; => {:db/id …, :user/name "richhickey",
  ;;     :user/email "rich@example.com",
  ;;     :user/handle ["@richhickey"]}
  ;;              ↑ new name AND new cardinality (a vector!) — with
  ;; exactly the one value that existed back then. Schema is read
  ;; through the present; facts through the past. Discuss: why is
  ;; that precisely the split an auditor wants?
  )

;; ═════════════════════════════════════════════════════════════════════
;; SOLUTIONS — no peeking until every ___ above made you sweat once.
;; ═════════════════════════════════════════════════════════════════════

(comment
























  ;; ─── T1 ───────────────────────────────────────────────────────────
  (def bad-tx
    (d/q '[:find ?tx .
           :in $ ?ticket
           :where [?tx :audit/ticket ?ticket]]
         (db) "JIRA-1337"))

  (->> (d/q '[:find ?who ?aname ?v ?op
              :in $ ?tx
              :where [?e ?a ?v ?tx ?op]          ;; tx = 4th slot of a datom
                     [(not= ?e ?tx)]
                     [?a :db/ident ?aname]
                     [?e :user/name ?who]]
            (d/history (db)) bad-tx)             ;; history — retractions live
       (sort-by (juxt first (comp str last))))   ;; only there

  ;; ─── T2 ───────────────────────────────────────────────────────────
  (->> (d/q '[:find ?who ?email ?actor ?inst
              :in $ $hist
              :where [$ ?e :user/name ?who]
                     [$ ?e :user/email ?email]
                     [$hist ?e :user/email ?email ?tx true]  ;; true = assert
                     [$ ?tx :db/txInstant ?inst]
                     [(get-else $ ?tx :audit/actor "unattributed") ?actor]]
            (db) (d/history (db)))
       (sort-by first)
       (map (fn [[who email actor _]] [who email actor])))

  ;; ─── T3 ───────────────────────────────────────────────────────────
  (defn tx-datoms [tx]
    (let [t (d/tx->t tx)]
      (->> (d/tx-range (d/log class/conn) t (inc t))
           first
           :data
           (remove (fn [dtm] (= (:e dtm) tx)))
           (map (fn [dtm] [(:e dtm) (:a dtm) (:v dtm) (:added dtm)])))))

  (defn invert [[e a v op]]
    (if op
      [:db/retract e a v]
      [:db/add     e a v]))

  ;; ─── T4 ───────────────────────────────────────────────────────────
  ;; 4a: {:db/id :user/twitter :db/ident :user/handle}
  ;; 4b: {:db/id :user/handle  :db/cardinality :db.cardinality/many}
  ;; 4c: [$ ?a :db/ident ?aname]  and  (d/history (db))
  ;;     (and the leading ?tx in :find exists only to sort by —
  ;;      tx ids are the one total order; txInstants can tie)
  )
