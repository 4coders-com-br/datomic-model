# Fulcro RAD × Datomic
## Attributes All The Way Down — and a Model That Grows

**A 2-hour full-stack class.**
One scaffolded app (`rad-app/`), ~10 attribute declarations,
zero hand-written CRUD — a probe at every stage of the round trip:

```
READ → RENDER → EDIT/ADD → MUTATION → TRANSACTION → DB
```

…and then the payoff: **evolving the model of the live, populated
database** — twice — with no migration script, no downtime, and no
rewritten rows.

> **How this deck works.** Slides carry the theory and the pictures.
> REPL work lives in `rad-app/src/main/rad_class/labs.clj` (§1–§8) and
> is referenced from **⚑ waypoint** boxes. The two are deliberately
> *loosely* coupled: go to the REPL when the room wants proof, stay on
> slides when it wants the model. Any waypoint can be taken early, late,
> or twice.

---

## Agenda (2:00)

| Time | Section | Stage |
|------|---------|-------|
| 0:00 | Why attribute-centric? | — |
| 0:08 | The stack, end to end | the map |
| 0:15 | Attributes → schema | the source of truth |
| 0:28 | Read | EQL → Pathom → pull |
| 0:40 | Render | normalized db → React (+ Inspect) |
| 0:52 | Edit / Add | form state is data |
| 1:02 | Mutation | one save for every form |
| 1:12 | Transaction | delta → tx-data |
| 1:22 | DB | datoms, time |
| 1:32 | **Growing the model** | **incremental schema evolution** |
| 1:52 | Stretch & where to go | — |

Setup (before class): `rad-app/README.md` — shadow watch, server REPL,
browser with console open, **Fulcro Inspect extension installed**.

---

# Part I · The Idea

---

## The CRUD tax

Every information system re-states the same facts, once per layer:

```
   SQL DDL          "an item has a name (string, required)"
   ORM model        "an item has a name (string, required)"
   API endpoint     "an item has a name (string, required)"
   Form component   "an item has a name (string, required)"
   Table component  "an item has a name (string, required)"
   Validation       "an item has a name (string, required)"
```

Six copies. They drift. The bug reports live in the drift.

And the day the model changes? Six edits **plus a migration** — the
seventh copy, the one that can destroy data.

---

## The RAD inversion

State the fact **once**, as data, and *derive* the layers:

```
                       ┌─────────────────────┐
                       │  (defattr item-name │
                       │    :item/name       │
                       │    :string          │
                       │    {ao/required? …})│
                       └──────────┬──────────┘
          ┌──────────────┬────────┼─────────┬──────────────┐
          ▼              ▼        ▼         ▼              ▼
   Datomic schema   Pathom     form       report      validation
   :db/valueType    resolver   field      column      required?
   :db.type/string  output     (text      (sortable)  (blocks save)
                               input)
```

An attribute is a **map**. The framework is a set of interpreters
over a vector of these maps.

Corollary we'll cash in at 1:32: *changing the model = editing the
vector.* The derivations — schema included — follow.

---

## Why Datomic is the natural floor

The attribute idea lands softly on Datomic because Datomic is *already*
attribute-centric:

| RAD says | Datomic says |
|----------|--------------|
| `defattr :item/name :string` | `{:db/ident :item/name, :db/valueType :db.type/string}` |
| `ao/identity? true` (uuid) | `:db.unique/identity` |
| `:ref` + `ao/target` | `:db.type/ref` |
| `ao/cardinality :many` | `:db.cardinality/many` |
| entity = bag of attrs | entity = set of datoms sharing an `e` |

No tables to project onto — and, decisive for evolution: **no rows to
alter**. A "column" is just datoms that happen to share an attribute.
Adding an attribute touches nothing that exists.

---

## This class's domain model — v1

