# Datomic at Scale
## Operations, Parallelism and the Cost of a Read

**A 2-hour class, plus a 45-minute exercise after the break.**

The class covers how a Datomic system behaves in production: what each
component does when it fails, how failover works, what a read costs at
each cache tier, where parallelism applies, which settings matter, and
how to deploy peers.

> **How this deck works.** Slides carry the model and the diagrams.
> REPL work lives in `src/datomic_ops/labs.clj` (§0–§6), referenced
> from **⚑ waypoint** boxes. The coupling is loose: a waypoint can be
> taken early, late, or skipped.
>
> **What this class assumes.** It continues from *Datomic in
> Production*. The deployment map, write path, read path and
> backup/restore drill are covered there
> (`src/datomic_infra/labs.clj`) and are not repeated here.
>
> **[MEM] and [PRO].** Labs marked **[MEM]** run on `datomic:mem://`
> with nothing installed. Labs marked **[PRO]** need Postgres, one or
> two transactors, and `$DATOMIC` — see `infra/HA.md`. The exercise
> after the break is entirely **[MEM]**, so it does not depend on what
> each laptop has set up.

---

## Agenda (2:00 + exercise)

| Time | Section | Question |
|------|---------|----------|
| 0:00 | The failure map | what breaks, and how? |
| 0:12 | **High availability** | who takes over, and how fast? |
| 0:38 | **The cost of a read** | which tier answered? |
| 1:02 | ☕ **Break (10 min)** | — |
| 1:12 | **Parallelism** | what scales, and what does not? |
| 1:38 | **Settings & signals** | which knob, and what does it affect? |
| 1:52 | **Deployment** | how are peers shipped? |
| 2:00 | → **The Incident** (E1–E4) | ~45 min, continues past the hour |

Setup: `infra/HA.md` for the live §2 lab. Otherwise `clj -M:repl`
covers the `[MEM]` labs and the whole exercise.

---

# Part I · The Failure Map

---

## Four components, four different failures

```
        ┌─────────┐   ┌─────────┐   ┌─────────┐
        │  peer   │   │  peer   │   │  peer   │      query runs HERE
        └────┬────┘   └────┬────┘   └────┬────┘      (your JVM)
             │             │             │
             │      ┌──────┴──────┐      │
             ├──────┤  memcached  ├──────┤           optional, shared
             │      └─────────────┘      │
             ▼             ▼             ▼
        ╔═══════════════════════════════════╗
        ║             STORAGE               ║        source of truth
        ╚═══════════════════════════════════╝        …and the arbiter
                     ▲           ▲
              ┌──────┴───┐   ┌───┴──────┐
              │transactor│   │ standby  │            exactly ONE writes
              │ (active) │   │ (parked) │
              └──────────┘   └──────────┘
```

| It dies | Effect |
|---------|--------|
| **storage** | total outage — reads too, once the caches miss |
| **transactor** | writes fail; reads continue |
| **one peer** | that peer's traffic only; siblings unaffected |
| **memcached** | nothing fails; storage load increases |

Three of the four are not outages. This is the main structural
difference from a single-server SQL deployment, and the rest of the
class follows from it.

> **⚑ waypoint — labs §1 [MEM].** `(d/basis-t (d/db conn))`,
> `(d/q all-readings (d/db conn))`, `(:datoms (d/db-stats (d/db conn)))`
> — a peer's position in time, its data, and its size.

---

## The asymmetry

```
   READS      served by the peer, from caches and storage
              → parallel; independent of the transactor

   WRITES     serialised through ONE process
              → ordered and transactional
```

HA protects the write path. Caching accelerates the read path.
Parallelism means something different on each side.

---

## One clarification before tuning

`d/db-stats` reports the **database**, not the cache:

```clojure
(:datoms (d/db-stats (d/db conn)))   ;; => 20301
```

No peer API reports cache occupancy. Cache behaviour is observed
through metrics (§5) or by timing two identical queries.

---

# Part II · High Availability

---

## Datomic HA is a lease, not a cluster

Two identical transactor processes, same storage, one lease.

```
   transactor A ──┐                        A holds the lease
                  ├──▶  ╔═══════════╗      B waits
   transactor B ──┘     ║  STORAGE  ║
                        ║  (lease)  ║      no quorum
                        ╚═══════════╝      no consensus protocol
                              ▲            no split brain
                              │
                          peers find
                       the active one HERE
```

