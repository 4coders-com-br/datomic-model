# Fulcro RAD × Datomic
## Attributes All The Way Down

**A 2-hour full-stack class.**
One scaffolded app (`rad-app/`), ~10 attribute declarations,
zero hand-written CRUD — and a probe at every stage of the round trip:

```
READ → RENDER → EDIT/ADD → MUTATION → TRANSACTION → DB
```

> **How this deck works.** Slides carry the theory and the pictures.
> REPL work lives in `rad-app/src/main/rad_class/labs.clj` (§1–§7) and
> is referenced from **⚑ waypoint** boxes. The two are deliberately
> *loosely* coupled: go to the REPL when the room wants proof, stay on
> slides when it wants the model. Any waypoint can be taken early, late,
> or twice.

---

## Agenda (2:00)

| Time | Section | Stage of the loop |
|------|---------|-------------------|
| 0:00 | Why attribute-centric? | — |
| 0:10 | The stack, end to end | the map |
| 0:20 | Attributes → schema | the source of truth |
| 0:35 | Read | EQL → Pathom → pull |
| 0:50 | Render | normalized db → React |
| 1:05 | Edit / Add | form state is data |
| 1:20 | Mutation | one save for every form |
| 1:35 | Transaction | delta → tx-data |
| 1:45 | DB | datoms, time, the closed loop |
| 1:55 | Stretch & where to go | — |

Setup (before class): `rad-app/README.md` — shadow watch, server REPL,
browser with console open.

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
over a vector of these maps. That's the whole trick.

---

## Why Datomic is the natural floor

The attribute idea lands softly on Datomic because Datomic is *already*
attribute-centric:

| RAD says | Datomic says |
|----------|--------------|
| `defattr :item/name :string` | `{:db/ident :item/name, :db/valueType :db.type/string}` |
| `ao/identity? true` (uuid) | `:db.unique/identity` |
| `:ref` + `ao/target` | `:db.type/ref` |
| entity = bag of attrs | entity = set of datoms sharing an `e` |

No tables to project onto. The impedance mismatch that ORMs exist to
hide simply isn't there.

---

## This class's whole domain model

```clojure
;; category.cljc                     ;; item.cljc
:category/id       uuid, identity    :item/id        uuid, identity
:category/label    string, required  :item/name      string, required
:category/all-…    resolver          :item/price     decimal
                                     :item/quantity  int
                                     :item/category  ref → :category/id
                                     :item/all-items resolver
```

Ten declarations. From these the app you'll click all class was
generated: schema, resolvers, two forms (with a ref picker), two
reports, create, delete.

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
        ▲                                        ▲                    ▲
   trace_client.cljs                      trace_server.clj      tx-report-queue
   ⇧/⇩ EQL log · render log ·             ::form/delta log      every committed
   dirty-field watcher                                          datom → REPL
```

The probes (bottom row) are ~120 lines total and part of the repo —
this is how we'll *see* each stage instead of trusting the slide.

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
Keep this slide in mind; we end the class back here.

---

# Part III · Attributes → Schema  (0:20)

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

(Native `:db/id` ids are possible — `do/native-id?` — but uuids keep
ids stable across dbs, imports, and the wire. Default to uuids.)

---

## Schema generation at start!

```
 model/all-attributes
        │
        ▼
 datomic/start-databases        (server.clj, at (start!))
        │
        ├── d/create-database "datomic:mem://rad-class"
        ├── automatic schema:  attrs with ao/schema :production
        │      → tx of {:db/ident :item/name, :db/valueType …, …}
        ├── ensure transactor functions (save support)
        └── verify: warn if live schema ≠ attributes
```

Idempotent: run it every boot. Additions transact cleanly; *changes*
follow Datomic's usual growth rules (grow, don't break).

> **⚑ waypoint — labs §1 (second half).** Query the live schema with
> plain `d/q` and match every `:db/…` fact back to an attribute option.
> Good moment to ask the room: *who wrote this schema?*

---

# Part IV · Read  (0:35)

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
                 (generated by the adapter)
  :item/all-items ──► [{:item/id}]           ; your defattr resolver
  {:item/id}      ──► :item/name :item/price
                      :item/quantity {:item/category [:category/id]}
  {:category/id}  ──► :category/label

  query: [{:item/all-items [:item/name {:item/category [:category/label]}]}]

  plan:  all-items ─► item-by-id (pull) ─► category-by-id (pull)
                       └─ one d/pull per entity, batched
```