```clojure
;; category.cljc                     ;; item.cljc
:category/id       uuid, identity    :item/id        uuid, identity
:category/label    string, required  :item/name      string, required
:category/all-…    resolver          :item/price     decimal
                                     :item/quantity  int
                                     :item/category  ref → :category/id
                                     :item/all-items resolver
```

Ten declarations, and — deliberately — **not the final model**. At 1:32
we outgrow it, live, with data in the database.

> **⚑ waypoint — labs §1.** Open `model/item.cljc` next to the running
> app; eval `(mapv ::attr/qualified-key model/all-attributes)` and
> `(pprint (first model/all-attributes))` — *an attribute is just a map*.

---

# Part II · The Map

---

## The stack, end to end

```
 BROWSER                                SERVER                     STORAGE
┌───────────────────────────┐  EQL/   ┌───────────────────────┐  ┌─────────┐
│ React (semantic-ui)       │ transit │ Ring + http-kit       │  │ Datomic │
│   ▲ render                │  over   │   /api                │  │  :mem   │
│ Fulcro                    │  HTTP   │ Pathom parser         │  │ (peer,  │
│   normalized client db    │ ──────► │   resolvers ──────────┼─►│  in-    │
│   form-state (pristine+Δ) │ ◄────── │   save/delete         │  │ process)│
│   tx queue / remotes      │         │   middleware ─────────┼─►│         │
└───────────────────────────┘         └───────────────────────┘  └─────────┘
        ▲            ▲                           ▲                    ▲
 trace_client   Fulcro Inspect            trace_server.clj      tx-report-queue
 ⇧/⇩ EQL log ·  (devtools tab:            ::form/delta log      every committed
 render log ·   DB · Transactions ·                             datom → REPL
 dirty watcher  Network · picker)
```

Two kinds of probes, same facts: the REPL/console stream (ours, ~120
lines) and **Fulcro Inspect** (the graphical twin, a Chrome devtools
tab). Use whichever the room reads faster.

---

## One request, six representations

The same fact — "the Hammer's quantity" — as it will appear today:

```
1  React props        {:item/quantity 12}
2  client db          [:item/id #uuid "e8dc…"] → {:item/quantity 12}
3  EQL on the wire    [{[:item/id #uuid "e8dc…"] [:item/quantity …]}]
4  form delta         {:item/quantity {:before 12 :after 13}}
5  tx-data            [:db/add [:item/id #uuid "e8dc…"] :item/quantity 13]
6  datom on disk      [17592186045424 :item/quantity 13 13194139534324 true]
```

Same identity (`#uuid "e8dc…"`), zero glue code written by us.
Keep this slide in mind; we end Part IX back here.

---

# Part III · Attributes → Schema  (0:15)

---

## Anatomy of a `defattr`

```clojure
(defattr item-name           ;  a var — attrs are first-class values
  :item/name                 ;  the qualified key: THE identity everywhere
  :string                    ;  RAD type → maps to a :db/valueType
  {ao/identities #{:item/id} ;  "reachable from an :item/id entity"
   ao/schema     :production ;  which Datomic db it belongs to
   ao/required?  true})      ;  UI validation + form gating
```

Three option families to keep apart:

- **storage** — `ao/identity?`, `ao/schema`, adapter options
- **graph** — `ao/identities`, `ao/target`, `ao/pc-resolve`
- **UI** — `ao/required?`, field styles, labels

One map, three audiences.

---

## Identity: the uuid pattern

```
        ao/identity? true                :db.unique/identity
   :item/id  ───────────────►  Datomic  :db.type/uuid
       │                                     │
       │  Fulcro ident                       │  lookup ref
       ▼                                     ▼
  [:item/id #uuid "e8dc…"]      [:item/id #uuid "e8dc…"]
   (client db table key)         (usable as :db/id in tx-data!)
```

The *same literal vector* is a client-db ident and a Datomic lookup
ref. This pun is load-bearing: it's why deltas can travel unchanged.

---

## Schema generation at start! — read this slide twice