Storage is the arbiter: the lease lives where the data lives, so
"who is the writer" and "what is committed" cannot disagree. There is
no separate failover controller and no virtual IP.

A standby that is working correctly prints nothing after startup.

---

## What a failover looks like

```
   writes   ──ok──ok──ok──✗──✗──✗──✗──ok──ok──ok──▶
                          └─── the window ───┘
                          A dies      B has the lease

   reads    ──ok──ok──ok──ok─ok─ok─ok──ok──ok──ok──▶
```

The peer reconnects on its own — no URI change, no restart, no load
balancer. The width of the window depends on
`heartbeat-interval-msec`, storage latency, and how warm the standby's
JVM is, so it is measured rather than quoted.

> **⚑ waypoint — labs §2 [PRO].** Start `writer-loop!`, then
> `pkill -f pg-transactor.properties`, then read
> `(failover-report @timeline)`. That number is your write-availability
> SLO.

---

## What a standby covers

| Covers | Does not cover |
|---|---|
| a **bounded write pause** | a second copy of the data |
| unattended recovery | storage loss |
| rolling transactor **upgrades** | a bad transaction |

Data redundancy is storage replication's job. Restore is covered in the
Production class, §4.

The peer-side dial that shapes the pause:

```
datomic.txTimeoutMsec    how long a write waits for a writer to exist
```

A short timeout produces fewer stalled threads and more failed writes;
a long one, the reverse. Tuning it means choosing between those.

---

# Part III · The Cost of a Read

---

## Four tiers

A read walks down until a tier answers:

```
  ┌──────────────────────────────────────────────┬───────────────┐
  │ 1  object cache   on-heap, decoded segments  │   ns … µs     │
  ├──────────────────────────────────────────────┼───────────────┤
  │ 2  valcache       local SSD, per peer        │   ~100s of µs │
  ├──────────────────────────────────────────────┼───────────────┤
  │ 3  memcached      shared, over the network   │   ~1 ms       │
  ├──────────────────────────────────────────────┼───────────────┤
  │ 4  storage        Postgres / DDB / S3        │   ms, metered │
  └──────────────────────────────────────────────┴───────────────┘
```

Tier 2 is the addition in this class. It is also the only tier that
survives a process restart, which matters more than its latency.

---

## The unit of caching is a segment

Not an entity, and not a row:

```clojure
(d/pull db '[*] some-e)                               ;; one entity asked for
(count (seq (d/index-range db :reading/t 4240 4260))) ;; => 20 in the segment
```

Neighbours in the index share a segment. So the working set is measured
in segments, and cache sizing follows the segments a workload touches
rather than the entities it names.

> **⚑ waypoint — labs §3 [MEM].** The segment demo needs no
> infrastructure — it is a property of the index, not of the cache.

---

## Cold peers after a deploy

Twenty peers restart together, with twenty empty object caches, and ask
storage the same questions at the same time.

```
   before deploy      │  after deploy
   ───────────────────┼──────────────────────────────────
   blks_hit  ████████ │  blks_read ████████████████████
   blks_read █        │  blks_hit  ██
                      │  ↑ every peer, cold, simultaneously
```

Three mitigations:

1. **memcached** — one shared miss instead of N *(needs a server)*
2. **valcache** — survives the restart that caused it *(needs a disk)*
3. **rolling deploys** — don't replace 20 peers at once *(needs nothing)*

> **⚑ waypoint — labs §3 [PRO].** Add valcache with two `-D` flags,
> restart the REPL, re-run the cold query. Then set
> `datomic.objectCacheMax=32m` and re-run the warm one. The gap between
> those two measurements is the object-cache sizing signal for this
> dataset.

---

## ☕ Break — 10 minutes

Next: which half of the system scales, and which does not.

---

# Part IV · Parallelism

---

## One writer, many readers

```
   WRITES                        READS
   ──────                        ─────
   one transactor                every peer, every core
   cannot be parallelised        parallel
   can be pipelined              cost storage nothing once warm

   lever: keep the writer        lever: cut the work up along
   from going idle               the index
```

Both are called "parallelism"; they are different mechanisms.

---

## Pipelining, measured on mem

Serial versus pipelined writes on `datomic:mem://`:

