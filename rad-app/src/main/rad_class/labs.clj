(ns rad-class.labs
  "FULCRO RAD × DATOMIC — ATTRIBUTES ALL THE WAY DOWN
   ════════════════════════════════════════════════════════════════════
   Companion for a 2-hour, full-stack class: one scaffolded Fulcro RAD
   application whose entire CRUD surface (Datomic schema, Pathom
   resolvers, forms, reports) is generated from ~10 attribute
   declarations — and a REPL probe at every stage of the round trip:

     READ → RENDER → EDIT/ADD → MUTATION → TRANSACTION → DB

   ── Before class (10 min, from rad-app/) ───────────────────────────
   Terminal 1 — frontend:   npm install && npx shadow-cljs watch main
   Terminal 2 — backend:    clj -M:dev:repl
     user=> (require 'rad-class.server 'rad-class.seed 'rad-class.labs)
     user=> (rad-class.server/start!)
     user=> (rad-class.seed/seed!)
   Browser — http://localhost:3000 with the devtools console OPEN.
   Optional Terminal 3 — CLJS REPL into the browser:
     npx shadow-cljs cljs-repl main        ; or nREPL on port 9001

   ── Conventions ────────────────────────────────────────────────────
   * Everything below lives in (comment ...): evaluate form by form.
   * `;; =>` shapes come from one rehearsal; ids and t values WILL
     differ, the shapes won't.
   * CLJS snippets are marked ;; CLJS — eval them in the shadow REPL
     (or paste into the browser console via Fulcro Inspect)."
  (:require
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    [com.fulcrologic.rad.database-adapters.datomic-common :as common]
    [datomic.api :as d]
    [rad-class.model :as model]
    [rad-class.server :as server]
    [rad-class.trace-server :as trace]))

;; ═════════════════════════════════════════════════════════════════════
;; §1 · 0:00–0:15 · ATTRIBUTES ARE THE APP
;; ═════════════════════════════════════════════════════════════════════
;; Open src/main/rad_class/model/item.cljc side by side with the running
;; app. Six defattr forms. No schema edn, no resolvers, no form HTML.

(comment
  ;; The registry the whole app is generated from:
  (mapv ::attr/qualified-key model/all-attributes)
  ;; => [:category/id :category/label :category/all-categories
  ;;     :item/id :item/name :item/price :item/quantity :item/category
  ;;     :item/all-items]

  ;; One attribute is just a map — show the class there is no magic:
  (pprint (first model/all-attributes))

  ;; RAD wrote the DATOMIC SCHEMA from those attributes at start!.
  ;; Prove it — this is a plain Datomic query against the live db:
  (let [db (d/db (server/connection))]
    (->> (d/q '[:find [(pull ?a [:db/ident :db/valueType :db/cardinality
                                 :db/unique]) ...]
                :where [?a :db/ident ?i]
                       [(namespace ?i) ?ns]
                       [(contains? #{"item" "category"} ?ns)]]
           db)
      (sort-by :db/ident)
      pprint))
  ;; => :item/id is :db.type/uuid + :db.unique/identity  (from ao/identity?)
  ;;    :item/category is :db.type/ref                   (from :ref + ao/target)
  )

;; ═════════════════════════════════════════════════════════════════════
;; §2 · 0:15–0:35 · READ — EQL in, tree out
;; ═════════════════════════════════════════════════════════════════════
;; In the browser click Items. Two places light up:
;;   * browser console:  "⇧ EQL request → /api"  (trace-client)
;;   * this REPL:        "Request: [...]"        (RAD parser logging)
;; The report did not call an endpoint — it sent a QUERY.

(comment
  ;; Run the EXACT same query through the EXACT same parser the browser
  ;; hits. There is no browser-only API:
  (server/q [{:item/all-items [:item/name :item/price :item/quantity]}])

  ;; EQL composes — ask for the join; Pathom chains two generated
  ;; resolvers (all-items → item-by-id) and one Datomic pull each:
  (pprint
    (server/q [{:item/all-items
                [:item/name {:item/category [:category/label]}]}]))

  ;; Where did those resolvers come from? Count them:
  (count (flatten [(datomic/generate-resolvers model/all-attributes :production)]))
  ;; => one resolver per identity + your attribute-declared ones
  )

;; ═════════════════════════════════════════════════════════════════════
;; §3 · 0:35–0:50 · RENDER — normalized db → React
;; ═════════════════════════════════════════════════════════════════════
;; The response was NORMALIZED into the client db (tables keyed by
;; ident), then rendered by the semantic-ui plugin. All CLJS:

