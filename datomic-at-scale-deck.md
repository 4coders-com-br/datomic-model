# Datomic at Scale
## Reads, Writes, Parallelism and Caches

**A 2-hour class.**

The class covers how a Datomic system performs in production, in four
topics: reads, writes, parallelism and caches. Each topic follows the
same shape — **how it works** (the basics), **an easy example**, and
**the main catches** you will actually hit.

> **How this deck works.** Everything is on the slides. Each section
> starts from the basics, uses small examples, and keeps to the broad
> performance picture — the goal is the right mental model, not a
> tuning reference.
>
> **What this class assumes.** It continues from *Datomic in
> Production*: what a peer, a transactor and storage are. Everything
> else is (re)explained here.
>
> **Going deeper.** `infra/HA.md` has the two-transactor setup, and
> the Production class (`src/datomic_infra/labs.clj`) has the write
> path, read path and backup drill in detail.

---

## Agenda (2:00)

| Time | Section | One-line version |
|------|---------|------------------|
| 0:00 | The map | three parts, and only one of them is fatal |
| 0:12 | **Reads** | run in your process; cost depends on where the data is |
| 0:40 | **Writes** | one door; batch, and know the two ways it can wait |
| 1:02 | ☕ **Break (10 min)** | — |
| 1:12 | **Parallelism** | reads scale out; writes never do |
| 1:38 | **Caches** | keep reads near the top of the ladder |
| 1:55 | Summary | fails · waits · scales · costs |

---

# The Map

---

## Three moving parts

```
        ┌─────────┐   ┌─────────┐   ┌─────────┐
        │  peer   │   │  peer   │   │  peer   │     queries run HERE
        └────┬────┘   └────┬────┘   └────┬────┘     (inside your app)
             │             │             │
             ▼             ▼             ▼
        ╔═══════════════════════════════════╗
        ║             STORAGE               ║       keeps the data
        ╚═══════════════════════════════════╝       (Postgres, DynamoDB…)
                          ▲
                    ┌─────┴─────┐
                    │transactor │                   ALL writes go here
                    └───────────┘                   (one process)
```

- The **peer** is a library inside your application. Queries run
  there, on data the peer pulls close.
- The **transactor** is one process that all writes go through, in
  order.
- **Storage** just keeps bytes. It is the only place the data lives.

---

## What happens when each one dies

| It dies | Effect |
|---------|--------|
| **storage** | everything stops — this is the real outage |
| **transactor** | writes stop; **reads keep working** |
| **one peer** | only that peer's traffic; the others don't notice |

Only one of the three failures is fatal. Reads and writes live and
die separately — that is the single idea the rest of the class builds
on.

---

# Part I · Reads

*A query runs in your process, on data pulled close.*

---

## How a read works

1. Your code asks a query.
2. The peer checks: **is the data already here?**
   - **Yes** → answer immediately, from memory.
   - **No** → fetch it from storage (milliseconds), **keep it**, answer.

The same query, twice:

```clojure
(time (d/q readings-q db))   ;; 1st run: ~120 ms  — fetching from storage
(time (d/q readings-q db))   ;; 2nd run:   ~2 ms  — data is already local
```

A read costs whatever *fetching* costs. Once the data is local,
reads are nearly free — and they never bother the transactor.

---

## The broad performance picture

**Warm vs cold is the only distinction that matters.**

```
   warm peer   ▍            ~ms        data already local
   cold peer   ██████████   ~100×     everything fetched from storage
```

Same query, same data, same code. The difference is only *where the
data is when the query runs*. Most read-performance work in Datomic
is really cache work (Part IV).

---

## The catches of reads

1. **A restarted peer forgets everything.** Its first requests are
   slow — it is re-fetching its world. (Why deploys hurt: Part IV.)
2. **A new peer may not see your newest write.** Peers learn about
   writes independently. If you write through one peer and read
   through another, pass the `t` along and wait for it:

   ```clojure
   @(d/sync conn t)   ;; a db guaranteed to include t
   ```
3. **One query runs on one core.** A slow query is not fixed by more
   CPUs — only by its shape, or by warmer data. (Part III.)

---

# Part II · Writes

*Everything goes through one door.*

---

## How a write works

```
   peer ──┐
   peer ──┼──▶  ONE transactor ──▶ storage
   peer ──┘     (in order, one
                 at a time)
```

Every write goes through the single transactor, which applies
transactions **in order**. That is what gives you ACID transactions
with no locks and no conflicts to resolve.

The flip side: **write capacity is fixed.** You cannot add write
machines. What you *can* do is use the one door well.

---

## Using the one door well: batch

```clojure
;; slow — 1,000 trips through the door
(doseq [r rows]
  @(d/transact conn [r]))

;; fast — 10 trips carrying 100 each
(doseq [batch (partition-all 100 rows)]
  @(d/transact conn batch))
```

Most write throughput is batching. The second lever: don't wait for
each acknowledgement before sending the next batch (keep a few in
flight — "pipelining"). Batching first; it is 90% of the win.

---

## Catch 1 · the writer can pause

Run **two** transactors: one active, one standby. If the active one
dies, the standby takes over automatically.

```
   writes   ──ok──ok──✗──✗──✗──ok──ok──▶     a short pause (seconds)
   reads    ──ok──ok──ok──ok──ok──ok──ok─▶   never interrupted
```

