# Oathbound

**Diegetic, mechanically-binding agreements for Paper servers.**

Oathbound brings contract law to your Minecraft kingdom. Players draft, negotiate, and seal binding
Oaths — with real escrow, real reputation consequences, and real territory on the line — all through
in-world objects (contract books, notaries, altars) instead of bare slash commands. It's built for
fantasy-kingdom roleplay servers that want diplomacy, betrayal, and consequences to actually mean
something.

Fully self-contained: **no Vault, no LuckPerms, no external claim plugin.** Oathbound owns its own
currency, permissions, and territory systems, backed by an embedded SQLite database — nothing extra
to stand up.

> **Status:** early development. The core Oath engine, ownership/permission model, altar placement
> detection, and a chest-GUI open trade contract flow are implemented and tested. Everything else in
> the [Roadmap](#roadmap) is still on the way — see that section for exactly what's live today.

---

## Table of Contents

- [Features](#features)
- [How It Works](#how-it-works)
- [Commands](#commands)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Roadmap](#roadmap)
- [Development](#development)

---

## Features

- **Oaths** — binding agreements between players with a real lifecycle: `DRAFT → PROPOSED → SEALED →
  ACTIVE → FULFILLED / BROKEN / VOIDED`. Every transition is validated and written to an append-only
  ledger.
- **Clauses & conditions** — oaths are built from composable clauses (transfer ownership, escrow
  payment, custom RP flags, kill counts) gated by composable conditions (time elapsed, death count,
  payment received, vote tally, manual confirmation, and `AND`/`OR`/`NOT` combinations of any of the
  above).
- **ProtectionGroups** — the single ownership/permission primitive behind everything: chests, doors,
  claims, companies, towns, kingdoms. Ownership can be a player *or* another group, so a territory can
  belong to "the ruling house" and survive a change in who leads that house without ever touching the
  territory's own record. Ownership-chain resolution is cycle-safe and depth-limited by design.
- **Native economy** — plugin-owned, multi-currency player balances. No economy plugin required.
- **Territory altars** — consecrate an altar by stacking a barrel, a capstone block (obsidian by
  default, configurable), and a candle. Completing the structure creates the altar at zero Power;
  claim radius is a live, diminishing-returns function of Power and scales with your group's tier
  (Individual → Company → Town → Region → Kingdom).
- **Chest-GUI open trade contracts** — post an item-for-item barter offer with no named counterparty
  through an in-world chest interface; anyone can browse the open contract board and fulfill it. The
  first player to accept becomes the oath's second party and the swap happens automatically, with
  delivery held safely if either side is offline at the moment of completion.
- **Everything persists** — SQLite-backed via a pluggable storage adapter, with an in-memory cache for
  fast permission checks and async writes so gameplay never blocks on disk I/O.

See [`oathbound-master-plan.md`](./oathbound-master-plan.md) for the full design doc, including
systems not built yet (Blood Oaths, the NPC Notary, the Public Oath Board, elections, bounty
contracts, and the altar sacrifice/decay ritual).

---

## How It Works

### Oaths

An Oath has two or more parties, an optional list of witnesses, and a list of clauses. It starts life
as a private `DRAFT`, gets `PROPOSED` to the other party, becomes `SEALED` once accepted, then `ACTIVE`
while its conditions are tracked. It resolves into exactly one of `FULFILLED`, `BROKEN`, or `VOIDED` —
and stays there; terminal states have no further transitions. Every transition is recorded in the
**Ledger**, an append-only log that's the single source of truth for oath history (and, eventually,
the Public Oath Board).

An Oath can also be **open**: instead of naming a counterparty up front, it starts with just its
creator and sits `PROPOSED` with an empty second slot. Whoever accepts it first fills that slot and
the oath is carried straight through `SEALED` into `ACTIVE` in one step — same lifecycle, same Ledger,
just decided by whoever claims it rather than a named party.

### Trade contracts (open contracts, in practice)

The chest-GUI contract builder (`/oathbound-trade`) is the first thing built on top of open oaths: an
item-for-item barter. The creator deposits items into a chest GUI and posts it; that creates an open
Oath plus a `TradeOffer` holding the deposited items. Anyone can then browse `/oathbound-trade board`,
open a listed contract, deposit their own items, and confirm — which fills the oath's second party,
carries it to `FULFILLED`, and swaps the goods: the creator receives what the fulfiller deposited, and
vice versa. If either side is offline the instant the swap happens, their items are held against the
`TradeOffer` and delivered automatically the next time they log in, so nothing is lost. The creator can
also cancel their own unclaimed listing from the board to get their items back.

This is deliberately a narrow, self-contained feature rather than the general-purpose Escrow system
described in the master plan (§4) — it doesn't use the generic `EscrowClause`/release-schedule model,
and there's no claim/expiry/abandonment handling. Real Escrow is still on the [Roadmap](#roadmap).

### ProtectionGroups

Every claim, faction, company, or kingdom is a `ProtectionGroup`. Groups have members with roles
(each role carries a rank weight and a permission set — invite, vote, open containers, transfer
ownership, accept deals on the group's behalf, etc.), and an `owner`, which is either a specific player
or *another group*. Resolving who ultimately owns something walks that chain live, with a fixed depth
cutoff — and any ownership change that would make a group its own ancestor is rejected at write time,
before it's ever persisted.

### Economy

A native, per-player, multi-currency balance system. No Vault, no external plugin — Oathbound is the
economy.

### Altars

An altar starts as three blocks stacked directly on top of each other: a **barrel**, a **capstone
block** (obsidian by default — configurable), and a **candle**. Placing the candle is the trigger:
Oathbound checks what's directly below it, and if the structure is right, the altar is consecrated on
the spot, owned by whoever placed the candle, at **zero Power**. Its claim radius is computed live from
current Power — at zero Power, that's zero radius. Charging Power via a sacrifice ritual, decay over
time, vulnerability tiers, and desecration are designed (see the master plan, §10) but not implemented
yet.

### Persistence

All state — oaths, groups, ledger entries, balances, altars, trade offers — is stored via a
`DataStore` adapter interface. The only implementation today is SQLite (embedded, file-based, bundled
inside the plugin jar — there is nothing separate to install or run). The interface is adapter-based
specifically so a flat-file/YAML backend can be added later without touching any calling code.

---

## Commands

### Trade contracts (in-game GUI)

```
/oathbound-trade            # open the contract builder - deposit items, post an open offer
/oathbound-trade board      # browse open contracts; click one to view/fulfill it, or your own to cancel it
```

### Debug commands

Everything else (Oaths, ProtectionGroups, the Ledger, Altars) doesn't have a player-facing UI yet — a
debug command surface lets you exercise it directly. Commands require a player, not console.

```
/oathbound-debug group create <name> [tier]
/oathbound-debug group transfer <groupId> <player|group> <targetNameOrId>
/oathbound-debug group info <groupId>
/oathbound-debug group list

/oathbound-debug oath create <otherPlayerName> [blood]
/oathbound-debug oath addflag <oathId> <text...>
/oathbound-debug oath propose <oathId>
/oathbound-debug oath seal <oathId>
/oathbound-debug oath activate <oathId>
/oathbound-debug oath fulfill <oathId>
/oathbound-debug oath breach <oathId>
/oathbound-debug oath void <oathId>
/oathbound-debug oath info <oathId>
/oathbound-debug oath list

/oathbound-debug ledger recent [limit]
/oathbound-debug ledger <oathId>

/oathbound-debug altar list
/oathbound-debug altar info <altarId>
```

`tier` is one of `INDIVIDUAL`, `COMPANY`, `TOWN`, `REGION`, `KINGDOM`. Tab completion works for
subcommands.

---

## Getting Started

### Requirements

- A **Paper 26.2** server (build 112 or later). Paper is the only supported platform — Oathbound uses
  Paper-specific API.
- **Java 25** on the machine running the server (and on whatever machine builds the plugin).
- **Nothing else.** Oathbound bundles its own SQLite driver and JSON library, shaded and relocated
  inside the plugin jar, so there's no separate database server to install, configure, or point the
  plugin at. The first time the plugin starts, it creates `oathbound.db` inside its own plugin data
  folder and runs its schema migrations automatically.

You do not need Gradle installed either — the repo ships a Gradle wrapper (`./gradlew`) that downloads
the correct Gradle version on first use.

### Building

```bash
git clone <this-repo>
cd oathbound
./gradlew build
```

This compiles the plugin, runs the test suite, and produces a shaded, ready-to-drop-in jar at
`build/libs/oathbound-<version>.jar`.

### Installing

Copy the built jar into your server's `plugins/` folder and (re)start the server:

```bash
cp build/libs/oathbound-*.jar /path/to/your/server/plugins/
```

On first boot, Oathbound will:

1. Create `plugins/Oathbound/config.yml` with the defaults shown below.
2. Create `plugins/Oathbound/oathbound.db` (SQLite) and run its schema migrations.
3. Log how many groups/oaths/altars/trade offers it loaded (zero, on a fresh install).

There's a helper script if you're iterating locally against a server checked out elsewhere on disk —
it builds, removes any previously-deployed Oathbound jar, and copies the fresh one in:

```bash
# Defaults to ~/mc-paper-server/plugins
./scripts/deploy.sh

# Or point it somewhere else
OATHBOUND_PLUGINS_DIR=/path/to/plugins ./scripts/deploy.sh
```

### Running a local test server

The project is also wired for Paper's standard plugin dev loop, which downloads and boots a real Paper
server for you with the freshly built jar already installed:

```bash
./gradlew runServer
```

The server's working files live under `run/` (gitignored). You'll need to accept the Minecraft EULA
once, in `run/eula.txt`, before it will fully start.

---

## Configuration

`plugins/Oathbound/config.yml`, generated on first run:

```yaml
persistence:
  # Path to the SQLite database file, relative to the plugin's data folder.
  sqlite-file: oathbound.db

ownership:
  # Max hops to walk when resolving a ProtectionGroup's owner chain before
  # failing safe and flagging the chain as unresolved.
  resolver-depth-cutoff: 10

economy:
  # Named currencies available on this server. The first entry is the default.
  currencies:
    - coin

altar:
  # Block required directly on top of the barrel, with a candle placed on top of that, to
  # consecrate an altar. Placing the candle is what triggers altar creation.
  capstone-material: OBSIDIAN
  # Radius (in blocks) = power-radius-scale * tier-multiplier * sqrt(current Power). Placeholder
  # curve - real tuning happens once the sacrifice/decay system is built.
  power-radius-scale: 4.0
  tier-radius-multiplier:
    INDIVIDUAL: 1.0
    COMPANY: 1.25
    TOWN: 1.5
    REGION: 2.0
    KINGDOM: 3.0
```

`capstone-material` accepts any valid Bukkit `Material` name.

---

## Roadmap

Tracking against the [master design plan](./oathbound-master-plan.md)'s build order.

### Done

- [x] Core Oath data model, lifecycle state machine, and Ledger
- [x] Native currency + ranked `ProtectionGroup` (player-or-group owner, cycle-safe live resolver)
- [x] Condition engine primitives (manual-confirm, time-elapsed, death-count, payment-received,
      vote-tally, and `AND`/`OR`/`NOT` composition) — evaluation logic exists; nothing drives it off
      real game events yet
- [x] SQLite-backed persistence adapter with async writes and an in-memory cache
- [x] Altar structure detection (barrel + capstone + candle) and altar creation at zero Power, with a
      tier-scaled, Power-based radius formula
- [x] Chest-GUI contract builder — open (no-named-counterparty) oaths, and an item-for-item trade
      contract built on top of them (post via GUI, browse a board, fulfill, auto-swap). Only this
      barter case is wired up; a general builder for arbitrary named-party clauses is still ahead.

### Not yet built

- [ ] General-purpose chest-GUI builder for named-party oaths and non-barter clause types (transfer,
      custom flags, kill counts) — today's GUI only covers the open item-trade case
- [ ] Full condition-engine wiring (clauses don't yet auto-resolve when their conditions are met)
- [ ] Virtualized Escrow — claim/expiry/abandonment handling
- [ ] Chest/door/claim access gating tied to `ProtectionGroup`
- [ ] Honor/reputation system + Blood Oath tier
- [ ] NPC Notary (rooted villager) + Sealing Table, and the async offer/counter-offer negotiation
      mailbox
- [ ] Public Oath Board (regional + capital)
- [ ] Altar sacrifice ritual (Power accrual), decay, vulnerability tiers, desecration outcomes,
      reconsecration cooldown, and claim nesting/overlap resolution
- [ ] Election Oaths
- [ ] Bounty / Kill Contracts — quantity/group targeting, head-return fulfillment, heat-scaling fees,
      banishment
- [ ] Polish pass: particle/sound effects, Notary flavor/skin system, full config-surface tuning

### Deliberately deferred (see master plan)

- Notary skin/flavor customization
- Per-relationship trust tracking (only a single global Honor score is planned)
- Release-oath hooks for reducing active banishment sentences
- Altar Power pooling across multiple altars
- Any siege/conquest mechanics beyond altar desecration

---

## Development

```bash
./gradlew test          # run the test suite
./gradlew build          # build the shaded plugin jar (runs tests first)
./gradlew runServer      # boot a local Paper test server with the plugin installed
```

The domain layer (`oath`, `group`, `economy`, `altar`, `contract` packages) has zero Bukkit
dependencies by design — it's plain, JUnit-testable Java. Bukkit-specific glue (commands, event
listeners, inventory GUIs, item (de)serialization) lives in its own packages (`command`, `listener`,
`gui`, `bukkit`) on top of that domain layer.