```
 model/all-attributes  ──►  datomic/start-databases     (every boot)
                                  │
                                  ├── d/create-database   (idempotent)
                                  ├── ensure-schema!:
                                  │     transact what the LIVE db
                                  │     is MISSING vs the attributes
                                  └── verify: warn on divergence
```

`ensure-schema!` is a **diff, not a dump**. Boot with an unchanged
model → empty diff → no-op. Boot with a grown model → the diff *is
the migration*. This single property is what Part X exploits.

> **⚑ waypoint — labs §1 (second half).** Query the live schema with
> plain `d/q` and match every `:db/…` fact back to an attribute option.
> Ask the room: *who wrote this schema?*

---

# Part IV · Read  (0:28)

---

## EQL: queries, not endpoints

The report does not call `GET /items`. It sends **data**:

```clojure
[{:item/all-items [:item/name :item/price :item/quantity]}]
```

EQL properties compose like joins:

```clojure
[{:item/all-items [:item/name {:item/category [:category/label]}]}]
```

- The **client** owns the shape (each component declares its query).
- The **server** owns the reachability (resolvers).
- There is exactly one endpoint, `/api`, and it is boring.

---

## Pathom: the resolver graph

A resolver is a typed edge: *given these keys, I can produce those.*

```
  :item/all-items ──► [{:item/id}]           ; your defattr resolver
  {:item/id}      ──► :item/name :item/price
                      :item/quantity {:item/category [:category/id]}
  {:category/id}  ──► :category/label

  query: [{:item/all-items [:item/name {:item/category [:category/label]}]}]

  plan:  all-items ─► item-by-id (pull) ─► category-by-id (pull)
```

You wrote **one** resolver (5 lines). The per-entity ones were
generated from `ao/identities`. File away for Part X: *regenerated on
every start! — so a grown model means grown resolvers, for free.*

---

## Where reads run

```
 browser console            server REPL              Fulcro Inspect
 ───────────────            ───────────              ──────────────
 ⇧ EQL request → /api       Request:  [{…}]          Network tab:
 ⇩ EQL response ← /api      Response: {…}            request + response
                                                     + timings, clickable
```

And crucially: `(server/q …)` runs *the browser's parser* from the
REPL. There is nothing the browser can ask that you cannot ask from
the REPL.

> **⚑ waypoint — labs §2.** Click Items with the logs visible, re-run
> the logged query via `server/q`, grow it with the category join.
> Inspect's Network tab shows the same exchange for the skeptics.

---

# Part V · Render  (0:40)

---

## The client database is normalized

The response tree is torn into **tables keyed by ident** on arrival:

```
 response (tree)                      client db (tables)
 ───────────────                      ──────────────────
 {:item/all-items                     :item/id
   [{:item/id #uuid "e8dc…"            {#uuid "e8dc…" {:item/name "Hammer"
     :item/name "Hammer"                               :item/quantity 12
     :item/category                                    :item/category
       {:category/id #uuid "4535…"  ═►                   [:category/id #uuid "4535…"]}}
        :category/label "Tools"}}     :category/id
    …]}                                {#uuid "4535…" {:category/label "Tools"}}
```

Every entity exists **once**. Refs are idents — pointers, not copies.

---

## Render = db → tree → React

```
 client db  ──(component query + db->tree)──►  props tree  ──►  React
     ▲                                                            │
     └──────────────── transactions mutate db ◄───────────────────┘
```

- A change to one ident re-renders exactly the components whose
  queries touch it.
- The rendering plugin (semantic-ui) supplies the *look*; attributes
  don't know they're being drawn.

**Fulcro Inspect is this slide, live**: the DB tab shows the tables,
the DB Explorer follows idents click by click, the element picker maps
any DOM node back to its component and props.

> **⚑ waypoint — labs §3.** REPL: `(keys (db))`, one `(entity …)`,
> `log-renders!` while sorting the report. Then the same three facts in
> Inspect — let the room pick its favorite lens.

---

# Part VI · Edit / Add  (0:52)

