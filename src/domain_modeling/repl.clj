(ns domain-modeling.repl
  "ONE DATABASE, MANY SHAPES — Domain Modeling with Datoms
   ════════════════════════════════════════════════════════════════════
   REPL companion for the 2-hour class. Sections (§0–§7) mirror the
   slide deck: every slide tagged `REPL §n` has its live code here.

   Inspired by / rebuilt from:
     Norbert Wójtowicz — \"Domain Modeling with Datalog\"
     https://www.youtube.com/watch?v=oo-7mN9WXTw
     (Pivorak Conf 2.0, 2019 — originally a 2-hour workshop; this class
      is essentially the uncompressed version, live in Datomic.)

   ── Setup ──────────────────────────────────────────────────────────
   deps.edn (ships next to this file):

     {:paths [\"src\"]
      :deps  {org.clojure/clojure {:mvn/version \"1.12.0\"}
              com.datomic/peer    {:mvn/version \"1.0.7469\"}
              org.slf4j/slf4j-nop {:mvn/version \"2.0.13\"}}}

   Datomic Pro is free (Apache-2.0) and the peer library is on Maven
   Central — no account, no transactor process needed for this class:
   the `datomic:mem://` protocol runs everything in-process, with the
   FULL feature set (history, log, fulltext, speculative dbs...).

   Put this file at src/domain_modeling/repl.clj, then:
     clj -M:repl        ;; or jack-in from Calva/CIDER/clojure-mcp

   ── Conventions ────────────────────────────────────────────────────
   * Everything evaluable lives in (comment ...) blocks — nothing runs
     on load. Evaluate form-by-form, top to bottom.
   * Every deck slide tagged `REPL §n` has a `SLIDE n` anchor below
     (search: SLIDE 24). Teleporting mid-class? Eval the (goto! n)
     under the anchor — it narrates which part of the class you're in
     and rebuilds the db to that slide's exact starting state, so every
     block runs standalone. The blank-line gaps between blocks are
     intentional: no spoilers while scrolling.
   * `;; =>` shows the expected result. Entity ids (17592186045xxx)
     and t values WILL differ on your machine — the shapes won't.
   * `db` is always a *value*: grab a fresh one after each transact."
  (:require [datomic.api :as d]
            [clojure.pprint :refer [pprint print-table]]
            [clojure.string :as str]))

;; ═════════════════════════════════════════════════════════════════════
;; §0 · SETUP — a database in one expression                 (slide 10)
;; ═════════════════════════════════════════════════════════════════════

(def uri "datomic:mem://github")

(defonce conn
  ;; connect on load — the file is "open REPL and go"
  (do (d/create-database uri)
      (d/connect uri)))

(defn fresh!
  "Nuke and recreate the in-memory db. Run between sections if you
   want a clean slate (each § below re-seeds what it needs)."
  []
  (d/delete-database uri)
  (d/create-database uri)
  (alter-var-root #'conn (constantly (d/connect uri)))
  :ok)

(defn db
  "Current database VALUE. Cheap — it's a pointer into a persistent
   data structure, exactly like `deref` on an atom. Queries never see
   later writes: you query a value, not a place."
  []
  (d/db conn))

(defn datom->vec
  "Render a raw datom as the honest 5-tuple it is."
  [db* dtm]
  [(:e dtm) (d/ident db* (:a dtm)) (:v dtm) (:tx dtm) (:added dtm)])

(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 10 ═══
  ;; §0 · A database in one expression
  (goto! 10)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  (fresh!)                                  ;; => :ok
  (db)                                      ;; => datomic.db.Db@...
  ;; The db value already contains ~266 datoms: Datomic's own schema,
  ;; partitions, and built-in idents. Turtles all the way down — the
  ;; system describes itself with the same 5-tuples you're about to use.
  (count (seq (d/datoms (db) :eavt)))       ;; => 266  (± a few by version)
  )

;; ═════════════════════════════════════════════════════════════════════
;; §1 · THE DATOM — the smallest fact                     (slides 11-14)
;; ═════════════════════════════════════════════════════════════════════
;; A datom is a 5-tuple:
;;
;;   [entity  attribute  value  transaction  added?]
;;    ─ who ── what ───── how ──── when ────── assert/retract
;;
;; That's the whole physical model. Rows, documents, graphs, key-value
;; pairs, event logs — every "shape" in this class is just an
;; arrangement of these. We start with the E-A-V core; Tx and Op join
;; the party in §6 (time).

;; ─── 1.1 A little schema (attributes are entities — more in §3) ─────

(def user-schema
  [{:db/ident       :user/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity   ;; upsert key + lookup-ref
    :db/doc         "GitHub handle. One per user, users own one."}
   {:db/ident       :user/email
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/value      ;; no two users share an email
    :db/doc         "Primary email."}])

(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 11 ═══
  ;; §1.1 · Attributes first: a little schema
  (goto! 11)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  (fresh!)
  @(d/transact conn user-schema)
  ;; => {:db-before ..., :db-after ..., :tx-data [...], :tempids {...}}
  ;; Schema is transacted like any other data, because it IS data.
























  ;; ═════════════════════════════════════════════════ SLIDE 12 ═══
  ;; §1.2 · First facts
  (goto! 12)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 1.2 First facts: three users ────────────────────────────────
  ;; (The cast from the talk: Rich Hickey, Nikita Tonsky, Norbert
  ;;  a.k.a. pithyless — plus you.)
  @(d/transact conn
     [{:user/name "richhickey"}
      {:user/name "tonsky"}
      {:user/name "pithyless"}])

  ;; Map syntax is sugar. What actually hit the log is list form:
  ;;   [:db/add  <new-eid>  :user/name  "richhickey"]
  ;; One datom per attribute. An "entity" is nothing more than all
  ;; datoms that share an E. There is no user record anywhere.

























  ;; ═════════════════════════════════════════════════ SLIDE 13 ═══
  ;; §1.3 · Look at the raw datoms
  (goto! 13)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 1.3 Look at the raw datoms ──────────────────────────────────
  (let [db* (db)]
    (->> (d/datoms db* :avet :user/name)
         (map #(datom->vec db* %))))
  ;; => ([17592186045420 :user/name "pithyless"  13194139534313 true]
  ;;     [17592186045418 :user/name "richhickey" 13194139534313 true]
  ;;     [17592186045419 :user/name "tonsky"     13194139534313 true])
  ;;
  ;; Note the 5 positions. Same tx for all three (one transaction),
  ;; :added true (assertions). This flat, sorted bag of tuples is the
  ;; entire database. Everything else in the next 2 hours is a *view*.

























  ;; ═════════════════════════════════════════════════ SLIDE 14 ═══
  ;; §1.4 · Lookup refs: identity that travels
  (goto! 14)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 1.4 Entity ids without ceremony ─────────────────────────────
  ;; :user/name is :db.unique/identity, so a 2-element vector is an
  ;; ADDRESS for an entity — a "lookup ref". Use it anywhere an eid
  ;; is expected. (This is your primary key, and it travels.)
  (d/entid (db) [:user/name "tonsky"])      ;; => 17592186045419
  (d/entity (db) [:user/name "tonsky"])     ;; => lazy entity map
  (d/touch (d/entity (db) [:user/name "tonsky"]))
  ;; => {:db/id 17592186045419, :user/name "tonsky"}
  )

;; ═════════════════════════════════════════════════════════════════════
;; §2 · QUERY — pattern matching all the way down         (slides 16-25)
;; ═════════════════════════════════════════════════════════════════════
;; A where-clause is a datom with holes:
;;
;;   [?e :user/name "tonsky"]   ?x  → variable (bind it)
;;   [?e :user/name _      ]    _   → wildcard (don't care)
;;   [?e ?a         ?v     ]    all vars → full scan (legal, dumb)
;;
;; The engine finds every datom the pattern matches. Multiple clauses
;; UNIFY: reuse a variable name and its values must agree everywhere.
;; That's a join. You never write JOIN again.

(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 17 ═══
  ;; §2.1 · Four result shapes (and a trap)   [slide 16 = the where-clause above]
  (goto! 17)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 2.1 The four result shapes ──────────────────────────────────
  (d/q '[:find ?name
         :where [_ :user/name ?name]]
         (db))
  ;; => #{["pithyless"] ["richhickey"] ["tonsky"]}     relation (set of tuples)

  (d/q '[:find [?name ...]                             ;; collection
         :where [_ :user/name ?name]]
       (db))
  ;; => ["pithyless" "richhickey" "tonsky"]

  (d/q '[:find ?name .                                 ;; scalar
         :where [_ :user/name ?name]]
       (db))
  ;; => "pithyless"
  ;; ⚠ Classroom trap (straight from the talk): which one does `.`
  ;; return? Results are SETS — no order — so "one of them". If you
  ;; want order, sort in Clojure: (sort (d/q ...)).

  (d/q '[:find [?name ?email]                          ;; single tuple
         :where [_ :user/name ?name]
                [_ :user/email ?email]]
       (db))
  ;; => nil — nobody has an email yet. Also correct! No nulls, no rows,
  ;; just the absence of facts.

























  ;; ═════════════════════════════════════════════════ SLIDE 18 ═══
  ;; §2.2–2.3 · Flip what you know; joins for free
  (goto! 18)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 2.2 Flip what you know — declarative means no direction ─────
  (d/q '[:find ?e .
         :where [?e :user/name "tonsky"]] (db))   ;; name → id
  ;; => 17592186045419

  (d/q '[:find ?name .
         :in $ ?e
         :where [?e :user/name ?name]]
       (db) [:user/name "tonsky"])                ;; id → name (silly, legal)
  ;; => "tonsky"

  ;; ─── 2.3 Joins: same variable = same value ───────────────────────
  @(d/transact conn
     [{:db/id [:user/name "richhickey"] :user/email "rich@example.com"}
      {:db/id [:user/name "tonsky"]     :user/email "tonsky@example.com"}])
  ;; New facts, appended. Nothing was rewritten: the users just grew.

  (d/q '[:find ?email .
         :where [?e :user/name "tonsky"]     ;; bind ?e ...
                [?e :user/email ?email]]     ;; ... reuse ?e ⇒ join
       (db))
  ;; => "tonsky@example.com"

  ;; And backwards, because why not:
  (d/q '[:find ?name .
         :where [?e :user/email "rich@example.com"]
                [?e :user/name ?name]]
       (db))
  ;; => "richhickey"

























  ;; ═════════════════════════════════════════════════ SLIDE 19 ═══
  ;; §2.4–2.5 · Parameterize; call the host mid-query
  (goto! 19)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 2.4 :in — parameterize with scalars, tuples, collections ────
  (d/q '[:find [?name ...]
         :in $ [?name ...]                    ;; collection binding
         :where [?e :user/name ?name]
                [?e :user/email _]]           ;; keep only those w/ email
       (db) ["tonsky" "pithyless" "richhickey"])
  ;; => ["richhickey" "tonsky"]      (set semantics — order isn't yours)

  ;; ─── 2.5 Predicates: call the host platform mid-query ────────────
  (d/q '[:find [?name ...]
         :where [?e :user/name ?name]
                [(clojure.string/starts-with? ?name "t")]]
       (db))
  ;; => ["tonsky"]
  ;; Any pure fn/method works: (.startsWith ?name "t"), (< ?stars 100)…
  ;; Same syntax runs .startsWith on the JVM (Datomic) and on JS
  ;; (DataScript). The query language brokers to the host.

























  ;; ═════════════════════════════════════════════════ SLIDE 20 ═══
  ;; §2.6 · Absence is information
  (goto! 20)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 2.6 missing? / get-else — absence is information ────────────
  (d/q '[:find [?name ...]
         :where [?e :user/name ?name]
                [(missing? $ ?e :user/email)]]
       (db))
  ;; => ["pithyless"]   (the sparse row that costs nothing to store)

  (d/q '[:find ?name ?email
         :where [?e :user/name ?name]
                [(get-else $ ?e :user/email "—") ?email]]
       (db))
  ;; => #{["pithyless" "—"] ["richhickey" "rich@example.com"]
  ;;      ["tonsky" "tonsky@example.com"]}
  )

