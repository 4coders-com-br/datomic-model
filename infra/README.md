# Lab infrastructure

Datomic Pro is a download, not a dependency: get it from
<https://docs.datomic.com/releases-pro.html> and unzip it somewhere.

```sh
export DATOMIC=~/datomic-pro-1.0.7705
```

## 1. Storage

```sh
docker compose -f infra/docker-compose.yml up -d
```

Only Postgres starts — memcached is behind the `cache` profile and is not
needed until session 3. If port 5432 is already taken on your machine, use
`PG_PORT=5440 docker compose -f infra/docker-compose.yml up -d` and change
`sql-url` in the transactor properties (and the `jdbc` string in
`src/datomic_infra/labs.clj`) to match.

Then create the Datomic storage schema, using the scripts that ship in
the distribution — these are the real thing, not a rewrite:

```sh
PGPASSWORD=postgres psql -h localhost -U postgres -f $DATOMIC/bin/sql/postgres-db.sql
PGPASSWORD=postgres psql -h localhost -U postgres -d datomic -f $DATOMIC/bin/sql/postgres-table.sql
PGPASSWORD=postgres psql -h localhost -U postgres -d datomic -f $DATOMIC/bin/sql/postgres-user.sql
```

`postgres-db.sql` creates the `datomic` database, `postgres-table.sql`
creates `datomic_kvs` — the only table there will ever be — and
`postgres-user.sql` creates the `datomic` role with password `datomic`.

## 2. Transactor

```sh
$DATOMIC/bin/transactor infra/pg-transactor.properties
```

It prints two lines — the Java options echo, then a bare `System started`.
That is the whole of a successful start; there is no URI in the banner.

## 3. Peer REPL

```sh
clj -M:infra:repl
```

then `(require 'datomic-infra.labs)`.

## Ports

Ports 5432, 4334 and (session 3) 11211 must be free. If something else
already owns them, change `docker-compose.yml`,
`pg-transactor.properties` and the URIs in `src/datomic_infra/labs.clj`
together.

## Tear down

```sh
docker compose -f infra/docker-compose.yml --profile cache down -v
```