---

## Form state: pristine + edits, diff computed

Opening a form does **not** start mutating the entity. RAD keeps:

```
 ┌─ form-state config for [:item/id #uuid "e8dc…"] ─────────────┐
 │ pristine: {:item/quantity 12, :item/name "Hammer", …}        │
 │ fields:   #{:item/name :item/price :item/quantity :item/category}
 └──────────────────────────────────────────────────────────────┘
            current entity: {:item/quantity 13, …}   ← you typed

 dirty-fields = diff(pristine, current)
             = {[:item/id #uuid "e8dc…"]
                {:item/quantity {:before 12, :after 13}}}
```

The diff is **derived on demand**, not recorded keystroke by
keystroke. Undo = copy pristine back. No dirty flags to forget.

---

## The delta shape (memorize this one)

```clojure
{[:item/id #uuid "e8dc…"]                 ; ident of the edited entity
 {:item/quantity {:before 12 :after 13}}} ; per-field before/after
```

- `:before` travels too — it enables conflict detection server-side.
- A **new** entity uses a Fulcro *tempid* in the ident position.
- Multi-entity edits are just more idents in the same map.

This shape **is** the wire protocol of save. Nothing else is sent.

> **⚑ waypoint — labs §4.** `watch-edits!` on the open Hammer form,
> type one digit, watch the delta print per keystroke. The best 30
> seconds of the class — spend them whenever energy dips.

---

# Part VII · Mutation  (1:02)

---

## One mutation to save them all

Pressing Save on *any* RAD form submits the same mutation:

```clojure
(com.fulcrologic.rad.form/save-form
  {::form/id        #uuid "e8dc…"
   ::form/master-pk :item/id
   ::form/delta     {[:item/id #uuid "e8dc…"]
                     {:item/quantity {:before 12 :after 13}}}})
```

No `save-item`, no `save-category`, no controller per entity. The
delta already says everything; entity-specific behavior lives in
**middleware**, not per-form endpoints.

---

## The save middleware sandwich

```
            pathom env (has the delta in ::form/params)
                          │
        ┌─────────────────▼──────────────────┐
        │ trace/wrap-traced-save             │  ← ours: print + capture Δ
        │   ┌────────────────────────────┐   │
        │   │ datomic/wrap-datomic-save  │   │  ← adapter: Δ → txn → transact
        │   └────────────────────────────┘   │
        │   (validation / audit / security   │
        │    wrappers go here)               │
        └─────────────────┬──────────────────┘
                          ▼
        result: {:tempids {fulcro-tempid → real uuid}}
```

> **⚑ waypoint — labs §5.** Save in the browser; read the delta in the
> server REPL (`@trace/last-delta`). Then the reveal: replay
> `save-form` **from the REPL** with a tempid — the browser is just
> another parser client.

---

# Part VIII · Transaction  (1:12)

---

## delta → tx-data is a pure function

```
 {[:item/id #uuid "e8dc…"]                (common/delta->txn env
  {:item/quantity                            :production delta)
   {:before 12 :after 13}}}    ─────►     {:txn [[:db/add
                                                  [:item/id #uuid "e8dc…"]
                                                  :item/quantity 13]]
                                           :tempid->generated-id {…}}
```

- The ident becomes a **lookup ref** — the uuid pun paying off.
- `:before` values can compile to compare-and-set style guards.
- Retractions appear when a value is removed.

---

## Tempid choreography (new entities)

```
  Fulcro tempid            Datomic tempid string        real ids
  (client, optimistic)     (inside one txn)             (durable)
 ┌──────────────────┐     ┌─────────────────────┐     ┌─────────────────┐
 │ #fulcro/tempid   │ ──► │ "019ffc63-3ba3-…"   │ ──► │ :db/id 17592186…│
 │ d5f0…            │     │ + [:db/add tid      │     │ :item/id #uuid  │
 └──────────────────┘     │    :item/id #uuid…] │     │   "019ffc63-…"  │
          ▲               └─────────────────────┘     └────────┬────────┘
          └—————— {:tempids {fulcro-tempid → uuid}} ◄——————————┘
```

