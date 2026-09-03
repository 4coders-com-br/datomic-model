# The Final Project — Your Domain, Live in the REPL

*Offered after the **Advanced Datomic** class (2026-09-03). Study it
tonight, start it this week, workshop it at the next class, present it
at the last one.*

The course ends the way it ran: in the REPL. You will build **one
namespace around a domain of your own** and drive it live in front of
the class — schema, invariants, queries, time travel, and the story of
*why* you shaped it that way. The file is the deck; there are no
slides.

This is not a toy checklist run. The goal is that one honest artifact
you can keep: proof, to yourself first, that you can take a domain you
care about and model, guard, query and explain it in Datomic.

## Timeline

| When                | What                                                        |
|---------------------|-------------------------------------------------------------|
| Today               | Read this brief. Pick a domain. Copy the skeleton. Seed 3 entities until it feels easy. |
| **Next class**      | **Checkpoint** — bring the one-pager (below) and your file *running*. We workshop blockers together; unclaimed rubric letters get rescued here. |
| **Final class**     | **Demo day** — 12 minutes live per project, 3 for questions. `fresh!`, then top to bottom. |

Bring the checkpoint one-pager on paper or in the file's header
comment: your entity map (boxes and arrows is fine), which checklist
letter each planned section covers, the invariant your transaction
function will guard, and the catch you intend to show.

## Ground rules

- **Solo or pairs.** Pairs demo together; both drive at some point.
- **Your domain, not ours.** Anything you actually know — your job's
  ordering flow, board-game nights, a sourdough log, your climbing
  gym. Not GitHub, not airlines, not the RAD invoices app.
- **One file**: `src/<yourname>/project.clj` in your fork of the
  course repo. `datomic:mem://` — same `clj -M:repl` you have used all
  course. (Running on Pro + Postgres is a stretch goal, not required.)
- **Course conventions**: everything evaluable inside `(comment ...)`
  blocks, seed blocks marked, `;; =>` expected results, expected
  failures wrapped in the `anomaly` macro (it ships in the skeleton).
  `src/datomic_advanced/repl.clj` is the reference for tone and shape.
- **It must run clean**: `(fresh!)` then every form, top to bottom, no
  editing mid-demo. Rehearse once with a timer. Cut content rather
  than rush it — a calm 10 minutes beats a breathless 15.

## The checklist

Six letters, A–F, mapping the six classes onto *your* domain. Every
letter must appear in the demo; how deep you go inside each is your
editorial call.

**A · Model it** *(One Database, Many Shapes)*
At least four entity "shapes"; refs in both cardinalities; one
component; one enum via idents; uniqueness chosen deliberately —
somewhere an `identity` (upsert) and, where it belongs, a `value`.
Narrate one modeling decision you changed your mind about.

**B · Guard it** *(Advanced §2/§4)*
One invariant that lives in a **transaction function** (`:db/fn` or
classpath) — shown succeeding *and* refusing. One schema-level
validation (attribute predicate or entity spec + `:db/ensure`). One
`:db/cas` somewhere it earns its keep.

**C · Compound it** *(Advanced §1)*
At least one tuple that pays rent: composite uniqueness, an ordered
pair, or a composite-key range scan (`d/index-range`) — demonstrated,
not just installed.

**D · Ask it** *(One Database + Advanced §3)*
A recursive rule or another query that would hurt in SQL; one
aggregate where you say out loud why `:with` is or isn't needed; one
pull-inside-query projection; one query that joins a second source
(an `:in` relation, or the db against its own `as-of`).

**E · Remember it** *(Time, across the course)*
The time story: annotate at least one transaction with audit metadata
and answer "who changed what, when — and show me the db from before"
using `history`/`as-of`.

**F · Explain it** *(Datomic in Production + At Scale + Console)*
Pick your heaviest query: show `:query-stats`, narrate the clause
order. Then sixty seconds, plain words, no infra needed: what changes
when this namespace leaves `mem://` — where writes serialize, what
your peer caches, what the indexing job means for your `noHistory`
and excision expectations.

**Stretch menu** *(optional, admired, never required)*: run the same
namespace against the infra class's Postgres + transactor; a `d/with`
test harness for your tx fn; a tx-report-queue consumer doing
something visible; two of your attributes surfaced in a RAD form or
report; a fulltext or excision walkthrough.

## What I will be looking for

1. **It runs.** `fresh!` to final form without hand-edits.
2. **Coverage.** All six letters, visibly.
3. **Judgment.** Schema choices you can defend when poked.
4. **Story.** Two minutes in, we understand your domain and care.
5. **Your own catch.** The best moment of every demo: one thing that
   surprised *you* while building — an error you didn't expect, a
   behavior you had to probe. The course files are full of those;
   demo day earns you yours.

## Starting tonight

1. Pick the domain (ten minutes, gut call, done).
2. `cp src/course_project/skeleton.clj src/<you>/project.clj`, rename
   the ns, change the db uri.
3. Write the schema for your three most obvious entities, transact
   one seed, run one query. Stop while it still feels easy.
4. Skim `src/datomic_advanced/repl.clj` once more — this time reading
   it as *format documentation*.
5. Write the checkpoint one-pager into your file's header.

Stuck between classes? Bring it broken to the checkpoint — that is
what it is for.
