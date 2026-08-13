# Fulcro RAD × Datomic — Attributes All The Way Down

A 2-hour, full-stack class built on one scaffolded [Fulcro
RAD](https://github.com/fulcrologic/fulcro-rad) application backed by
Datomic (in-memory peer). The entire CRUD surface — Datomic schema,
Pathom resolvers, forms with a ref picker, reports with sorting,
form-links, create and delete — is **generated from ~10 attribute
declarations** in `src/main/rad_class/model/`.

The class instrument is a REPL probe at every stage of the round trip:

| Stage           | Where you watch it                                            |
|-----------------|---------------------------------------------------------------|
| **Read**        | browser console (`⇧ EQL request`) + server REPL (`Request:`)  |
| **Render**      | `trace-client/log-renders!` + the normalized client db        |
| **Edit/Add**    | `trace-client/watch-edits!` — the form delta on every keystroke |
| **Mutation**    | `trace-server/wrap-traced-save` — `::form/delta` on the server |
| **Transaction** | `delta->txn` — watch the delta become Datomic tx-data         |
| **DB**          | `tx-report-queue` — every committed datom, streamed to the REPL |

## Run it

```sh
# Terminal 1 — frontend build (first run: npm install)
npm install
npx shadow-cljs watch main

# Terminal 2 — backend REPL
clj -M:dev:repl
user=> (require 'rad-class.server 'rad-class.seed 'rad-class.labs)
user=> (rad-class.server/start!)     ; writes schema, starts :3000
user=> (rad-class.seed/seed!)

# Browser: http://localhost:3000  (keep the devtools console open)
# Install the "Fulcro Inspect" Chrome extension: a Fulcro tab appears in
# devtools with the client DB, transactions, EQL network log, and an
# element picker. The build ships both inspect preloads.
# Optional Terminal 3 — CLJS REPL into the browser:
npx shadow-cljs cljs-repl main
```

The slide deck is `../fulcro-rad-datomic-deck.md` (theory + diagrams,
with ⚑ waypoints into the labs). The REPL companion is
`src/main/rad_class/labs.clj` (§1–§8, timed for 2 hours, everything in
`(comment ...)` blocks); the two are loosely coupled by design. §8 is
the capstone: growing the Datomic model of the live, populated database
(new scalar attribute, then a to-many attribute) with no migration
script — mixed-generation data, `missing?` queries, and `as-of` across
the migration boundary.

## Layout

- `src/main/rad_class/model/` — the attributes. This is the whole app.
- `src/main/rad_class/model.cljc` — the registry (`all-attributes`).
- `src/main/rad_class/ui.cljs` — 2 `defsc-form` + 2 `defsc-report`; no CRUD HTML.
- `src/main/rad_class/server.clj` — Datomic + Pathom + http-kit (~100 lines).
- `src/main/rad_class/seed.clj` — sample rows.
- `src/main/rad_class/trace_server.clj` — mutation/transaction/DB probes.
- `src/main/rad_class/trace_client.cljs` — read/render/edit probes.
- `src/main/rad_class/labs.clj` — the 2-hour guided walkthrough.

The database is `datomic:mem://rad-class` — no infra needed. To run it
against the Production-class Postgres transactor instead, flip
`:datomic/driver` in `rad-class.server/db-config`.