- Peers reconnect by themselves — nothing to fail over manually.
- During the pause, writes fail or wait; **reads don't notice.**
- A standby is **not a backup** — both write to the same storage.
  Data safety comes from storage replication and backups.

---

## Catch 2 · the writer can push back

Behind the scenes the transactor also *indexes* — it periodically
reorganizes recent writes into storage. If writes arrive faster than
indexing can keep up, the transactor **deliberately slows writers
down** (back-pressure).

What you see: write latency rises, **no errors anywhere**.

What it means: not a failure — the system protecting itself. Ask why
indexing is slow (usually: storage is slow) before touching knobs.

---

# ☕ Break — 10 minutes

---

# Part III · Parallelism

*Reads scale out. Writes never do.*

---

## One word, two sides

```
   WRITES                        READS
   ──────                        ─────
   one process, serial           every peer, every core
   cannot be parallelized        parallel by default
   lever: batch + keep busy      lever: split the work up
```

Reads parallelize freely because a Datomic `db` is an **immutable
value** — nothing can change under a reader, so there is nothing to
lock or coordinate.

---

## Easy example · split a big read

100,000 readings, cut into 8 slices along the index, `pmap` across
cores — each slice is an independent read:

```
   serial     78.7 ms
   8 slices   24.7 ms      ≈ 3×
```

Rules of thumb:

- Slice roughly to the number of cores. Hundreds of tiny slices just
  add overhead.
- Every slice reads the *same* db value — no snapshots, no locks,
  nothing to clean up.

---

## Easy example · try before you commit

`d/with` applies a transaction to a db **value** — no transactor, no
durability, just "what would the db look like if…":

```clojure
(d/with db proposed-tx)          ;; => a new value; the real db unchanged
(pmap #(check (d/with db %)) scenarios)   ;; many at once, safely
```

Use it to validate a big import before spending transactor time on
it, or to compare what-if scenarios in parallel.

---

## The catches of parallelism

1. **One `d/q` uses one core.** There is no setting that changes
   this. Parallelism is always *across* queries or slices you cut.
2. **A second transactor adds zero write throughput.** It is a
   standby for failover (Part II), not a second worker.
3. **On a cold peer, the speedup is fake-ish.** You are overlapping
   fetches, not dividing CPU work. Still useful — but the real fix
   is a warm cache.

---

# Part IV · Caches

*Where the read cost actually goes.*

---

## The ladder

A read walks down until a tier answers:

```
  ┌──────────────────────────────────────────────┬──────────────┐
  │ 1  object cache   in the peer's memory       │  ~instant    │
  ├──────────────────────────────────────────────┼──────────────┤
  │ 2  valcache       local SSD (survives        │  fast        │
  │                   restarts!)                 │              │
  ├──────────────────────────────────────────────┼──────────────┤
  │ 3  memcached      shared between peers       │  ~1 ms       │
  ├──────────────────────────────────────────────┼──────────────┤
  │ 4  storage        the database itself        │  slow, $     │
  └──────────────────────────────────────────────┴──────────────┘
```

1 comes for free. 2 and 3 are optional add-ons. Your whole job:
keep everyday reads answering from as high up as possible.

Nothing ever needs invalidating — Datomic data is immutable, so a
cached block is correct forever. That is why stacking four caches is
safe with no coordination at all.

---

## The unit is a block, not a row

When you ask for one entity, the peer fetches the whole index
**segment** it lives in — thousands of neighboring datoms come along
free.

Consequence: **data read together is cheap if it lives together** in
an index. Scanning a range of readings by time = a few segments.
Fetching 1,000 scattered entities = up to 1,000 segments. Same
answer size, ~100× the fetching.

---

## Catch 1 · deploys empty the caches

Restart all 20 peers at once → 20 empty caches → everyone fetches
the same things from storage at the same moment. Latency spikes,
zero errors, resolves itself in minutes — and often gets misread as
a bad release and rolled back (which restarts everything again).

Three fixes, cheapest first:

1. **Rolling deploys** — replace a few peers at a time *(needs nothing)*
2. **memcached** — 20 cold peers cause 1 shared miss, not 20 *(needs a server)*
3. **valcache** — the SSD cache survives the restart *(needs a disk)*

---

## Catch 2 & 3 · small but common

- **memcached dying breaks nothing** — everything just gets slower
  as storage takes the traffic. Treat it as a degradation, not an
  incident.
- **You can't ask the cache anything.** No API reports what is
  cached. To see cache behaviour: time the same query twice, or
  watch storage-read metrics.

---

## Summary

| | |
|---|---|
| **Fails** | only storage is fatal; transactor = writes pause; a peer = local |
| **Waits** | writers — during failover, and during back-pressure |
| **Scales** | reads: more peers, more cores · writes: only batching |
| **Costs** | storage, whenever a cache is cold |

Any performance symptom lands on one of these four rows.

---

## Where to go next

- `src/datomic_infra/labs.clj` — *Datomic in Production*: the write
  path, read path and backup drill in full detail.
- `infra/HA.md` — the two-transactor setup, to run the failover
  yourself.
- Datomic Cloud: same model; the platform manages failover, and
  "query groups" are Part III's read scaling as an autoscaling group.