(comment
  ;; CLJS — the client database is one big map of tables:
  ;; (keys (rad-class.trace-client/db))
  ;; => (... :item/id :category/id ...)

  ;; CLJS — one normalized entity; note the ref is an IDENT, not a map:
  ;; (rad-class.trace-client/entity
  ;;   [:item/id #uuid "PASTE-AN-ID-FROM-THE-SERVER-LOG"])
  ;; => {:item/name "Hammer" ... :item/category [:category/id #uuid "..."]}

  ;; CLJS — watch React work; then click around and read the console:
  ;; (rad-class.trace-client/log-renders!)
  ;; (rad-class.trace-client/quiet-renders!)
  )

;; ═════════════════════════════════════════════════════════════════════
;; §4 · 0:50–1:05 · EDIT/ADD — form state is data
;; ═════════════════════════════════════════════════════════════════════
;; Click an item name to open the generated form. Editing never mutates
;; the entity in place: RAD keeps a PRISTINE copy + your edits, and the
;; diff is computed, not recorded.

(comment
  ;; CLJS — print the minimal diff live on every keystroke:
  ;; (rad-class.trace-client/watch-edits!
  ;;   rad-class.ui/ItemForm [:item/id #uuid "PASTE-ID"])
  ;; ...type in the Quantity field, watch:
  ;; ✎ dirty {[:item/id #uuid "..."] {:item/quantity {:before 12, :after 13}}}
  ;; (rad-class.trace-client/unwatch-edits!)

  ;; That diff shape is ::form/delta — it IS the wire protocol of save.
  )

;; ═════════════════════════════════════════════════════════════════════
;; §5 · 1:05–1:25 · MUTATION — save is one generic mutation
;; ═════════════════════════════════════════════════════════════════════
;; Press Save in the browser. The console shows the outgoing mutation,
;; and THIS REPL prints (via rad-class.trace-server/wrap-traced-save):
;;
;;   ══ MUTATION › ::form/delta from the browser ══════════════
;;   {[:item/id #uuid "..."] {:item/quantity {:before 12, :after 13}}}
;;
;; Every form in the app saves through this ONE mutation.

(comment
  ;; The last delta that came over the wire is captured for you:
  (pprint @trace/last-delta)
  (pprint @trace/last-result)

  ;; Replay the whole save path WITHOUT a browser — a new item, from
  ;; the REPL, through the same parser/middleware/adapter:
  (let [tid (tempid/tempid)]
    (server/q
      [{(list 'com.fulcrologic.rad.form/save-form
          {::form/id        tid
           ::form/master-pk :item/id
           ::form/delta     {[:item/id tid]
                             {:item/id       {:after tid}
                              :item/name     {:after "Wrench"}
                              :item/price    {:after 24.00M}
                              :item/quantity {:after 5}}}})
        [:tempids :item/id :item/name]}]))
  ;; => tempid remapped to a real uuid; refresh the browser report.
  )

;; ═════════════════════════════════════════════════════════════════════
;; §6 · 1:25–1:45 · TRANSACTION — delta → tx-data
;; ═════════════════════════════════════════════════════════════════════
;; Between the mutation and the storage sits ONE pure translation:
;; the form delta becomes Datomic tx-data. Run it yourself:

(comment
  (let [conn  (server/connection)
        env   (merge (datomic/mock-resolver-env :production conn)
                {::attr/key->attribute model/key->attribute})
        delta @trace/last-delta]
    (pprint (common/delta->txn env :production delta)))
  ;; => {:tempid->string {...}
  ;;     :tempid->generated-id {...}
  ;;     :txn [[:db/add [:item/id #uuid "..."] :item/quantity 13] ...]}
  ;;
  ;; Points to land:
  ;;  * :before/:after pairs → optimistic-concurrency txn (compare style)
  ;;  * refs arrive as idents, become lookup refs / entity ids
  ;;  * a NEW entity's Fulcro tempid becomes a Datomic tempid string
  )

;; ═════════════════════════════════════════════════════════════════════
;; §7 · 1:45–2:00 · DB — datoms, time, and the closed loop
;; ═════════════════════════════════════════════════════════════════════
;; This REPL has been printing "══ DB › committed datoms ══" the whole
;; class — that is the tx-report-queue: storage telling us what became
;; true. Close the loop with Datomic's time model:

(comment
  ;; Full audit trail of one item the class edited — every value the
  ;; quantity EVER had, with transaction times:
  (let [conn (server/connection)
        db   (d/db conn)
        eid  (d/q '[:find ?e . :where [?e :item/name "Hammer"]] db)]
    (->> (d/q '[:find ?v ?inst ?added
                :in $ ?e
                :where [?e :item/quantity ?v ?tx ?added]
                       [?tx :db/txInstant ?inst]]
           (d/history db) eid)
      (sort-by second)
      pprint))
  ;; => ([12 #inst "..." true] [12 #inst "..." false] [13 #inst "..." true])

  ;; And as-of: the report the browser WOULD have shown 5 minutes ago.
  (let [conn (server/connection)
        t    (d/basis-t (d/db conn))]         ; grab a basis early, use later
    (d/q '[:find ?n ?q :where [?e :item/name ?n] [?e :item/quantity ?q]]
      (d/as-of (d/db conn) (- t 5))))

  ;; Wrap-up talking track: the SAME uuid appeared in
  ;;   the React props → the client db ident → the EQL join →
  ;;   the form delta → the :db/add → the datom on disk.
  ;; One identity, six representations, zero glue code written by us.
  )

;; ═════════════════════════════════════════════════════════════════════
;; STRETCH (if time remains)
;; ═════════════════════════════════════════════════════════════════════
(comment
  ;; 1. Add :item/in-stock? :boolean to the model + ItemForm, restart —
  ;;    schema, resolver, form field and column all appear. ~3 minutes.
  ;; 2. Delete a row in the UI; find the retraction in the datom stream
  ;;    (:added false) and in (d/history ...).
  ;; 3. Swap :datomic/driver :mem → :postgresql in server/db-config and
  ;;    point it at the Production-class infra. Nothing else changes.
  )