You wrote **one** resolver (`all-items`, 5 lines). The per-entity ones
were generated from `ao/identities` — that's what the option was for.

---

## Where reads run

```
 browser console                 server REPL
 ───────────────                 ───────────
 ⇧ EQL request → /api            Request:  [{:item/all-items […]}]
   (trace_client middleware)       (RAD parser logging)
                                 Response: {:item/all-items […]}
 ⇩ EQL response ← /api
```

And crucially: `(server/q …)` runs *the browser's parser* from the
REPL. Same plugins, same middleware, same db. No "but it works in
curl" class of bug — there is nothing the browser can ask that you
cannot ask from the REPL.

> **⚑ waypoint — labs §2.** Click Items with both logs visible, then
> re-run the logged query via `server/q`, then grow it with the
> category join. Loose order encouraged — some rooms want `server/q`
> *before* believing the browser log.

---

# Part V · Render  (0:50)

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
                                      + a root edge :item/all-items
                                        [[:item/id #uuid "e8dc…"] …]
```

Every entity exists **once**. Two components showing the Hammer show
*the same* Hammer. Refs are idents — pointers, not copies.

---

## Render = db → tree → React

```
 client db  ──(component query + db->tree)──►  props tree  ──►  React
     ▲                                                            │
     └──────────────── transactions mutate db ◄───────────────────┘
```

- Components declare a query; Fulcro denormalizes just that slice.
- A change to `[:item/id #uuid "e8dc…"]` re-renders exactly the
  components whose queries touch that ident.
- The RAD rendering plugin (semantic-ui) supplies the *look* of forms
  and reports; swapping it doesn't touch the model. Attributes don't
  know they're being drawn.

> **⚑ waypoint — labs §3.** From the CLJS REPL: `(keys (db))`,
> one `(entity [:item/id …])` — see the ident-shaped ref with your own
> eyes — and `log-renders!` while sorting the report. Fulcro Inspect
> shows the same db graphically if the room prefers pictures.

---

# Part VI · Edit / Add  (1:05)

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
keystroke. Undo = copy pristine back. Cancel = throw the copy away.
No dirty flags to forget.

---

## The delta shape (memorize this one)

```clojure
{[:item/id #uuid "e8dc…"]                 ; ident of the edited entity
 {:item/quantity {:before 12 :after 13}}} ; per-field before/after
```

- `:before` travels too — it enables conflict detection server-side.
- A **new** entity uses a Fulcro *tempid* in the ident position, and
  every field is `{:after …}` with no `:before`.
- Multi-entity edits (subforms) are just more idents in the same map.

This shape **is** the wire protocol of save. Nothing else is sent.

> **⚑ waypoint — labs §4.** `watch-edits!` on the open Hammer form,
> then type one digit and watch the delta print per keystroke. The
> best 30 seconds of the class — spend them whenever energy dips.

---

# Part VII · Mutation  (1:20)

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

There is no `save-item`, no `save-category`, no controller per entity.
The delta already says everything; entity-specific behavior lives in
**middleware**, not in per-form endpoints.

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
        │   (your validation / audit /       │
        │    security wrappers go here)      │
        └─────────────────┬──────────────────┘
                          ▼
        result: {:tempids {fulcro-tempid → real uuid}}
```

Middleware is where real apps hang authorization, defaulting, audit
trails. The class tracer is *itself* a demo of the extension point.

> **⚑ waypoint — labs §5.** Save in the browser; read the same delta in
> the server REPL (`@trace/last-delta`). Then the reveal: replay
> `save-form` **from the REPL** with a tempid — new row appears in the
> browser after refresh. The browser is just another parser client.

---

# Part VIII · Transaction  (1:35)

---

## delta → tx-data is a pure function

Between the mutation and storage sits one translation, and you can
hold it in your hand:

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
- Retractions appear when `:after` is nil/removed.

---

## Tempid choreography (new entities)

Three id systems shake hands, in order:

```
  Fulcro tempid            Datomic tempid string        real ids
  (client, optimistic)     (inside one txn)             (durable)
 ┌──────────────────┐     ┌─────────────────────┐     ┌─────────────────┐
 │ #fulcro/tempid   │ ──► │ "019ffc63-3ba3-…"   │ ──► │ :db/id 17592186…│
 │ d5f0…            │     │ + [:db/add tid      │     │ :item/id #uuid  │
 │ (in delta ident) │     │    :item/id #uuid…] │     │   "019ffc63-…"  │
 └──────────────────┘     └─────────────────────┘     └────────┬────────┘
          ▲                                                    │
          └—————— {:tempids {fulcro-tempid → uuid}} ◄——————————┘
                  client db rewrites the ident in place;
                  the optimistic row becomes the real row
```

The user saw the row instantly (optimistic); the remap makes it true.

> **⚑ waypoint — labs §6.** Run `delta->txn` by hand on
> `@trace/last-delta`. If a create happened recently, its tempid story
> is sitting in the atom — use the room's own data, not the slide's.

---

# Part IX · DB  (1:45)

---

## The datom stream you've been watching

All class, every commit printed to the REPL:

```
══ DB › committed datoms ═════════════════════════════════
  [13194139534324 :db/txInstant  #inst "2026-08-13T18:32:59"  13194139534324 true]
  [17592186045424 :item/quantity 13                           13194139534324 true]
  [17592186045424 :item/quantity 12                           13194139534324 false]
```

That's `tx-report-queue`: storage narrating itself. Note the *pair* —
an update is an assertion **plus** a retraction, in one transaction.
Nothing was overwritten.

---

## Accumulate, don't update

```
                      d/history: every datom ever
   ┌────────────────────────────────────────────────────┐
   │ [e :item/quantity 12  tx1 true ]   seeded          │
   │ [e :item/quantity 12  tx2 false]   class edit      │
   │ [e :item/quantity 13  tx2 true ]                   │
   └────────────────────────────────────────────────────┘
        │                     │
   d/as-of db tx1        d/db (now)
   "the report the       "the report the
    browser showed        browser shows
    at 0:15"              now"
```

The audit trail wasn't built. It's what a database *is* when facts
accumulate. (Cross-reference: the domain-modeling class spends two
hours here; today we just close the loop.)

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

Six representations, one identity, and the only code we wrote was:
**ten attributes, one list resolver each, two forms, two reports.**
Everything else was derivation — and every derivation was inspectable
at a REPL.

> **⚑ waypoint — labs §7.** `d/history` of the Hammer's quantity and an
> `as-of` query. Then scroll the REPL back to the first datom stream of
> the day: the whole class is in that scrollback.

---

# Part X · Stretch & Where To Go  (1:55)

---

## Stretch (pick one, ~3 minutes each)

1. **Add an attribute live.** `:item/in-stock? :boolean` in the model +
   one entry in `fo/attributes` → restart → schema, form field, and
   column all exist. Count the files touched: two.
2. **Delete and find the corpse.** Delete a row in the UI; find
   `:added false` in the datom stream and in `d/history`.
3. **Swap storage under a running design.** `:datomic/driver :mem` →
   `:postgresql` (Production-class infra). The model doesn't change;
   that's what `ao/schema` decoupled.

---

## Where to go from here

- **This repo:** `rad-app/src/main/rad_class/labs.clj` — everything
  from today, runnable solo.
- **Fulcro RAD Demo** (fulcrologic/fulcro-rad-demo) — the full-size
  version of this app: sales, invoices, blob attributes, reports with
  parameters.
- **Fulcro Developers Guide** + RAD book — the theory of forms, UISM,
  and dynamic routing we waved at.
- **Datomic classes in this repo** — domain modeling (datoms, time)
  and Datomic in Production (transactor, storage, caches) go deep on
  the right-hand side of today's diagram.

*The thesis, one last time: state each fact once, as data, and make
every layer a derivation you can interrogate at a REPL.*