```clojure
[(serial! 500000) (pipeline! 600000)]
;; => [24.69 24.31]        ms — no difference
```

`datomic:mem://` runs the transactor inside the same JVM, so there is
no round trip for pipelining to overlap. The measurement isolates the
mechanism: pipelining does not make the transactor faster, it keeps it
from idling between round trips.

Running the same two lines against a transactor over a socket produces
a gap proportional to the round-trip cost — which is why the number is
measured per environment.

Unbounded pipelining trades latency for heap. In production, 8–16
transactions in flight covers most of the benefit.

---

## Parallel reads: slicing the index

`:reading/t` is indexed, so AVET can be sliced. Each slice is an
independent read — no locks, no coordination, no transactor.

```
   d/index-range over 100,000 readings

   ├──────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┤
      8 slices, one immutable db value, pmap across 10 cores

   serial   78.70 ms
   pmap     24.68 ms          ≈ 3.2×     (median of five runs)
```

Two constraints:

- `db` is a **value**, so every slice reads the same immutable
  database. There is no snapshot to hold open and no transaction to
  leak.
- Speed-up is bounded by cache misses rather than cores. On a cold peer
  the parallelism overlaps storage waits.

There is also a floor. Re-cutting the same 100,000 readings:

```
   slices        8      100     1000    10000
   pmap ms   24.68    27.62    30.12    54.41
```

The degradation is gradual, and only at 10,000 slices does the parallel
version approach the 78.70 ms serial baseline.

---

## Parallel what-ifs

`d/with` applies a transaction to a database value. No transactor is
involved, so many can run concurrently:

```clojure
(doall (pmap #(d/q all-readings (:db-after (d/with db %))) scenarios))
;; => ([[105005]] [[105005]] [[105005]])

(d/q all-readings db)
;; => [[105000]]          the base db is unchanged
```

Useful for validation, scenario analysis, and import dry runs.

---

## Where parallelism does not apply

A single `d/q` is single-threaded inside the peer. Parallelism applies
across queries, or across index slices cut by the caller — not within
one query. A slow single query is addressed by its shape or its cache.

> **⚑ waypoint — labs §4 [MEM].** All four experiments run in memory,
> including the one that shows no difference.

---

# Part V · Settings and Signals

---

## The memory-index loop

```
      writes ──▶ ┌─────────────────┐
                 │  memory index   │──▶ durable log (always)
                 └────────┬────────┘
                          │ past memory-index-threshold
                          ▼
                 ┌─────────────────┐
                 │  indexing job   │──▶ storage
                 └────────┬────────┘
                          │ if writes keep outrunning it
                          ▼
                 ┌─────────────────────────────────┐
                 │  memory-index-max reached       │
                 │  → transactor throttles writers │
                 │  → p99 write latency rises      │
                 └─────────────────────────────────┘
```

`threshold` starts the indexing job; `max` starts throttling. The
throttle is intentional back-pressure, not a failure — but it is what
appears on the dashboard, so the loop is worth knowing before tuning.

---

## The settings, and what each affects

**Transactor** — properties file:

| Setting | If | Effect |
|---|---|---|
| `memory-index-threshold` | too high | long, bursty indexing jobs |
| `memory-index-max` | too low | early throttling under load |
| `object-cache-max` | too low | warm reads behave like cold ones |
| `memcached` | unset | each peer misses separately |
| `heartbeat-interval-msec` | too high | longer §2 failover window |

**Peer** — `-D` flags:

| Flag | Effect |
|---|---|
| `datomic.objectCacheMax` | the peer's own heap cache |
| `datomic.memcachedServers` | join the shared tier |
| `datomic.valcachePath` | the SSD tier |
| `datomic.valcacheMaxGb` | how much of that disk to use |
| `datomic.txTimeoutMsec` | how long a write waits for a writer |

Note: passing any JVM flag to `bin/transactor` makes it drop its own GC
defaults. Re-specify `-XX:+UseG1GC -XX:MaxGCPauseMillis=50` when
passing anything.

---

## Read the defaults from the distribution

```sh
ls $DATOMIC/config/samples/
grep -vE '^\s*(#|$)' $DATOMIC/config/samples/sql-transactor-template.properties
grep -rn "valcache" $DATOMIC/bin $DATOMIC/config
```

Defaults change between releases. Where the distribution and this deck
disagree, the distribution is current.

---

## Two instruments already available