The user saw the row instantly (optimistic); the remap makes it true.

> **⚑ waypoint — labs §6.** Run `delta->txn` by hand on
> `@trace/last-delta` — use the room's own data, not the slide's.

---

# Part IX · DB  (1:22)

---

## The datom stream you've been watching

All class, every commit printed to the REPL:

```
══ DB › committed datoms ═════════════════════════════════
  [13194139534324 :db/txInstant  #inst "2026-08-13T18:32:59"  13194139534324 true]
  [17592186045424 :item/quantity 13                           13194139534324 true]
  [17592186045424 :item/quantity 12                           13194139534324 false]
```

An update is an assertion **plus** a retraction, in one transaction.
Nothing was overwritten. Facts *accumulate* — hold that thought for
three more slides.

---

## The loop, closed

```
 React props   client db ident   EQL join    form delta    :db/add       datom
 {:quantity    [:item/id         [{[:item/id  {[:item/id    [:db/add      [17592186…
   13}          #uuid "e8dc…"]     #uuid…]…}]  #uuid…] …}    [:item/id     :item/quantity
                                                              #uuid…]…]    13 …]
     └───────────────┴───────────────┴────────────┴─────────────┴────────────┘
                          the same #uuid "e8dc…" at every hop
```

Six representations, one identity, zero glue code.

> **⚑ waypoint — labs §7.** `d/history` of the Hammer's quantity and an
> `as-of` query. Keep `t-v1` from the labs handy — Part X needs a
> pre-growth basis to time-travel back to.

---

# Part X · Growing the Model  (1:32)

---

## The migration you didn't write

Setup so far: a live app, a populated database, a v1 model. Now the
domain changes — twice:

```
  v1                          v2                          v3
  :item/id                    + :item/in-stock?           + :item/tags
  :item/name                    (:boolean)                  (:string,
  :item/price                                               cardinality :many)
  :item/quantity
  :item/category
```

Plan: edit the attribute registry, reload, `start!` again — **same
JVM, same database, data in place**. No script, no downtime, no
ALTER, no backfill. Then interrogate what happened.

---

## Why this is safe: grow, don't break

Datomic's schema rules, the honest version:

| Change | Cost |
|--------|------|
| new attribute | a transaction — that's all |
| new entity type | *nothing* (entities are not declared) |
| cardinality one → many | alteration, allowed live |
| add docs / index | alteration, allowed live |
| rename | alter `:db/ident` — the **old name keeps resolving** |
| change value type | **not allowed** — new attr + deprecate old |
| add uniqueness | allowed only if existing data already conforms |

The forbidden row is the point: nothing you do to the schema can
rewrite or invalidate an existing datom. History stays readable
under the schema it was written with.

---

## Growth 1: a new scalar — the entire diff

```clojure
;; model/item.cljc — ADD:
(defattr in-stock? :item/in-stock? :boolean
  {ao/identities #{:item/id}
   ao/schema     :production})
;; …and add in-stock? to the attributes vector.

;; ui.cljs (optional, for the UI): one entry in fo/attributes,
;; one in ro/columns. shadow watch hot-reloads the form.
```

Then, in the running REPL:

```clojure
(require 'rad-class.model.item :reload)
(require 'rad-class.model :reload)
(server/stop!) (server/start!)
```

`ensure-schema!` diffs attributes vs live schema → transacts exactly
the missing piece. And because schema is data, **the datom stream
prints the migration itself**:

```
══ DB › committed datoms ═════════════════════════════════
  [72 :db/ident       :item/in-stock?  …  true]
  [72 :db/valueType   :db.type/boolean …  true]
  [72 :db/cardinality :db.cardinality/one … true]
```

---

## Absence, not NULL

What happened to the four existing items? **Nothing.** That's the
feature:

