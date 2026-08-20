# Datomic Classes

Companion materials for five classes:

- **One Database, Many Shapes** — a 2-hour, REPL-first class on domain
  modeling with Datomic datoms.
- **Datomic in Production** — four 2-hour, hands-on sessions on Datomic as
  infrastructure: deployment topologies, the read and write paths, storage
  and persistence modes, the physical cache tiers, and admin operations.
  Labs run Datomic Pro against PostgreSQL in Docker; Datomic Cloud and
  Datomic Local are covered by comparison.
- **Datomic Console — Wall to Wall** — a 2-hour, Console-first class that
  walks every surface of the Datomic Console: schema tree, query builder,
  entities, transactions, indexes, as-of/since/history, and data sources.
  Reuses the Production class infra (Postgres + Pro transactor).

- **Datomic at Scale** — a 2-hour class on operating Datomic, plus a
  45-minute exercise after the break: high availability and transactor
  failover, the cache tiers and the cost of a cold read, what
  parallelism means on the write path versus the read path, the
  production settings and what each one affects, and how peers are
  deployed. Continues from *Datomic in Production*.

- **Fulcro RAD × Datomic — Attributes All The Way Down** — a 2-hour,
  full-stack class on a scaffolded Fulcro RAD application: ~10 attribute
  declarations generate the Datomic schema, Pathom resolvers, CRUD forms
  and reports — with REPL probes tracing every request through
  Read → Render → Edit/Add → Mutation → Transaction → DB.

## Files

### One Database, Many Shapes

- `domain-modeling-with-datoms.pptx` - the slide deck.
- `build_deck.py` + `deck_content.py` - the deck's source.
  `python3 build_deck.py` regenerates the pptx.
- `src/domain_modeling/repl.clj` - the live REPL companion. Slide anchors in
  the file match the deck's `REPL §n` slides.
- `src/domain_modeling/time_exercises.clj` - "fill the gaps" exercises for the
  last 30 minutes, built on Part 4 (Time): tx provenance, cell-level blame,
  reverting a transaction, and schema evolution under a full audit. Eval
  `(domain-modeling.time-exercises/start!)` to begin; solutions at the bottom
  of the file.

### Datomic in Production

- `datomic-infrastructure.pptx` - the 4-session slide deck.
- `build_infra_deck.py` + `infra_content.py` + `infra_part1..4.py` - its
  source, one module per session. `python3 build_infra_deck.py` regenerates
  the pptx and fails the build if a slide's content overflows the canvas.
- `infra_layout.py` - the flow-layout DSL the sessions are authored in
  (`BUL`, `ROW`, `CARDS`, `TABLE`, `CODE`, `NOTE`, `TWO`), so slides are
  written as content rather than as EMU coordinates.
- `src/datomic_infra/labs.clj` - the lab companion; sections match the
  deck's sessions and every `◆ lab` slide.
- `infra/` - `docker-compose.yml` (PostgreSQL, plus memcached for session
  3), a transactor properties file, and setup instructions.

### Datomic Console — Wall to Wall

- `datomic-console.pptx` - the 2-hour, Console-first slide deck (55 slides).
- `datomic-console-deck.js` - source; `node datomic-console-deck.js` regenerates
  the pptx (requires `npm install pptxgenjs`).
- `src/datomic_console/labs.clj` - seed + verifier companion. Sections
  (`§0`–`§10`) match the deck; every `◆ CONSOLE` slide has click paths and
  REPL checks here. Run `(datomic-console.labs/seed!)` once before class,
  then spend the hour in the browser.

  ```sh
  docker compose -f infra/docker-compose.yml up -d
  $DATOMIC/bin/transactor infra/pg-transactor.properties
  clj -M:infra:repl
  # (require 'datomic-console.labs) (datomic-console.labs/seed!)
  $DATOMIC/bin/console -p 8080 \
    pg "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"
  ```

  Open http://localhost:8080/browse/ · storage **pg** · DB **store**.
  Chrome recommended. The Console URI has **no** database name — that is
  deliberate.

### Datomic at Scale

- `datomic-at-scale.pptx` - the 2-hour slide deck (42 slides). Every
  diagram is drawn with real shapes, and the explanatory prose lives in
  the speaker notes, one note per slide.
- `datomic-at-scale-deck.js` - its source; `node datomic-at-scale-deck.js`
  regenerates the pptx (requires `npm install pptxgenjs`). Run with
  `DECK_QA=1` to also print a text overflow / collision report.
- `datomic-at-scale-deck.md` - the same deck in markdown (model +
  diagrams; REPL work referenced via ⚑ waypoints rather than
  slide-by-slide).
