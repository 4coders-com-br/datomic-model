# Datomic Classes

Companion materials for two classes:

- **One Database, Many Shapes** — a 2-hour, REPL-first class on domain
  modeling with Datomic datoms.
- **Datomic in Production** — four 2-hour, hands-on sessions on Datomic as
  infrastructure: deployment topologies, the read and write paths, storage
  and persistence modes, the physical cache tiers, and admin operations.
  Labs run Datomic Pro against PostgreSQL in Docker; Datomic Cloud and
  Datomic Local are covered by comparison.

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
