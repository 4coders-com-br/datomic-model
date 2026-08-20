# Two transactors, one database

Setup for §2 of **Datomic at Scale** (`src/datomic_ops/labs.clj`). It
builds on the Production class infrastructure — Postgres in Docker, a
Datomic Pro distribution on disk — and adds a second transactor.

```sh
export DATOMIC=~/datomic-pro-1.0.7705
```

## 1. Storage, as usual

```sh
docker compose -f infra/docker-compose.yml up -d
```

If this is a fresh machine, create the Datomic storage schema first —
see `infra/README.md` §1. Nothing about HA changes that step: **one
storage, one `datomic_kvs` table, two transactors.**

## 2. The active transactor

Terminal A:

```sh
$DATOMIC/bin/transactor infra/pg-transactor.properties
```

Wait for `System started`.

## 3. The standby

Terminal B:

```sh
$DATOMIC/bin/transactor infra/pg-transactor-standby.properties
```

It does not print `System started`. It has found the lease held in
storage and is waiting for it to go stale. A standby that is working
correctly prints nothing after startup.

The two properties files differ in one thing that matters: `port`
(4334 vs 4335), because both run on the same machine. `sql-url` is
identical, which is what makes them one logical transactor rather than
two databases.

## 4. The peer

Terminal C:

```sh
clj -M:infra:repl
```

then `(require 'datomic-ops.labs)` and work through §2.

The peer's URI contains no mention of a second transactor, a virtual
IP, or a load balancer: peers discover the active transactor through
storage. Since the lease and the data live in the same place, there is
no split brain to reason about.

## 5. The failover

From §2's lab: start `writer-loop!`, then kill the active transactor
rudely.

```sh
pkill -f 'pg-transactor.properties'
```

`pkill -f` matches the full command line, so that pattern hits the
active and **not** the standby (whose command line contains
`pg-transactor-standby.properties`). Check before you fire:

```sh
pgrep -fl transactor
```

Terminal B takes the lease and prints its own `System started`. Back in
the REPL, `(failover-report @timeline)` reports the window. That number
is the write-availability SLO for this hardware.

## 6. Recovery

Restart the killed process; it comes back as the new standby. There is
no failback, no promotion step, and nothing to reconfigure — the roles
are decided by who holds the lease, not by config.

```sh
$DATOMIC/bin/transactor infra/pg-transactor.properties
```

## Ports

4334 (active), 4335 (standby), 5432 (Postgres), 11211 (memcached, if
used). If those ports are already in use — likely on a machine that has
run the Production class — change `docker-compose.yml`, **both**
properties files, and the URIs in `src/datomic_ops/labs.clj` together.

## Tear down

```sh
pkill -f transactor
docker compose -f infra/docker-compose.yml --profile cache down -v
```