- `datomic-at-scale-preclass.md` - the one-page brief to send
  participants beforehand: what the class covers, what to install, and
  the single command that warms the dependency cache before they are on
  the room's network.
- `src/datomic_ops/labs.clj` - the lab companion, §0–§6. Labs are
  tagged **[MEM]** (runs on `datomic:mem://`, nothing installed — these
  are machine-verified) or **[PRO]** (needs Postgres, a transactor or
  two, and `$DATOMIC`).
- `src/datomic_ops/exercises.clj` - **The Incident**, the fill-the-gaps
  exercise for after the break (E1–E4, ~45 min): read the log as an
  audit trail, fix a badly shaped writer, parallelise a reader by
  slicing the index, then deploy the change. Eval
  `(datomic-ops.exercises/start!)` to begin; solutions at the bottom of
  the file. Entirely in-memory, so it does not depend on what each
  laptop has installed.
- `infra/HA.md` + `infra/pg-transactor-standby.properties` - the second
  transactor, for the live failover in §2.

  ```sh
  clj -M:repl                    # enough for every [MEM] lab and the drill
  clj -M:infra:repl              # adds the JDBC driver, for the [PRO] labs
  ```

### Fulcro RAD × Datomic — Attributes All The Way Down

- `fulcro-rad-datomic-deck.md` - the 2-hour slide deck, in markdown
  (theory + diagrams; REPL work referenced via ⚑ waypoints rather than
  slide-by-slide, so you can move between deck and REPL loosely). The
  closing part grows the Datomic model of the live database — two
  incremental schema-growth examples, no migration script.
- `rad-app/` — a self-contained Fulcro RAD project (own `deps.edn`,
  `shadow-cljs.edn`); see `rad-app/README.md` to run it. The guided
  REPL companion is `rad-app/src/main/rad_class/labs.clj` (§1–§8).
  Fulcro Inspect preloads are wired into the dev build.
  Uses `datomic:mem://` — no infra needed.

### Shared

- `deck_shell.py` + `deck_builder.py` - pptx packaging for both decks:
  plain text boxes on a Keynote-authored master/layout/theme, so the files
  import cleanly in macOS Keynote (the original Walnut export did not).
- `deps.edn` - Clojure CLI dependencies. The `:infra` alias adds the
  PostgreSQL JDBC driver the peer needs to reach SQL storage.

## Prerequisites

- JDK 11 or newer.
- Clojure CLI tools.

**One Database, Many Shapes** runs entirely in memory with `datomic:mem://`,
so no Datomic transactor, storage service, account, or external database is
required.

**Datomic in Production** is about the infrastructure, so it needs the real
thing: Docker (for PostgreSQL and memcached) and a Datomic Pro distribution
unzipped locally, for `bin/transactor` and `bin/datomic`. Datomic Pro has
been Apache-2.0 licensed and free since 2023 — no account, no license key.
See `infra/README.md`.

**Datomic at Scale** runs its `[MEM]` labs and the whole post-break
drill on `datomic:mem://`, so `clj -M:repl` is enough to follow most of
it. The `[PRO]` labs — valcache, the deploy stampede, and the live
transactor failover — need the Production class's infrastructure plus a
second transactor; see `infra/HA.md`.

**Datomic Console — Wall to Wall** needs the same infra as Production
(Docker Postgres + Pro transactor), plus `bin/console` from the Pro
distribution. Console cannot attach to `datomic:mem://` — it is a separate
process that talks to storage.

## Start The REPL

Clone the repo and start the interactive nREPL:

```sh
git clone https://github.com/4coders-com-br/datomic-model.git
cd datomic-model
clj -M:repl
```

From the REPL, load the companion namespace:

```clojure
(require 'domain-modeling.repl)
```

The namespace creates an in-memory Datomic connection on load. Nothing from the
class blocks runs automatically; the examples live inside `(comment ...)` forms
so you can evaluate them one expression at a time.

## Follow The Deck

Open `domain-modeling-with-datoms.pptx`, then use the `SLIDE n` anchors in
`src/domain_modeling/repl.clj`.

If you jump into the middle of the class, evaluate the matching `goto!` call
under that slide anchor first:

```clojure
(domain-modeling.repl/goto! 24)
```

`goto!` rebuilds the in-memory database from scratch to the exact starting state
for that slide, then prints the current class section and basis-t. For the
exercises (slide 41, end of Part 5):

```clojure
(domain-modeling.repl/goto! 41)
```

Expected result:

```clojure
[:slide 41 :ready]
```

## Reset Manually

To clear the in-memory database at any time:

```clojure
(domain-modeling.repl/fresh!)
```

Then continue top to bottom, or use `goto!` to rebuild the state for a specific
slide.
