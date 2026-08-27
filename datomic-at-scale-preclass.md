# Datomic at Scale — before the class

**2 hours.** REPL-first: you will be measuring things on your own
machine, not watching slides.

## What it covers

How a Datomic system behaves in production — what each component does
when it fails, how transactor failover works, what a read costs at each
cache tier, where parallelism applies on the read and write paths,
which settings matter, and how peers are deployed.

It continues from *Datomic in Production*. If you have not taken that
class you can still follow; Part III moves fastest, since it assumes
the read and write paths are already familiar.

## What to install

JDK 11 or newer and the Clojure CLI. Datomic Pro has been Apache-2.0
licensed since 2023 — no account, no license key, nothing to sign.

```sh
git clone <this repo>
cd Datomic
```

## Run this once before class

It downloads the Datomic peer library and checks that the toolchain
works. **Do it on your own network, not on the room's** — the first
run fetches around 100 MB and takes about a minute.

```sh
clj -M -e "(require '[datomic.api :as d]) (d/create-database \"datomic:mem://check\") (println :datomic-ready)"
```

Expected output:

```
true
:datomic-ready
```

If you see that, you are set for the class. The `[MEM]` labs run on
`datomic:mem://` — no Docker, no transactor, no Datomic distribution
required.

## Optional: the two hands-on infrastructure labs

Three sections have labs marked `[PRO]` — valcache, the cold-peer
deploy measurement, and a live transactor failover. These need real
infrastructure and are demonstrated from the front, so setting them up
is optional.

To follow along on your own machine you need Docker and a Datomic Pro
distribution unzipped locally. Setup is in `infra/README.md` and
`infra/HA.md`; the failover lab needs two transactors, so allow twenty
minutes for it.

## What to bring

A terminal, an editor connected to a REPL if you use one, and a
question about a system you actually operate — the settings sections
are more useful with a real workload to hold them against.

## Files

| | |
|---|---|
| `datomic-at-scale-deck.md` | the deck |
| `src/datomic_ops/labs.clj` | labs §0–§6, referenced from the deck |
| `infra/HA.md` | two-transactor setup, for the `[PRO]` labs |
