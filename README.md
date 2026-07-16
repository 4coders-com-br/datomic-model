# Datomic Domain Modeling Class

Companion materials for **One Database, Many Shapes**, a REPL-first class on
domain modeling with Datomic datoms.

## Files

- `domain-modeling-with-datoms.pptx` - the slide deck.
- `src/domain_modeling/repl.clj` - the live REPL companion. Slide anchors in
  the file match the deck's `REPL §n` slides.
- `src/domain_modeling/time_exercises.clj` - "fill the gaps" exercises for the
  last 30 minutes, built on Part 4 (Time): tx provenance, cell-level blame,
  reverting a transaction, and schema evolution under a full audit. Eval
  `(domain-modeling.time-exercises/start!)` to begin; solutions at the bottom
  of the file.
- `deps.edn` - Clojure CLI dependencies for Datomic Peer and nREPL.

## Prerequisites

- JDK 11 or newer.
- Clojure CLI tools.

Datomic runs in memory for this class with `datomic:mem://`, so no Datomic
transactor, storage service, account, or external database is required.

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