;; ─── 2.7 Refs & polymorphism: orgs, repos, one owner attribute ──────
;; The SQL interview question: repos are owned by users OR orgs.
;; Nullable columns? Join tables? Polymorphic FK + type column?
;; Here: values can be REFERENCES. One attribute, any target.

(def repo-schema
  [{:db/ident       :org/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :repo/slug
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Repo name. NOT unique — identity is owner+slug."}
   {:db/ident       :repo/owner
    :db/valueType   :db.type/ref          ;; ← points at ANY entity
    :db/cardinality :db.cardinality/one}
   {:db/ident       :repo/fork
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Origin this repo was forked from. Absent ⇒ original."}
   {:db/ident       :repo/language
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/many} ;; ← many: just repeat datoms
   {:db/ident       :user/stars
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         "Repos this user starred. user ─stars→ repo."}
   {:db/ident       :user/follows
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}
   ;; composite tuple: THE Datomic answer to "unique owner+slug"
   {:db/ident       :repo/owner+slug
    :db/valueType   :db.type/tuple
    :db/tupleAttrs  [:repo/owner :repo/slug]
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "Auto-maintained composite key ⇒ lookup-ref-able."}])

(def seed-owners
  ;; Owners must exist BEFORE repos can point at them with lookup
  ;; refs: a lookup ref resolves against the db as of the START of
  ;; its tx, so owner + repo in ONE tx dies with
  ;; :db.error/not-an-entity. (Inside a single tx, string tempids
  ;; are the tool for that job — see "clj"/"ds" below.)
  [{:user/name "victor" :user/email "victor@example.com"}
   {:org/name "clojure"}])

(def seed
  [;; string :db/id = tempid, resolvable within this tx
   {:db/id "clj"  :repo/slug "clojure"
    :repo/owner [:org/name "clojure"]          ;; lookup ref as value!
    :repo/language #{"Java" "Clojure"}}
   {:db/id "ds"   :repo/slug "datascript"
    :repo/owner [:user/name "tonsky"]          ;; …or a user. Same attr.
    :repo/language #{"Clojure" "ClojureScript"}}
   {:db/id "ds-n" :repo/slug "datascript"      ;; pithyless' fork
    :repo/owner [:user/name "pithyless"]
    :repo/fork "ds"
    :repo/language #{"Clojure"}}
   {:db/id "ds-v" :repo/slug "datascript"      ;; your fork OF THE FORK
    :repo/owner [:user/name "victor"]
    :repo/fork "ds-n"
    :repo/language #{"Clojure"}}
   ;; stars: user ─→ repo, cardinality many
   {:db/id [:user/name "tonsky"]    :user/stars #{"clj"}}
   {:db/id [:user/name "pithyless"] :user/stars #{"clj" "ds"}}
   {:db/id [:user/name "victor"]    :user/stars #{"clj"}}
   ;; a follow chain for §4.5 graph traversal. GOTCHA: on a
   ;; cardinality-MANY attr a bare vector means MANY VALUES, so a
   ;; naked lookup ref would splinter into `:user/name` + a tempid.
   ;; Wrap it in a set (or vector-of-one):
   {:db/id [:user/name "victor"]    :user/follows #{[:user/name "pithyless"]}}
   {:db/id [:user/name "pithyless"] :user/follows #{[:user/name "tonsky"]}}
   {:db/id [:user/name "tonsky"]    :user/follows #{[:user/name "richhickey"]}}])

(def rules
  '[;; polymorphism, named: an owner's name, whatever the owner is
    [(owner-name ?r ?n) [?r :repo/owner ?o] [?o :user/name ?n]]
    [(owner-name ?r ?n) [?r :repo/owner ?o] [?o :org/name  ?n]]
    ;; recursion: transitive follows (the Kevin Bacon move)
    [(follows ?a ?b) [?a :user/follows ?b]]
    [(follows ?a ?b) [?a :user/follows ?x] (follows ?x ?b)]
    ;; recursion: walk a fork chain to every ancestor
    [(fork-of ?f ?o) [?f :repo/fork ?o]]
    [(fork-of ?f ?o) [?f :repo/fork ?x] (fork-of ?x ?o)]])

(defn repo
  "Resolve a repo eid by owner display-name + slug (owner may be a
   user or an org — the rule above hides which)."
  [db* owner slug]
  (d/q '[:find ?r .
         :in $ % ?owner ?slug
         :where (owner-name ?r ?owner)
                [?r :repo/slug ?slug]]
       db* rules owner slug))



(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 21 ═══
  ;; §2.7 · Refs: the owner interview question
  (goto! 21)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; GOTCHA: this seeding is NOT re-runnable in place. Repo identity is
  ;; the composite tuple :repo/owner+slug, and composite tuples don't
  ;; participate in upsert — so transacting `seed` onto an already-
  ;; seeded db dies with :db.error/unique-conflict. (Contrast
  ;; seed-owners: :user/name / :org/name are asserted directly, so it
  ;; upserts and is idempotent.) Hence the (goto! 21) inside the `do`:
  ;; refresh to the start of this slide, THEN seed — safe to re-eval.
  (do (goto! 21)
      @(d/transact conn repo-schema)
      @(d/transact conn seed-owners)  ;; owners FIRST — see note above
      @(d/transact conn seed))

























  ;; ═════════════════════════════════════════════════ SLIDE 22 ═══
  ;; §2.8 · Polymorphism happens at read time
  (goto! 22)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 2.8 One attribute, two kinds of owner ───────────────────────
  (d/q '[:find ?slug .
         :where [?u :user/name "tonsky"]
                [?r :repo/owner ?u]
                [?r :repo/slug ?slug]]
       (db))
  ;; => "datascript"

  (d/q '[:find ?slug .
         :where [?o :org/name "clojure"]
                [?r :repo/owner ?o]
                [?r :repo/slug ?slug]]
       (db))
  ;; => "clojure"
  ;; Swap ONE clause and the "type" of the owner changes. Schema was
  ;; applied at READ time. An entity that had both :user/name and
  ;; :org/name would be both — polymorphism without a class hierarchy.

  ;; or-join sugar / rules do it in one query:
  (d/q '[:find ?owner ?slug
         :in $ %
         :where (owner-name ?r ?owner)
                [?r :repo/slug ?slug]]
       (db) rules)
  ;; => #{["clojure" "clojure"] ["tonsky" "datascript"]
  ;;      ["pithyless" "datascript"] ["victor" "datascript"]}

























  ;; ═════════════════════════════════════════════════ SLIDE 23 ═══
  ;; §2.9 · Forks: self-joins & meaningful absence
  (goto! 23)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 2.9 Forks: relationships between rows of the "same table" ───
  (d/q '[:find ?fork-owner ?orig-owner
         :in $ %
         :where [?f :repo/fork ?orig]
                (owner-name ?f ?fork-owner)
                (owner-name ?orig ?orig-owner)]
       (db) rules)
  ;; => #{["pithyless" "tonsky"] ["victor" "pithyless"]}
  ;; Same entity plays ?f in one binding and ?orig in another. In SQL:
  ;; self-join with aliases. Here: two variable names. That's it.

  ;; Originals = repos that simply LACK the attribute:
  (d/q '[:find [?slug ...]
         :where [?r :repo/slug ?slug]
                [(missing? $ ?r :repo/fork)]]
       (db))
  ;; => ["clojure" "datascript"]

























  ;; ═════════════════════════════════════════════════ SLIDE 24 ═══
  ;; §2.10–2.11 · Cardinality-many & the only metric that matters
  (goto! 24)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 2.10 Cardinality-many: repeat yourself, proudly ─────────────
  (d/q '[:find [?lang ...] :where [_ :repo/language ?lang]] (db))
  ;; => ["Java" "ClojureScript" "Clojure"]   (6 datoms → 3, sets dedupe)

  ;; ─── 2.11 Aggregation: stars, the only metric that matters ───────
  (d/q '[:find ?slug (count ?u)
         :where [?u :user/stars ?r]
                [?r :repo/slug ?slug]]
       (db))
  ;; => [["clojure" 3] ["datascript" 1]]
  ;; Non-aggregated find vars group; (count …) folds each group.
  ;; sum/avg/max/min/count-distinct/variance/stddev all built in.

























  ;; ═════════════════════════════════════════════════ SLIDE 25 ═══
  ;; §2.11–2.12 · The :with trap; negation
  (goto! 25)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ⚠ :with — sets collapse duplicates BEFORE aggregation:
  (d/q '[:find (count ?r) .
         :where [?u :user/stars ?r]] (db))
  ;; => 2      distinct repos-with-stars… probably not what you meant
  (d/q '[:find (count ?r) .
         :with ?u                            ;; keep (u,r) pairs alive
         :where [?u :user/stars ?r]] (db))
  ;; => 4      total star events. :with = "don't dedupe over these".

  ;; ─── 2.12 not / not-join ─────────────────────────────────────────
  (d/q '[:find [?name ...]
         :where [?u :user/name ?name]
                (not [?u :user/stars _])]
       (db))
  ;; => ["richhickey"]   (too busy hammocking to star repos)
  )

;; ═════════════════════════════════════════════════════════════════════
;; §3 · SCHEMA IS DATA — attributes are entities          (slides 26-29)
;; ═════════════════════════════════════════════════════════════════════

(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 27 ═══
  ;; §3 · Attributes are entities
  (goto! 27)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; :user/name is not a column definition in a catalog. It's an
  ;; ENTITY in the same database, with datoms about it:
  (d/pull (db) '[*] :user/name)
  ;; => {:db/id 72, :db/ident :user/name,
  ;;     :db/valueType {:db/id 23},     ;; ← 23 IS :db.type/string; `*`
  ;;     :db/cardinality {:db/id 35},   ;;   shows refs as bare ids —
  ;;     :db/unique {:db/id 38},        ;;   pull {:db/valueType [:db/ident]} for names
  ;;     :db/doc "GitHub handle. One per user, users own one."}

  ;; So you can QUERY your schema with the same language:
  (d/q '[:find [?ident ...]
         :where [?a :db/valueType :db.type/ref]
                [?a :db/ident ?ident]
                [(namespace ?ident) ?ns]   ;; expression clauses don't
                [(= ?ns "repo")]]          ;; nest: bind, THEN test
       (db))
  ;; => [:repo/fork :repo/owner]

























  ;; ═════════════════════════════════════════════════ SLIDE 28 ═══
  ;; §3 · Growth = accretion; composite identity
  (goto! 28)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; The composite tuple did its job during seeding — owner+slug is a
  ;; real unique key you can lookup-ref through:
  (let [tonsky (d/entid (db) [:user/name "tonsky"])]
    (d/pull (db) [:repo/slug {:repo/owner [:user/name]}]
            [:repo/owner+slug [tonsky "datascript"]]))
  ;; => {:repo/slug "datascript", :repo/owner {:user/name "tonsky"}}

  ;; Growth = accretion. Product wants "topics"? Transact ONE map.
  ;; No migration. No ALTER TABLE. No downtime. Old data untouched;
  ;; old queries still true.
  @(d/transact conn
     [{:db/ident       :repo/topics
       :db/valueType   :db.type/string
       :db/cardinality :db.cardinality/many}])
  @(d/transact conn
     [{:db/id (repo (db) "tonsky" "datascript")
       :repo/topics #{"database" "datalog" "clojurescript"}}])

























  ;; ═════════════════════════════════════════════════ SLIDE 29 ═══
  ;; §3 · Enums are idents
  (goto! 29)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; Enums? Entities with :db/ident — refs to well-known names:
  @(d/transact conn
     [{:db/ident :repo.visibility/public}
      {:db/ident :repo.visibility/private}
      {:db/ident       :repo/visibility
       :db/valueType   :db.type/ref
       :db/cardinality :db.cardinality/one}])
  @(d/transact conn
     [{:db/id (repo (db) "victor" "datascript")
       :repo/visibility :repo.visibility/private}])
  ;; Idents are free foreign keys into a one-row dimension table —
  ;; typo-proof (unknown ident ⇒ tx fails), renamable, queryable.
  )

;; ═════════════════════════════════════════════════════════════════════
;; §4 · ONE STORE, MANY SHAPES                            (slides 30-41)
;; ═════════════════════════════════════════════════════════════════════
;; The pitch of this whole class:
;;
;;   Sort the SAME datoms four ways and you get four databases.
;;
;;   EAVT  entity-first     → a row store        (PostgreSQL-ish)
;;   AEVT  attribute-first  → a column store     (warehouse-ish)
;;   AVET  attr+value       → a key-value store  (Redis-ish)
;;   VAET  value-first*     → a graph, reversed  (Neo4j-ish)
;;                            (*refs only)
;;
;; Datomic maintains all four continuously. `d/datoms` gives you raw,
;; lazy, sorted access to each — the query engine is "just" a
;; constraint solver walking these.

;; ─── §4.1 · Shape: KEY-VALUE store (AVET) ────────────────────────────
(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 32 ═══
  ;; §4.1 · Shape 1/7: key-value store (AVET)   [slide 31 = the core trick above]
  (goto! 32)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; GET user by natural key — a lookup ref IS a key:
  (d/pull (db) [:user/email] [:user/name "tonsky"])
  ;; => {:user/email "tonsky@example.com"}

  ;; That sugar is powered by the AVET index. See it raw:
  (->> (d/datoms (db) :avet :user/name)
       (map (juxt :v :e)))
  ;; => (["pithyless" 17592186045420] ["richhickey" 17592186045418]
  ;;     ["tonsky" 17592186045419] ["victor" 17592186045424])
  ;; Sorted by (A,V) ⇒ point lookups AND range scans:
  (->> (d/index-range (db) :user/name "p" "u")   ;; names in [p,u)
       (map :v))
  ;; => ("pithyless" "richhickey" "tonsky")
  ;; SET = transact. DEL = retract. TTL = your cron + retract. Redis,
  ;; minus the second database to keep consistent.
  )

;; ─── §4.2 · Shape: RELATIONAL rows (EAVT) ────────────────────────────
(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 33 ═══
  ;; §4.2 · Shape 2/7: relational rows (EAVT)
  (goto! 33)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; entity = row, attribute = column, query = SELECT:
  ;;
  ;;   SELECT u.name, u.email, count(s.repo_id)
  ;;   FROM users u JOIN stars s ON s.user_id = u.id
  ;;   GROUP BY u.id;
  (->> (d/q '[:find ?name ?email (count ?r)
              :keys  user email starred            ;; maps out = rows
              :where [?u :user/name ?name]
                     [(get-else $ ?u :user/email "—") ?email]
                     [?u :user/stars ?r]]
            (db))
       (sort-by :user)
       print-table)
  ;;  | :user     | :email             | :starred |
  ;;  |-----------+--------------------+----------|
  ;;  | pithyless | —                  |        2 |
  ;;  | tonsky    | tonsky@example.com |        1 |
  ;;  | victor    | victor@example.com |        1 |
  ;; (richhickey has no :user/stars datom ⇒ no row: clause semantics
  ;;  are inner joins. Want SQL's LEFT JOIN? get-else, or pull.)

  ;; The row itself, materialized on demand from EAVT:
  (->> (d/datoms (db) :eavt [:user/name "tonsky"])
       (map #(datom->vec (db) %)))
  ;; => all datoms for that E, contiguous on "disk" — that IS the row.
  ;; Difference from SQL: the row is a VIEW you asked for, not the
  ;; storage physics you're stuck with.
  )

;; ─── §4.3 · Shape: COLUMN store (AEVT) ───────────────────────────────
(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 34 ═══
  ;; §4.3 · Shape 3/7: column store (AEVT)
  (goto! 34)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; Warehouse question: language distribution across all repos —
  ;; touch ONE attribute, never load a row:
  (->> (d/datoms (db) :aevt :repo/language)
       (map :v)
       frequencies)
  ;; => {"Clojure" 4, "Java" 1, "ClojureScript" 1}
  ;; AEVT physically clusters one attribute across all entities:
  ;; the definition of columnar. Same data, analytics-friendly order.

  ;; In query form (engine picks the index for you):
  (d/q '[:find ?lang (count ?r)
         :where [?r :repo/language ?lang]]
       (db))
  ;; => [["Clojure" 4] ["ClojureScript" 1] ["Java" 1]]
  )

;; ─── §4.4 · Shape: DOCUMENT store (nested tx maps + pull) ────────────
(def issue-schema
  [{:db/ident       :repo/issues
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true          ;; issues BELONG to their repo
    :db/doc "Component: retracting the repo retracts its issues."}
   {:db/ident       :issue/title
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/fulltext    true}         ;; Lucene index, set at creation
   {:db/ident       :issue/state
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :issue.state/open}
   {:db/ident :issue.state/closed}
   {:db/ident       :issue/comments
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true}
   {:db/ident       :comment/body
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :comment/author
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}])

(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 35 ═══
  ;; §4.4 · Shape 4/7: documents — write (nested maps in)
  (goto! 35)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  @(d/transact conn issue-schema)

  ;; WRITE a document: one nested map in, a tree of datoms out.
  ;; (Exactly what you'd POST as JSON — Datomic shreds it for you.)
  @(d/transact conn
     [{:db/id (repo (db) "tonsky" "datascript")
       :repo/issues
       [{:issue/title "Support tuple bindings in :find"
         :issue/state :issue.state/open
         :issue/comments
         [{:comment/body   "PR incoming — same trick as Datomic?"
           :comment/author [:user/name "pithyless"]}
          {:comment/body   "Yes. Keep the API surface identical."
           :comment/author [:user/name "tonsky"]}]}]}])

























  ;; ═════════════════════════════════════════════════ SLIDE 36 ═══
  ;; §4.4 · Shape 4/7: documents — read (pull)
  (goto! 36)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; READ a document: pull = declarative tree projection (GraphQL,
  ;; but it's data, not a string DSL — this is literally what EQL and
  ;; Fulcro grew out of):
  (d/pull (db)
          '[:repo/slug
            {:repo/owner [:user/name]}
            {:repo/issues
             [:issue/title
              {:issue/state [:db/ident]}
              {:issue/comments
               [:comment/body {:comment/author [:user/name]}]}]}]
          (repo (db) "tonsky" "datascript"))
  ;; => {:repo/slug "datascript",
  ;;     :repo/owner {:user/name "tonsky"},
  ;;     :repo/issues
  ;;     [{:issue/title "Support tuple bindings in :find",
  ;;       :issue/state {:db/ident :issue.state/open},
  ;;       :issue/comments
  ;;       [{:comment/body "PR incoming — same trick as Datomic?",
  ;;         :comment/author {:user/name "pithyless"}}
  ;;        {:comment/body "Yes. Keep the API surface identical.",
  ;;         :comment/author {:user/name "tonsky"}}]}]}
  ;;
  ;; MongoDB gives you this shape. Only this shape. Here the document
  ;; boundary is drawn PER QUERY — comments by author? flip the tree:

























  ;; ═════════════════════════════════════════════════ SLIDE 37 ═══
  ;; §4.4 · Reverse refs, cascades, fulltext
  (goto! 37)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  (d/pull (db)
          '[:user/name
            {:comment/_author            ;; ← reverse ref: who points at me
             [:comment/body {:issue/_comments [:issue/title]}]}]
          [:user/name "pithyless"])
  ;; => {:user/name "pithyless",
  ;;     :comment/_author
  ;;     [{:comment/body "PR incoming — same trick as Datomic?",
  ;;       :issue/_comments {:issue/title "Support tuple bindings..."}}]}
  ;; (reverse via a component comes back as a map, not a vector —
  ;;  a child has exactly one parent.)

  ;; pull niceties: wildcards, defaults, limits, nesting depth
  (d/pull (db) '[* {:repo/owner [*]}] (repo (db) "tonsky" "datascript"))
  (d/pull (db) '[:user/name (default :user/email "—")]
          [:user/name "pithyless"])
  ;; => {:user/name "pithyless", :user/email "—"}

  ;; Components give documents their OWNED-ness — cascade on retract:
  (let [before (d/q '[:find (count ?c) . :where [?c :comment/body _]] (db))
        {db2 :db-after}
        (d/with (db) [[:db/retractEntity
                       (repo (db) "tonsky" "datascript")]])
        after  (d/q '[:find (count ?c) . :where [?c :comment/body _]] db2)]
    {:comments-before before :comments-after after})
  ;; => {:comments-before 2, :comments-after nil}
  ;; (ran speculatively with d/with — the real db still has them. §6.)

  ;; Fulltext (because :issue/title had :db/fulltext true):
  (d/q '[:find ?title ?score
         :where [(fulltext $ :issue/title "tuple")
                 [[?i ?title _ ?score]]]]
       (db))
  ;; => #{["Support tuple bindings in :find" 1.0]}   (score is Lucene's)
  ;; Lucene, in-process, addressed from Datalog. NOTE: since adaptive
  ;; indexing, fulltext updates in the background — if this comes back
  ;; empty right after the transact, wait a beat and re-run.
  )

;; ─── §4.5 · Shape: GRAPH database (VAET + recursive rules) ───────────
(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 38 ═══
  ;; §4.5 · Shape 5/7: graph database (VAET + recursive rules)
  (goto! 38)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; Neo4j's trick is answering "who points AT me?". That's VAET —
  ;; the reverse index over every ref in the system:
  (let [ds  (repo (db) "tonsky" "datascript")
        db* (db)]
    (->> (d/datoms db* :vaet ds)
         (map (fn [dtm]
                (let [src (d/entity db* (:e dtm))]
                  [(d/ident db* (:a dtm))
                   (or (:user/name src) (:repo/slug src))])))))
  ;; => ([:repo/fork "datascript"]      pithyless' fork points here
  ;;     [:user/stars "pithyless"])     pithyless starred it
  ;; Every ref is bidirectional for free. No edge tables, no
  ;; :incoming/:outgoing config, no index hints.

  ;; Traversal, entity-style (lazy navigation, both directions):
  (->> (d/entity (db) [:user/name "victor"])
       :user/follows (map :user/name))          ;; → outgoing
  ;; => ("pithyless")
  (->> (d/entity (db) [:user/name "tonsky"])
       :user/_follows (map :user/name))         ;; ← incoming
  ;; => ("pithyless")

  ;; Traversal, Datalog-style — RECURSIVE rules (see `rules` above):
  (d/q '[:find [?name ...]
         :in $ % ?start
         :where (follows ?start ?x)
                [?x :user/name ?name]]
       (db) rules [:user/name "victor"])
  ;; => ["pithyless" "richhickey" "tonsky"]    (set semantics, again)
  ;; Arbitrary depth, no UNION ALL recursive CTE, no max-hops guess.
  ;; This is the Kevin Bacon query.

  ;; Fork ancestry — same recursion pattern on a different edge:
  (d/q '[:find [?owner ...]
         :in $ % ?fork
         :where (fork-of ?fork ?orig)
                (owner-name ?orig ?owner)]
       (db) rules (repo (db) "victor" "datascript"))
  ;; => ["pithyless" "tonsky"]     (fork of a fork ⇒ two ancestors)

























  ;; ═════════════════════════════════════════════════ SLIDE 39 ═══
  ;; §4.5 · The mesh payoff: a recommender in five clauses
  (goto! 39)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; The mesh payoff — a recommender in five lines:
  ;; "repos starred by people I (transitively) follow, minus mine"
  (d/q '[:find ?owner ?slug
         :in $ % ?me
         :where (follows ?me ?friend)
                [?friend :user/stars ?r]
                (not [?me :user/stars ?r])
                [?r :repo/slug ?slug]
                (owner-name ?r ?owner)]
       (db) rules [:user/name "victor"])
  ;; => #{["tonsky" "datascript"]}
  ;; Users, follows, stars, repos, owners: five "tables", zero JOINs
  ;; written, one recursive hop. This query is why the class exists.
  )

;; ─── §4.6 · Shape: EVENT LOG / stream (the log IS the database) ─────
(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 40 ═══
  ;; §4.6 · Shape 6/7: the log IS the database
  (goto! 40)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; The transaction log is API-accessible — your system's history as
  ;; a stream of fact-sets:
  (->> (d/tx-range (d/log conn) nil nil)  ;; nil nil = everything
       (take 3)
       (map (fn [{:keys [t data]}] {:t t :datom-count (count data)})))
  ;; => ({:t 1000 :datom-count 13} {:t 1001 :datom-count 4} ...)
  ;; (the log begins at YOUR first tx — t 1000; Datomic's bootstrap
  ;;  isn't replayed. Gaps between t values are normal.)

  ;; Event sourcing's hardest problem is schema migration on old
  ;; events. Datoms don't have that problem: an event here is not
  ;; {:type :UserRenamed :payload {...v3...}} — it's plain facts.
  ;; New attributes accrete; old facts remain true and queryable.
  ;; Projections? That's what the four indexes ARE. You've been
  ;; running CQRS since §0 without ceremony.
  )

;; ─── §4.7 · Shape: SPARSE matrix / wide table ────────────────────────
(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 40 ═══
  ;; §4.7 · Shape 7/7: sparse matrix / wide table
  (goto! 40)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; A "table" with 500 optional columns costs 500×N nulls in a
  ;; rectangle. Here, an absent fact occupies zero bytes and even
  ;; MEANS something (§2.6/§2.9: missing? found originals & the
  ;; email-less). Add one-off attributes with no guilt:
  @(d/transact conn
     [{:db/ident       :user/twitter
       :db/valueType   :db.type/string
       :db/cardinality :db.cardinality/one}])
  @(d/transact conn
     [{:db/id [:user/name "tonsky"] :user/twitter "@tonsky"}])
  ;; One user has it. Three don't. Nothing is null. Nobody migrated.
  (d/q '[:find ?name ?tw
         :where [?u :user/name ?name]
                [(get-else $ ?u :user/twitter "—") ?tw]]
       (db))
  ;; => #{["victor" "—"] ["tonsky" "@tonsky"]
  ;;      ["pithyless" "—"] ["richhickey" "—"]}
  )

;; ═════════════════════════════════════════════════════════════════════
;; §5 (recap slide 41) — the same datoms wore seven costumes.
;; The moves that changed: WHICH INDEX ORDER + WHICH PROJECTION.
;; Query language, transaction shape, storage: identical throughout.
;; ═════════════════════════════════════════════════════════════════════

;; ═════════════════════════════════════════════════════════════════════
;; §6 · TIME — columns four and five                      (slides 42-48)
;; ═════════════════════════════════════════════════════════════════════
;; E-A-V models your domain AT REST. Change over time is the other two:
;;   Tx  — which transaction (atomicity + provenance handle)
;;   Op  — true (assert) | false (retract)
;; "Update" is not a primitive. It's retract + assert, same Tx.

(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 43 ═══
  ;; §6.1 · "Update" is not a primitive
  (goto! 43)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 6.1 Update-in-place, decomposed ─────────────────────────────
  (def victor [:user/name "victor"])
  (def t-before (d/basis-t (db)))            ;; bookmark for time travel

  @(d/transact conn
     [{:db/id victor :user/email "victor@4coders.com.br"}])
  ;; For cardinality-one, Datomic expands this to BOTH datoms:
  ;;   [e :user/email "victor@example.com"    tx false]  ← retract
  ;;   [e :user/email "victor@4coders.com.br" tx true ]  ← assert
  ;; Proof — the history db keeps every 5-tuple ever true:
  (->> (d/q '[:find ?v ?tx ?op
              :in $ ?e
              :where [?e :user/email ?v ?tx ?op]]
            (d/history (db)) victor)
       (sort-by second))
  ;; => (["victor@example.com"     13194139534319 true ]  ← seeded
  ;;     ["victor@4coders.com.br"  13194139534342 true ]  ← assert…
  ;;     ["victor@example.com"     13194139534342 false]) ← …auto-retract, SAME tx
  ;; Nothing was overwritten. The past is immutable; the present is
  ;; just the fold of the log.

  ;; New t now
  (d/basis-t (db))

























  ;; ═════════════════════════════════════════════════ SLIDE 44 ═══
  ;; §6.1 · Surgical writes: retract & CAS
  (goto! 44)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; Explicit retraction (e.g. cardinality-many — surgical forget):
  @(d/transact conn
     [[:db/retract [:user/name "victor"] :user/stars
       (repo (db) "clojure" "clojure")]])   ;; un-star, nothing else

  ;; Optimistic concurrency in one form — compare-and-swap:
  @(d/transact conn
     [[:db/cas victor :user/email
       "victor@4coders.com.br" "v@4coders.com.br"]])
  ;; and a stale CAS fails the WHOLE tx (atomicity!):
  ;; @(d/transact conn [[:db/cas victor :user/email "old@wrong" "x@y"]])
  ;; => ExceptionInfo :db.error/cas-failed

























  ;; ═════════════════════════════════════════════════ SLIDE 45 ═══
  ;; §6.2 · Transactions are entities — annotate them
  (goto! 45)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 6.2 Transactions are entities. Annotate them. ───────────────
  @(d/transact conn
     [{:db/ident :audit/actor  :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}
      {:db/ident :audit/reason :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}
      {:db/ident :user/twitter :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}])

  @(d/transact conn
     [{:db/id "datomic.tx"                   ;; ← the tx's OWN tempid
       :audit/actor  "audit-userd"
       :audit/reason "import batch #42"}
      {:db/id [:user/name "richhickey"] :user/twitter "@richhickey"}])

  ;; Provenance query: what happened to rich, when, who, why?
  ;; Two sources: $hist for the fact's history, $ (present) for the
  ;; tx entity's metadata — multi-db queries are just more inputs.
  (d/q '[:find ?v ?inst ?actor ?why
         :in $ $hist ?e
         :where [$hist ?e :user/twitter ?v ?tx true]
                [?tx :db/txInstant ?inst]
                [(get-else $ ?tx :audit/actor "?") ?actor]
                [(get-else $ ?tx :audit/reason "?") ?why]]
       (db) (d/history (db)) [:user/name "richhickey"])
  ;; => #{["@richhickey" #inst "2026-..." "bruna" "COI import batch #42"]}
  ;; "Three months later, WHY does this row say that?" — answered in
  ;; one query, forever, for free. This alone sells the model.

























  ;; ═════════════════════════════════════════════════ SLIDE 46 ═══
  ;; §6.3 · Time-travel APIs
  (goto! 46)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 6.3 Time-travel APIs ─────────────────────────────────────────
  (d/q '[:find ?email . :in $ ?e :where [?e :user/email ?email]]
       (d/as-of (db) t-before) victor)
  ;; => "victor@example.com"          the db AS IT WAS. Same query!

  (d/q '[:find ?email . :in $ ?e :where [?e :user/email ?email]]
       (db) victor)
  ;; => "v@4coders.com.br"            now

  ;; since = only what's new after t (note: entities may lack their
  ;; identity attrs in a since-db — join it with the present when
  ;; you need names; classic gotcha):
  (count (seq (d/datoms (d/since (db) t-before) :eavt)))
  ;; => small number: just the recent datoms

























  ;; ═════════════════════════════════════════════════ SLIDE 47 ═══
  ;; §6.4 · What-if: a database you can lie to
  (goto! 47)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 6.4 What-if: a database you can lie to ──────────────────────
  (let [{spec :db-after}
        (d/with (db)
                [{:repo/slug "lupii" :repo/owner victor
                  :repo/language #{"Clojure"}}])]
    {:speculative (d/q '[:find (count ?r) . :where [?r :repo/slug _]] spec)
     :real        (d/q '[:find (count ?r) . :where [?r :repo/slug _]] (db))})
  ;; => {:speculative 5, :real 4}
  ;; Transaction + query + rollback, no lock, no cleanup. Test suites,
  ;; dry-run endpoints, "preview this change" UIs — all d/with.

























  ;; ═════════════════════════════════════════════════ SLIDE 48 ═══
  ;; §6.5 · Filtered dbs: permissions as datom predicates
  (goto! 48)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ─── 6.5 Filtered dbs: permissions as datom predicates ───────────
  (let [email-attr (d/entid (db) :user/email)
        public-db  (d/filter (db)
                             (fn [_ dtm] (not= email-attr (:a dtm))))]
    (d/pull public-db '[*] victor))
  ;; => {:db/id ..., :user/name "victor", :user/follows [...]}
  ;;    — no :user/email key AT ALL. Hand `public-db` to any query;
  ;;    the facts simply do not exist from its point of view. No
  ;;    WHERE-clause discipline, no view soup: filter datoms, then
  ;;    "go ahead, the sky's the limit".

  ;; (Mentioning for completeness: true deletion for compliance/GDPR
  ;;  exists too — excision — a special tx that removes datoms and
  ;;  leaves a tombstone acknowledging it did.)
  )

;; ═════════════════════════════════════════════════════════════════════
;; §7 · CLOSING NOTES + EXERCISES                         (slides 49-55)
;; ═════════════════════════════════════════════════════════════════════
;; Architecture in one paragraph: a single TRANSACTOR serializes
;; writes (that's your ACID + total order); STORAGE is pluggable and
;; dumb (Postgres, DynamoDB, Cassandra, dev files, memory — it stores
;; blocks, not semantics); PEERS are your app processes — they hold
;; the indexes' working set in local memory and run queries THERE.
;; Reads scale by adding peers; most queries never cross the wire.
;; Every db value carries its basis-t, so "eventual consistency
;; surprise" is structurally impossible: you always know exactly
;; which point in time you're querying.
;;
;; Same mental model, different trade-offs:
;;   DataScript — this entire model in the browser (tonsky's — you
;;                queried its repo tonight). re-frame app-db as a
;;                triple store; pull/EQL as your wire protocol.
;;   Datahike   — open-source, durable, Datomic-flavored.
;;   XTDB       — bitemporal (valid-time + tx-time) Datalog.
;;
;; Modeling heuristics to leave the room with:
;;   1. Model ATTRIBUTES, not tables. Namespaces group concepts
;;      (:user/*, :repo/*) — they are not containers, just names.
;;   2. Identity: :db.unique/identity for external keys (upsert +
;;      lookup refs). Composite reality? :db/tupleAttrs (§3).
;;   3. Relationships are refs. Direction: pick the natural one —
;;      VAET makes the reverse free (:user/_follows).
;;   4. Ownership (delete-together) is :db/isComponent. Association
;;      is a plain ref. Choose consciously; cascades follow.
;;   5. Enums are idents (§3) — refs, not strings.
;;   6. Absence beats null. Don't transact placeholder values.
;;   7. Annotate transactions (who/why) from day one. Future-you
;;      debugging an import at 2am will send a thank-you note.
;;   8. When NOT this model: unbounded high-churn streams you'll
;;      never audit (mouse moves, tick-by-tick books — Pulsar/Kafka
;;      territory), blob storage, and write volumes beyond a single
;;      transactor's serialization point.

(comment
























  ;; ═════════════════════════════════════════════════ SLIDE 53 ═══
  ;; §7 · Exercises E1–E5 (goto! 53 = full class db)
  (goto! 53)   ;; ⇦ eval if you jumped here — narrates + rebuilds the db

  ;; ── EXERCISES (solutions: honor system — REPL doesn't lie) ───────
  ;; E1  Pull requests: a PR is a repo relationship (from-branch on a
  ;;     fork → to a repo) with a state enum. Design the attributes;
  ;;     transact one PR from victor's fork to tonsky/datascript;
  ;;     write the query "open PRs targeting repos I own".
  ;;
  ;; E2  Teams: orgs have teams, teams have members with a role enum
  ;;     (:role/admin :role/dev). "Which repos can victor push to?"
  ;;     needs a 3-hop traversal — write it with a rule.
  ;;
  ;; E3  Stars-over-time: using d/history + :db/txInstant, produce
  ;;     [[date star-count-delta] ...] for the clojure repo. (Hint:
  ;;     ?op true = +1, false = -1.)
  ;;
  ;; E4  Rectangle detox: take one real table from your last project
  ;;     (nullable columns, a type column, a join table). Remodel as
  ;;     attributes. Count the concepts that disappeared.
  ;;
  ;; E5  DataScript field trip: paste §4.5's recommender into a
  ;;     DataScript REPL (or a re-frame app-db). Note precisely what
  ;;     you had to change. (Spoiler: the schema map shrinks; the
  ;;     query survives intact.)
  )


;; ═════════════════════════════════════════════════════════════════════
;; NAVIGATION — slide anchors, jump-in points, class narration
;; ═════════════════════════════════════════════════════════════════════
;; Every deck slide tagged `REPL §n` has a `SLIDE n` anchor further
;; down (search: SLIDE 24). Under each anchor sits (goto! n): eval it
;; when you TELEPORT somewhere — it announces where you are in the
;; class and rebuilds the db, from scratch and in canonical order, to
;; that slide's exact starting state. Ids in `;; =>` comments
;; reproduce, and every block runs standalone. Plain top-to-bottom
;; evaluation never needs goto!.

(declare issue-schema)          ;; defined in §4.4; replayed below

(def parts
  {1 "PART 1 · The datom — the smallest fact"
   2 "PART 2 · Query — pattern matching all the way down"
   3 "PART 3 · Schema is data"
   4 "PART 4 · One store, seven shapes"
   5 "PART 5 · Time — columns four and five"
   6 "PART 6 · Architecture, heuristics, homework"})

(def slide-notes
  ;; slide → [part title]: what (goto! n) announces.
  {10 [1 "A database in one expression"]
   11 [1 "Attributes first: a little schema"]
   12 [1 "First facts"]
   13 [1 "Look at the raw datoms"]
   14 [1 "Lookup refs: identity that travels"]
   16 [2 "A where-clause is a datom with holes"]
   17 [2 "Four result shapes (and a trap)"]
   18 [2 "Flip what you know; joins for free"]
   19 [2 "Parameterize; call the host mid-query"]
   20 [2 "Absence is information"]
   21 [2 "Refs: the owner interview question"]
   22 [2 "Polymorphism happens at read time"]
   23 [2 "Forks: self-joins & meaningful absence"]
   24 [2 "Cardinality-many & the only metric that matters"]
   25 [2 "The :with trap; negation"]
   27 [3 "Attributes are entities"]
   28 [3 "Growth = accretion; composite identity"]
   29 [3 "Enums are idents"]
   31 [4 "Sort the same datoms four ways"]
   32 [4 "Shape 1/7 · AVET = key-value store"]
   33 [4 "Shape 2/7 · EAVT = relational rows"]
   34 [4 "Shape 3/7 · AEVT = column store"]
   35 [4 "Shape 4/7 · documents — write (nested maps in)"]
   36 [4 "Shape 4/7 · documents — read (pull)"]
   37 [4 "Shape 4/7 · reverse refs, cascades, fulltext"]
   38 [4 "Shape 5/7 · VAET = graph database"]
   39 [4 "The mesh payoff — a recommender in five clauses"]
   40 [4 "Shapes 6 & 7 · event log · sparse matrix"]
   43 [5 "\"Update\" is not a primitive"]
   44 [5 "Surgical writes: retract & CAS"]
   45 [5 "Transactions are entities — annotate them"]
   46 [5 "Time-travel APIs"]
   47 [5 "What-if: a database you can lie to"]
   48 [5 "Permissions as datom predicates"]
   53 [6 "Exercises E1–E5 — full class db, over to you"]})

(def replay
  ;; slide → the WRITES that slide's own block performs. Kept in sync,
  ;; by hand, with the literal forms in the blocks below. (goto! n)
  ;; replays every entry strictly BEFORE n, in order, onto a fresh db —
  ;; that determinism is what makes the `;; =>` ids reproduce.
  [[11 (fn [] @(d/transact conn user-schema))]
   [12 (fn [] @(d/transact conn [{:user/name "richhickey"}
                                 {:user/name "tonsky"}
                                 {:user/name "pithyless"}]))]
   [18 (fn [] @(d/transact conn [{:db/id [:user/name "richhickey"]
                                  :user/email "rich@example.com"}
                                 {:db/id [:user/name "tonsky"]
                                  :user/email "tonsky@example.com"}]))]
   [21 (fn [] ;; NB: the slide block wraps these in (do (goto! 21) …) so
              ;; it's re-runnable at the REPL. Do NOT copy the goto! in
              ;; here — goto! drives this table; a fresh! mid-replay
              ;; would wipe the db it is rebuilding.
              @(d/transact conn repo-schema)
              @(d/transact conn seed-owners)
              @(d/transact conn seed))]
   [28 (fn [] @(d/transact conn [{:db/ident       :repo/topics
                                  :db/valueType   :db.type/string
                                  :db/cardinality :db.cardinality/many}])
              @(d/transact conn [{:db/id (repo (db) "tonsky" "datascript")
                                  :repo/topics #{"database" "datalog"
                                                 "clojurescript"}}]))]
   [29 (fn [] @(d/transact conn [{:db/ident :repo.visibility/public}
                                 {:db/ident :repo.visibility/private}
                                 {:db/ident       :repo/visibility
                                  :db/valueType   :db.type/ref
                                  :db/cardinality :db.cardinality/one}])
              @(d/transact conn [{:db/id (repo (db) "victor" "datascript")
                                  :repo/visibility
                                  :repo.visibility/private}]))]
   [35 (fn [] @(d/transact conn issue-schema)
              @(d/transact conn
                 [{:db/id (repo (db) "tonsky" "datascript")
                   :repo/issues
                   [{:issue/title "Support tuple bindings in :find"
                     :issue/state :issue.state/open
                     :issue/comments
                     [{:comment/body   "PR incoming — same trick as Datomic?"
                       :comment/author [:user/name "pithyless"]}
                      {:comment/body   "Yes. Keep the API surface identical."
                       :comment/author [:user/name "tonsky"]}]}]}]))]
   [40 (fn [] @(d/transact conn [{:db/ident       :user/twitter
                                  :db/valueType   :db.type/string
                                  :db/cardinality :db.cardinality/one}])
              @(d/transact conn [{:db/id [:user/name "tonsky"]
                                  :user/twitter "@tonsky"}]))]
   [43 (fn [] (intern 'domain-modeling.repl 'victor [:user/name "victor"])
              (intern 'domain-modeling.repl 't-before (d/basis-t (db)))
              @(d/transact conn [{:db/id [:user/name "victor"]
                                  :user/email "victor@4coders.com.br"}]))]
   [44 (fn [] @(d/transact conn [[:db/retract [:user/name "victor"]
                                  :user/stars (repo (db) "clojure" "clojure")]])
              @(d/transact conn [[:db/cas [:user/name "victor"] :user/email
                                  "victor@4coders.com.br"
                                  "v@4coders.com.br"]]))]
   [45 (fn [] @(d/transact conn [{:db/ident :audit/actor
                                  :db/valueType :db.type/string
                                  :db/cardinality :db.cardinality/one}
                                 {:db/ident :audit/reason
                                  :db/valueType :db.type/string
                                  :db/cardinality :db.cardinality/one}])
              @(d/transact conn [{:db/id "datomic.tx"
                                  :audit/actor  "bruna"
                                  :audit/reason "COI import batch #42"}
                                 {:db/id [:user/name "richhickey"]
                                  :user/twitter "@richhickey"}]))]])

(defn goto!
  "Teleport to SLIDE n. Announces where we are in the class, then
   rebuilds the in-memory db from scratch by replaying every prior
   slide's writes in canonical order — entity ids and t values in the
   `;; =>` comments reproduce exactly. Idempotent; back-jumps welcome."
  [n]
  (let [[p title] (or (slide-notes n) [nil "(uncharted territory)"])]
    (fresh!)
    (doseq [[at thunk] replay :when (< at n)] (thunk))
    (println)
    (println "════════════════════════════════════════════════════════════")
    (println (str "  ▶ SLIDE " n "  ·  " (get parts p "…")))
    (println (str "  ▶ " title))
    (println (str "  ▶ db rebuilt: " (count (seq (d/datoms (db) :eavt)))
                  " datoms · basis-t " (d/basis-t (db))
                  " · eval forms below, top → bottom"))
    (println "════════════════════════════════════════════════════════════")
    [:slide n :ready]))