**`d/sync-index`** returns a db whose *index* — not only its log —
includes a given `t`. `d/sync` waits on the log alone; the difference
appears under load.

**The tx-report queue** lets any peer observe transactions as they
land:

```clojure
:t 1001 :datoms 401
:t 1102 :datoms 401     ;; 100 readings × 4 attrs + 1 txInstant
```

Used for audit, cache invalidation and CDC; in class, as a throughput
monitor.

**Signals to alert on**, mapping onto the loop above:

- alarms of any kind
- indexing job duration — the drain is falling behind
- transaction latency p99 — throttling
- storage read/write time — the bottleneck is storage
- memcached hit ratio — the shared tier is not being shared

> **⚑ waypoint — labs §5.** The memory-index demo is [MEM]; the
> metrics-callback contract is read from the distribution.

---

# Part VI · Deployment

---

## A peer carries a cache

The cache is the difference between a 10 ms read and a 1,000 ms read,
so a readiness probe that succeeds at process start reports a peer that
is up but cold.

```clojure
(defn warm! [conn]
  (let [db (d/db conn)]
    (doall (pmap (fn [[lo hi]] (count (seq (d/index-range db :reading/t lo hi))))
                 (partition 2 1 (range 0 20001 2500))))
    {:ready? true :basis-t (d/basis-t db)}))
```

Warming the segments the service reads moves that cost ahead of the
first request.

> **⚑ waypoint — labs §6 [MEM].** Run `warm!`, then the `d/sync`
> read-your-writes check on the next slide.

---

## Reading your own writes

A fresh peer starts at whatever `t` storage gives it. A request
arriving just after a write through a *different* peer can be served a
database that does not include it.

```clojure
(d/basis-t (deref (d/sync conn t) 5000 nil))
;; => 21011      the t that was written
```

Passing the `t` with the request is the consistency contract of a
multi-peer deployment. For the index rather than the log, `d/sync-index`.

---

## Rollout order

```
   1. ship the SCHEMA          additive → old peers ignore what's new
   2. roll peers in WAVES      avoids the cold-cache burst
   3. WARM, then report ready
   4. roll the TRANSACTOR      §2's failover, intentionally
```

Step 1 differs from SQL: Datomic schema is additive, so an old peer
ignores attributes it does not know. There is no lock, no `ALTER`, and
no migration window, which is what makes the rolling order safe.

Step 4 is a rolling transactor upgrade: start the new-version standby,
stop the old active, and spend one bounded write pause. From a peer, an
upgrade and an outage are indistinguishable.

---

## Summary

| | |
|---|---|
| **Fails** | storage totally · transactor for writes · peers locally |
| **Waits** | writers, during failover and during throttling |
| **Scales** | reads: peers × cores · writes: by pipelining |
| **Costs** | storage, whenever a cache is cold |

The settings in Part V are lookups; this table is the model they
operate on.

---

# → The Incident

**`src/datomic_ops/exercises.clj` · ~45 minutes · continues past the hour**

```clojure
clj -M:repl
(require 'datomic-ops.exercises)
(datomic-ops.exercises/start!)
;; => [:incident :ready :basis-t 61013]
```

The readings service was healthy at 08:00 and unusable at 09:10. The
database and the REPL are the available evidence.

| | | |
|---|---|---|
| **E1** | ~8 min | **Read the evidence** — the log as an audit trail |
| **E2** | ~10 min | **Fix the writer** — transaction shape |
| **E3** | ~12 min | **Fix the reader** — slice the index across cores |
| **E4** | ~15 min | **Ship the fix** — warm, sync, rollout order |

Fill-the-gaps (`___`), solutions at the bottom of the file. Everything
runs on `datomic:mem://`, so no Docker, transactor or `$DATOMIC` is
needed. Where an exercise checks a duration it checks a ratio or a
boolean rather than a millisecond count.

---

## Where to go next

- `src/datomic_infra/labs.clj` — *Datomic in Production*: the paths and
  storage layers this class assumed.
- `infra/HA.md` — the two-transactor setup, if §2 was not run live.
- Datomic Cloud handles §2 and §6 differently: **query groups** are the
  read scaling of §4 as an autoscaling group, and failover is managed
  by the platform. Same model, different operational surface.
- The failover drill is worth repeating on your own staging
  environment, since the window depends on that infrastructure.
