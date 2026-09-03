(ns course-project.skeleton
  "FINAL PROJECT SKELETON — copy me, then make me yours.
   ════════════════════════════════════════════════════════════════════
     cp src/course_project/skeleton.clj src/<you>/project.clj
   then: rename the ns, change `uri`, replace every TODO. The brief
   (datomic-final-project.md) has the rubric; the letters below (A–F)
   are its checklist. Section time budgets are a guide for a 12-minute
   demo — cut, don't rush.

   ── Checkpoint one-pager (fill in, bring to next class) ────────────
   Domain      : TODO — one sentence
   Entities    : TODO — boxes & arrows welcome, ASCII is fine
   Invariant   : TODO — the rule your tx function will refuse to break
   My catch    : TODO — the surprise you plan to show (ok to change)
   ────────────────────────────────────────────────────────────────────
   Conventions: eval top to bottom; (fresh!) restarts; ;; => shows
   expected results; failures-on-purpose go through (anomaly ...)."
  (:require [datomic.api :as d]
            [clojure.pprint :refer [pprint]]))

(def uri "datomic:mem://TODO-your-project")           ; ← rename me

(defonce conn
  (do (d/create-database uri)
      (d/connect uri)))

(defn fresh!
  "Nuke and recreate the db — how every rehearsal and the demo start."
  []
  (d/delete-database uri)
  (d/create-database uri)
  (alter-var-root #'conn (constantly (d/connect uri)))
  :ok)

(defn db [] (d/db conn))

(defmacro anomaly
  "Eval body EXPECTING it to throw; return the root cause's message —
   so your failure demos read as data, not stacktraces."
  [& body]
  `(try ~@body
        :unexpected-success!
        (catch Throwable t#
          (let [root# (loop [e# t#]
                        (if-let [c# (.getCause e#)] (recur c#) e#))]
            (.getMessage root#)))))

;; Functions the TRANSACTOR must resolve (attribute/entity predicates,
;; classpath tx fns, custom aggregates) are defined HERE, at top level,
;; referenced by fully qualified symbol — see Advanced §0 for why.

;; (defn my-invariant-pred [db eid] ...)              ; TODO or delete


;; ═════════════════════════════════════════════════════════════════════
;; §1 · MODEL IT                                    [A]  (~2 min demo)
;; ═════════════════════════════════════════════════════════════════════
;; ≥4 entity shapes · refs one AND many · a component · an ident enum ·
;; unique/identity somewhere, unique/value where it belongs.
;; SEED lives here: schema tx, then data tx (remember: lookup refs
;; cannot see entities born in the same tx — tempids for those).

(comment

  ;; TODO schema transact
  ;; TODO seed transact
  ;; TODO one pull that shows the shape (components inline!)
  )


;; ═════════════════════════════════════════════════════════════════════
;; §2 · GUARD IT + COMPOUND IT                      [B C]  (~3 min)
;; ═════════════════════════════════════════════════════════════════════
;; The tx function that owns your invariant — happy path AND refusal
;; (wrap the refusal in `anomaly`). A pred or entity spec. A :db/cas.
;; And the tuple that pays rent: composite uniqueness or a range scan.

(comment

  ;; TODO install + call your tx fn; then (anomaly <the illegal call>)
  ;; TODO validation: :db.attr/preds or :db.entity/attrs + :db/ensure
  ;; TODO :db/cas guarding a state transition
  ;; TODO tuple: lookup by pair, or d/index-range over the composite
  )


;; ═════════════════════════════════════════════════════════════════════
;; §3 · ASK IT                                      [D]  (~3 min)
;; ═════════════════════════════════════════════════════════════════════
;; The query that would hurt in SQL (recursive rule?) · an aggregate
;; with the :with call made out loud · pull-in-query · one second
;; source ($ + :in relation, or db vs its own as-of).

(comment

  ;; TODO rules + recursive query (bind the entry var — [?x] heads!)
  ;; TODO aggregate (say why :with / why not)
  ;; TODO pull inside :find
  ;; TODO two-source join
  )


;; ═════════════════════════════════════════════════════════════════════
;; §4 · REMEMBER IT + EXPLAIN IT                    [E F]  (~3 min)
;; ═════════════════════════════════════════════════════════════════════
;; Audit metadata on a tx ("datomic.tx"), then answer: who changed
;; what, when — and show the db from before. Close with :query-stats
;; on your heaviest query + sixty seconds on life beyond mem://.

(comment

  ;; TODO tx with {:db/id "datomic.tx" ...} audit facts
  ;; TODO provenance query (the ?tx in position 4 is yours to use)
  ;; TODO as-of / history: "before" vs "after"
  ;; TODO (d/query {:query <heaviest> :args [(db)] :query-stats true})
  ;;      → narrate rows-in/rows-out and your clause order
  )


;; ═════════════════════════════════════════════════════════════════════
;; §5 · STRETCH (optional)                          (only if time loves you)
;; ═════════════════════════════════════════════════════════════════════

(comment
  ;; d/with test harness · tx-report-queue consumer · Postgres run ·
  ;; RAD form/report · fulltext · excision walkthrough
  )