```
              :item/name   :item/quantity   :item/in-stock?
 Hammer        "Hammer"     12               true      ← got v2 data
 Screwdriver   "Screwdr…"   30                         ← no datom. Not
 Garden hose   "Garden…"     7                           null — ABSENT.
 M6 bolt       "M6 bolt…"   84
```

- `pull` / EQL: the key simply isn't in the map. Clients degrade
  gracefully — the report shows a blank cell, not a crash.
- The "not yet migrated" generation is a **query**, not a table scan:

```clojure
[:find [?n ...]
 :where [?e :item/name ?n]
        [(missing? $ ?e :item/in-stock?)]]
```

Backfill if the domain needs it, when it needs it — lazily, per
entity, as ordinary transactions. Or never.

---

## Growth 2: a to-many attribute

```clojure
(defattr tags :item/tags :string
  {ao/identities #{:item/id}
   ao/cardinality :many          ; ← the only new idea
   ao/schema      :production})
```

Same reload + `start!` dance. In a row-oriented world this is a join
table, a foreign key, and a repository method. Here:

```
  [e :item/tags "steel"      tx true]     one entity,
  [e :item/tags "hand-tool"  tx true]     one attribute,
                                          several datoms.
```

No join table *because there were never tables* — cardinality is a
property of the attribute, not a shape of storage.

---

## Time-travel across the migration boundary

`t-v1` was captured before the growth. Then:

```clojure
(d/q '[:find ?a . :where [?a :db/ident :item/in-stock?]]
  (d/as-of db t-v1))
;; => nil        the attribute DID NOT EXIST — not empty: nonexistent

(d/pull (d/db conn) [:item/name :item/tags] hammer)
;; => {:item/name "Hammer", :item/tags ["hand-tool" "steel"]}
```

The past is queryable **under its own schema**; the present under the
grown one; both from the same connection. An `as-of` report in the UI
would render the v1 world with v1's vocabulary — correctly.

---

## The RAD angle: the registry is the migration plan

```
        edit model/item.cljc            (the ONLY human step)
                 │
     ┌───────────┼──────────────┬───────────────┐
     ▼           ▼              ▼               ▼
 ensure-schema!  generate-   form fields     report columns
 transacts the   resolvers   (hot-reload     (hot-reload
 diff            regrow      via shadow)     via shadow)
```

What RAD will **not** do — by design: retract schema, change value
types, delete attributes. Growth is automated; anything destructive
stays a deliberate, human, plain-Datomic act.

> **⚑ waypoint — labs §8.** The whole part, hands-on: both growths,
> the mixed-generation queries, the `as-of` check. ~15 minutes. This
> is the section the class is named after — protect the time for it.

---

# Part XI · Stretch & Where To Go  (1:52)

---

## Stretch (pick one)

1. **Growth at entity scale.** New file `model/supplier.cljc` (id +
   name), `:item/supplier` ref, a picker on ItemForm — the §8 moves,
   one size up. Existing data again untouched.
2. **Delete and find the corpse.** Delete a row in the UI; find
   `:added false` in the datom stream and in `d/history`.
3. **Swap storage under a running design.** `:datomic/driver :mem` →
   `:postgresql` (Production-class infra). The model doesn't change;
   that's what `ao/schema` decoupled.

---

## Where to go from here

- **This repo:** `rad-app/src/main/rad_class/labs.clj` — everything
  from today, runnable solo (§8 = the evolution lab).
- **Fulcro Inspect** — keep the devtools tab open in your own
  projects; it's the fastest answer to "what does the client think?"
- **Fulcro RAD Demo** (fulcrologic/fulcro-rad-demo) — the full-size
  version of this app.
- **Datomic classes in this repo** — domain modeling (datoms, time)
  and Datomic in Production (transactor, storage, caches).

*The thesis, one last time: state each fact once, as data — and then
the model can grow the way domains actually grow: incrementally,
live, without ever rewriting what was already true.*
