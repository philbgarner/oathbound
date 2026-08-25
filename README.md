# Oathbound

**Diegetic, mechanically-binding agreements for Paper servers.**

Oathbound brings contract law to your Minecraft kingdom. Players draft, negotiate, and seal binding
Oaths — with real escrow and real reputation consequences on the line — all through in-world objects
(contract books, oath-sealing NPCs) instead of bare slash commands. It's built for fantasy-kingdom
roleplay servers that want diplomacy, betrayal, and consequences to actually mean something. Territory
protection is left to a dedicated plugin (WorldGuard is a first-class optional integration) rather than
reimplemented here.

Fully self-contained for what it *does* own: **no Vault, no LuckPerms required.** Oathbound owns its own
currency and permission/ownership systems, backed by an embedded SQLite database. WorldGuard is an
optional `softdepend` for anyone who wants area claims kept in sync with Oathbound group ownership.

> **Status:** early development. The core Oath engine, ownership/permission model, and chest-GUI flows
> for both open trade contracts and named-party oaths are implemented and tested. See [Roadmap](#roadmap)
> for exactly what's live today.

---

## What Is It?

A few moments this looks like in practice:

- **A trade nobody has to trust.** Two players who've never met agree to swap a diamond pickaxe for a
  stack of blaze rods. Neither wants to go first. Instead, each deposits their side into a chest-GUI
  trade contract - the plugin holds both, swaps them the instant the second deposit lands, and queues
  delivery safely even if someone's offline when it clears. No middleman, no risk of a ninja logout.
- **A marriage alliance, sealed and witnessed.** Two rival Houses swear a Blood Oath at the Sealing Table
  to cement a truce, with the whole court named as witnesses. It posts to the Public Oath Board the
  moment it's sealed - and if either side breaks it later, that breach posts too and costs the breaker
  real Honor plus a temporary curse. Reputation stops being a roleplay conceit and starts being a ledger
  anyone can check.
- **A claim that stays in the family.** A Kingdom links its capital's WorldGuard region to its
  `ProtectionGroup`. When the ruling house's line passes to an heir via a `TransferClause`, the linked
  region's WorldGuard owners update automatically - no separate manual step, no gap where the claim is
  unprotected or misattributed.
- **A found item and its finder.** A player deposits an item into escrow as part of an oath; if the
  recipient's offline when the release condition fires, it waits in a claimable pool instead of being
  lost, with a login nudge the moment it's there to collect.

---

## Table of Contents

- [What Is It?](#what-is-it)
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
  payment, custom RP flags, kill counts, diplomacy) gated by composable conditions (time elapsed, death
  count, payment received, vote tally, manual confirmation, and `AND`/`OR`/`NOT` combinations of any of
  the above).
- **ProtectionGroups** — the single ownership/permission primitive behind everything: chests, doors,
  companies, towns, kingdoms, diplomacy. Ownership can be a player *or* another group, so a territory can
  belong to "the ruling house" and survive a change in who leads that house without ever touching the
  territory's own record. Ownership-chain resolution is cycle-safe and depth-limited by design.
- **Native economy** — plugin-owned, multi-currency player balances. No economy plugin required.
- **WorldGuard integration (optional)** — link a `ProtectionGroup` to a WorldGuard region; the region's
  owners stay in sync with the group's members automatically across ownership changes. A `softdepend`,
  not a hard requirement - everything else works fine with WorldGuard absent.
- **Chest-GUI open trade contracts** — post an item-for-item barter offer with no named counterparty
  through an in-world chest interface; anyone can browse the open contract board and fulfill it. The
  first player to accept becomes the oath's second party and the swap happens automatically, with
  delivery held safely if either side is offline at the moment of completion.
- **Chest-GUI named-party oath builder** — draft an oath against a specific counterparty and attach
  transfer, custom-flag, kill-count, and escrow clauses to it through an in-world chest interface, then
  propose it. The named counterparty reviews and signs (or declines) it from their own pending-oaths
  board.
- **Live condition-engine wiring** — a periodic engine watches every active oath and executes a
  transfer, escrow, or diplomacy clause's effect, or resolves a kill-count clause, the moment its
  condition is actually met, rather than just being able to evaluate whether it *would* be met.
- **Virtualized Escrow** — items and currency staked as part of an oath are withdrawn from the depositor
  immediately; currency pays straight into the recipient's balance on release, items go into a claimable
  pool with a configurable expiry that returns them to the depositor if nobody claims them.
- **Diplomacy** — set `WAR`/`PEACE`/`ALLIANCE` between two groups' senior-most ("root") owners, either
  unilaterally (declaring war) or via a mutually-sealed treaty oath; PvP damage between two players can
  be restricted to only when their groups are at declared war.
- **Ceremony Designer** — admin-authored templates that skip the usual propose-and-wait handshake: hand
  a tagged item to a player (or bind it to a pressure plate/button), they get a dialogue prompt and a
  click-to-accept confirmation, and the oath activates immediately on accept.
- **Anchored villager NPCs** — install any of 13 vanilla villager professions at a fixed spot; the
  spawned villager is invulnerable and can't wander off, but otherwise behaves exactly like a normal
  Minecraft villager - default profession, default trades, default trade GUI. No custom shop system to
  configure.
- **Honor & reputation** — a global Honor score per player, moved automatically by oath outcomes, with
  Blood Oath amplification and cosmetic title tiers.
- **Public Oath Board** — a physical board (regional or capital-wide) that surfaces witnessed oath
  activity without any separately-stored feed - it queries the Ledger live.
- **Everything persists** — SQLite-backed via a pluggable storage adapter, with an in-memory cache for
  fast permission checks and async writes so gameplay never blocks on disk I/O.

---

## How It Works

### Oaths

An Oath has two or more parties, an optional list of witnesses, and a list of clauses. It starts life
as a private `DRAFT`, gets `PROPOSED` to the other party, becomes `SEALED` once accepted, then `ACTIVE`
while its conditions are tracked. It resolves into exactly one of `FULFILLED`, `BROKEN`, or `VOIDED` —
and stays there; terminal states have no further transitions. Every transition is recorded in the
**Ledger**, an append-only log that's the single source of truth for oath history - the Public Oath
Board queries it live rather than keeping its own copy.

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

This is deliberately a narrow, self-contained feature rather than the general-purpose Escrow system —
it doesn't use the generic `EscrowClause`/release-schedule model, just a simpler direct swap.

### Named-party oaths (the general-purpose builder)

`/oathbound-oath create <player>` drafts a two-party oath against a named counterparty and opens a chest
GUI for building it up: click a button to add a **transfer** clause (reassigns one of your
`ProtectionGroup`s' ownership - the mechanism behind, for example, passing a claim to an heir), a
**custom flag** (free-text roleplay clause with no mechanical effect), a **kill count** (a target player
and a required kill tally, resolved once `DeathTracker` confirms it), or an **escrow** clause (deposit
items and/or currency, released to the counterparty once the oath is signed). A separate **Add Witness**
button (not a clause - witnesses don't affect the oath's terms) lets you name players who'll see this
oath post to any Oath Board once it's sealed, fulfilled, or broken; unwitnessed oaths never post
anywhere, for privacy. Since chest GUIs have no text field, any free text or numbers a clause needs (flag
wording, a target's name, a quantity, a currency amount, a witness's name) are collected by closing the
GUI and typing the answer in chat, which the plugin intercepts. Once at least one clause is attached,
**Propose** sends the draft to the named counterparty; they review it and sign or decline it from their
own `/oathbound-oath pending` board. Signing carries the oath straight through `SEALED` into `ACTIVE`,
same as accepting an open contract does.

Every clause added this way is gated by an `Immediate` condition (the builder doesn't expose a condition
picker yet), but once the oath is `ACTIVE` a periodic condition engine actually executes it: transfer
clauses reassign ownership (and, if the group is linked to a WorldGuard region, trigger an owner-list
resync - see [WorldGuard Integration](docs/worldguard-integration.md)), escrow clauses pay their
currency straight into the recipient's balance and drop any items into a claimable pool
(`/oathbound-oath claim`), and kill-count clauses resolve once the tracked death count is met.

### Escrow

`EscrowClause` deposits are withdrawn from the depositor (items pulled from their inventory, currency
from their balance) the moment the clause is added to a draft, and refunded if the draft is cancelled
before being proposed. Once the oath is signed and its release condition is met, currency pays straight
into the recipient's balance - no claim needed, a balance isn't a delivery problem. Items are different:
they need a real inventory to land in, so they become an unclaimed batch instead of being pushed
anywhere. `/oathbound-oath claim` lists everything you can currently claim (normally as the clause's
recipient), and you get a login nudge if something's waiting. A release schedule with multiple steps
fires atomically - everything releases once every step's condition is true at once, not incrementally.
If a released item batch goes unclaimed for `escrow.claim-expiry-days` (30 by default), it flips back to
being claimable by the original depositor instead.

### ProtectionGroups

Every claim, faction, company, or kingdom is a `ProtectionGroup`. Groups have members with roles
(each role carries a rank weight and a permission set — invite, vote, open containers, transfer
ownership, accept deals on the group's behalf, etc.), and an `owner`, which is either a specific player
or *another group*. Resolving who ultimately owns something walks that chain live, with a fixed depth
cutoff — and any ownership change that would make a group its own ancestor is rejected at write time,
before it's ever persisted. `/oathbound-group create <name> [tier]` is the player-facing way to found
one — the creator becomes its sole "Owner"-role member; there's no in-game invite/membership command yet
(see [Roadmap](#roadmap)), so a group has exactly one member until that ships. `/oathbound-group
link-region <groupId> <regionId>` links the group to a WorldGuard region, if WorldGuard is installed.

### Economy

A native, per-player, multi-currency balance system. No Vault, no external plugin — Oathbound is the
economy.

### Diplomacy

A pairwise relation (`NEUTRAL`/`WAR`/`PEACE`/`ALLIANCE`) between two groups' *root* owners - diplomatic
authority always belongs to the most senior group in an ownership chain, so a vassal's relations are
whatever its liege's relations are. War can be declared unilaterally (a betrayal from an existing
Peace/Alliance costs the declarer Honor); Peace/Alliance requires a mutually-sealed treaty oath
(`DiplomacyClause`), since it rides on the normal propose/seal handshake. `pvp.restrict-to-declared-wars`
(on by default) means two players can only deal PvP damage to each other if their groups' root relation
is `WAR` - a sealed Peace or Alliance actually means something mechanically, not just cosmetically.

### Ceremony Designer

Admin-authored templates (`config.yml`'s `ceremony-templates`) bind a group-to-individual agreement to a
tagged item, given out via `/oathbound-debug ceremony give <templateId> <groupId> [player]`.
Right-clicking another player with the item (or right-clicking it onto an already-placed pressure
plate/button to bind the ceremony there instead, so the binder doesn't need to be online for the trigger
to fire) speaks the template's `dialogue` to the target in chat, ending in a clickable [Accept]/[Decline]
prompt - clicking Accept immediately materializes the template's clauses into a real Oath, taken straight
through `DRAFT → PROPOSED → SEALED → ACTIVE` in one call since both sides already consented via the
ceremony itself. Ships with one live default template, `welcome-pact` — a zero-mechanical-stakes
onboarding pledge safe to hand any new player without review.

### Anchored villager NPCs

Thirteen `/oathbound-<role>` commands (armorer, butcher, cartographer, cleric, farmer, fisherman,
fletcher, leatherworker, librarian, mason, shepherd, toolsmith, weaponsmith — every vanilla `Villager`
profession that actually trades) each spawn a rooted, invulnerable Villager skinned for that role at
your location - AI disabled and removal suppressed so it can't wander off or despawn, but otherwise a
completely standard villager. Right-click it and you get the normal vanilla trade GUI for its profession;
Oathbound doesn't intercept the interaction or maintain a custom price list.

### Public Oath Board

Right-click the configured block (an oak sign by default) to open a small hub: **Browse Postings**
(read-only - a live-queried feed of recent witnessed activity, newest first), **Bind To A Group** (pick
one of your groups; the board becomes regional, showing only postings where a party is a member of that
group), or **Make Capital** (unbound - shows everything). Only *witnessed* oaths post, and only on
sealing, fulfillment, or breach; a witnessed Blood Oath breaking also broadcasts a dramatic message
server-wide, while every other posting is only visible by browsing a board. Nothing about a posting is
separately stored - the feed is computed fresh from the Ledger and the live oath cache every time the
board is opened, the same pattern every other board-style GUI in this plugin already uses.

### Persistence

All state — oaths, groups, ledger entries, balances, trade offers, death records, escrow claims,
protections, Honor, oath boards, villager NPCs, diplomatic relations, ceremony triggers, WorldGuard
region links — is stored via a `DataStore` adapter interface. The only implementation today is SQLite
(embedded, file-based, bundled inside the plugin jar — there is nothing separate to install or run). The
interface is adapter-based specifically so a flat-file/YAML backend can be added later without touching
any calling code.

---

## Commands

### Groups & claims (in-game)

```
/oathbound-group create <name> [tier]              # found a Company/Town/Region/Kingdom - you become its sole Owner
/oathbound-group link-region <groupId> <regionId>   # link the group to a WorldGuard region (requires TRANSFER_OWNERSHIP)
```

`create` is needed before a chest or door can be locked to anything, or before any of the commands below
can name a group you belong to. `tier` defaults to `INDIVIDUAL` if omitted. `link-region` is a no-op
data record (still saved) if WorldGuard isn't installed - see [WorldGuard Integration](docs/worldguard-integration.md).

### Trade contracts (in-game GUI)

```
/oathbound-trade            # open the contract builder - deposit items, post an open offer
/oathbound-trade board      # browse open contracts; click one to view/fulfill it, or your own to cancel it
```

### Named-party oaths (in-game GUI)

```
/oathbound-oath create <playerName> [blood]   # draft a named-party oath, then build it up in the chest GUI
/oathbound-oath pending                       # review oaths proposed to you; click one to sign or decline it
/oathbound-oath claim                         # list escrow item batches you can currently claim
```

Right-click the configured Sealing Table block (a lectern by default) for a face-to-face shortcut
straight to the draft-name prompt.

### Villager NPCs (in-game)

```
/oathbound-fisherman install <name...>      # and armorer, butcher, cartographer, cleric, farmer,
/oathbound-mason install <name...>          # fletcher, leatherworker, librarian, shepherd, toolsmith,
/oathbound-farmer install <name...>         # weaponsmith - same "install <name...>" shape for all 13
/oathbound-librarian install <name...>
```

Right-click an installed NPC for the normal vanilla trade GUI of its profession.

### Public Oath Board (in-game)

No install command - right-click the configured board block (an oak sign by default) anywhere to open
its hub and bind it (regional, to one of your groups) or leave/make it capital, then browse its feed.

### Debug commands

Everything else (Oaths, ProtectionGroups, the Ledger, Diplomacy, Ceremony) doesn't have a player-facing
UI yet — a debug command surface lets you exercise it directly. Commands require a player, not console.
The whole surface requires the `oathbound.debug` permission (`default: op` in `plugin.yml`) — nothing
under it is meant to be player-safe (arbitrary oath construction, `group transfer`, `honor adjust`, and
so on), so an admin/testing role is expected to grant it explicitly on servers that don't want every
player using it.

```
/oathbound-debug group create <name> [tier]   # same as /oathbound-group create - kept here too for admin/testing scripting
/oathbound-debug group transfer <groupId> <player|group> <targetNameOrId>
/oathbound-debug group info <groupId>
/oathbound-debug group list

/oathbound-debug oath create <otherPlayerName> [blood]
/oathbound-debug oath addflag <oathId> <text...>
/oathbound-debug oath adddiplomacy <oathId> <groupA> <groupB> <war|peace|alliance>
/oathbound-debug oath confirm <oathId>
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

/oathbound-debug honor info [player]
/oathbound-debug honor adjust <player> <delta>

/oathbound-debug board list
/oathbound-debug board info <boardId>
/oathbound-debug board remove <boardId>

/oathbound-debug villager list
/oathbound-debug villager info <villagerNpcId>
/oathbound-debug villager remove <villagerNpcId>

/oathbound-debug ceremony give <templateId> <groupId> [player]
/oathbound-debug ceremony list
/oathbound-debug ceremony triggers

/oathbound-debug diplomacy declare-war <groupA> <groupB>
/oathbound-debug diplomacy info <groupA> <groupB>
/oathbound-debug diplomacy list
```

`tier` is one of `INDIVIDUAL`, `COMPANY`, `TOWN`, `REGION`, `KINGDOM`. Tab completion works for
subcommands.

---

## Getting Started

### Requirements

- A **Paper 26.2** server (build 112 or later). Paper is the only supported platform — Oathbound uses
  Paper-specific API.
- **Java 25** on the machine running the server (and on whatever machine builds the plugin).
- **Nothing else required.** Oathbound bundles its own SQLite driver and JSON library, shaded and
  relocated inside the plugin jar, so there's no separate database server to install, configure, or
  point the plugin at. The first time the plugin starts, it creates `oathbound.db` inside its own plugin
  data folder and runs its schema migrations automatically. **Optional:** install
  [WorldGuard](https://enginehub.org/worldguard) too if you want `/oathbound-group link-region` to
  actually sync region owners - Oathbound loads and runs fine without it.

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
3. Log how many groups/oaths/trade offers/etc. it loaded (zero, on a fresh install).

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

escrow:
  # Days an escrow release sits unclaimed before it's returned to the depositor instead of the
  # recipient. Currency releases are unaffected - only unclaimed items expire.
  claim-expiry-days: 30

protection:
  # Item that must be held to lock/unlock a chest or door - right-click the block while holding
  # this item to open the group picker.
  lock-tool-material: TRIPWIRE_HOOK

honor:
  fulfill-gain-base: 10
  breach-loss-base: 20
  blood-oath-multiplier: 2.0
  min-honor-for-blood-oath: 0
  tiers:
    -1000: Faithbroken
    0: Unproven
    500: Oathkeeper
    2000: Renowned
  blood-oath-breach-debuff-effect: WEAKNESS
  blood-oath-breach-debuff-duration-seconds: 300
  blood-oath-breach-debuff-amplifier: 1

notary:
  sealing-table-material: LECTERN
  pending-offer-cap-per-player: 10
  pending-offer-cap-per-sender-recipient: 2
  negotiation-expiry-days: 7

oath-board:
  material: OAK_SIGN
  feed-size: 50

ceremony-templates:
  - id: welcome-pact
    display-name: "Welcome Pact"
    item-material: PAPER
    item-display-name: "Charter of Welcome"
    dialogue:
      - "{initiator} offers you a place among these lands, {target}."
      - "Swear only to keep the peace here - nothing more is asked of you."
      - "Do you accept?"
    prompt-timeout-seconds: 60
    blood-oath: false
    clauses:
      - type: custom-flag
        text: "Keep the peace of this land."

ceremony-block-triggers-enabled: true

diplomacy:
  betrayal-honor-penalty: 20

pvp:
  restrict-to-declared-wars: true
```

`lock-tool-material`, `sealing-table-material`, and `oath-board.material` all accept any valid Bukkit
`Material` name (each must be distinct), and `blood-oath-breach-debuff-effect` accepts any valid Bukkit
`PotionEffectType` name.

---

## Roadmap

Tracking against the [master design plan](./oathbound-master-plan.md)'s build order, since rescoped to
cut territory/kill-contract mechanics duplicated by dedicated plugins.

### Implemented

- Core Oath data model, lifecycle state machine, and Ledger
- Native currency + ranked `ProtectionGroup` (player-or-group owner, cycle-safe live resolver)
- Condition engine primitives (manual-confirm, time-elapsed, death-count, payment-received, vote-tally,
  and `AND`/`OR`/`NOT` composition) and evaluation logic, wired to actually execute Transfer/Escrow/
  Diplomacy clause effects and resolve Kill-Count clauses
- SQLite-backed persistence adapter with async writes and an in-memory cache
- Chest-GUI contract builder — open (no-named-counterparty) oaths, and an item-for-item trade contract
  built on top of them (post via GUI, browse a board, fulfill, auto-swap)
- General-purpose chest-GUI builder for named-party oaths — attach transfer, custom-flag, kill-count,
  and escrow clauses, propose to a named counterparty, and sign/decline from a pending-oaths board
- Virtualized Escrow — deposit-on-add, atomic multi-step release schedules, a claimable pool for items
  with configurable expiry-back-to-depositor
- Chest/door access gating tied to `ProtectionGroup` permissions
- Honor/reputation system + Blood Oath tier, with cosmetic title tiers
- Diplomacy — unilateral war declarations and mutually-sealed treaty oaths between groups' root owners,
  with an optional PvP-restriction side effect
- Ceremony Designer — templated item/block-trigger oaths with click-based accept/decline
- Public Oath Board (regional + capital), fed live from the Ledger
- Anchored, invulnerable villager NPCs for all 13 trading professions, using vanilla trades
- WorldGuard integration — link a `ProtectionGroup` to a region, keep its owners synced across
  `TransferClause`-driven ownership changes

**Known limitation carried across most of the above:** the domain model has no fault-attribution
concept, so Honor/debuff effects on a `BROKEN` oath apply to every party, not just whoever actually broke
it - and `BROKEN` is still only reachable via the debug command (no automated "unmet deadline" detection
exists). A first step - fault-tagging on manually-declared breaches - is fully researched and parked in
[`fault-attribution-plan.md`](./fault-attribution-plan.md).

### Not yet built

- [ ] Fault attribution (see above and `fault-attribution-plan.md`)
- [ ] In-game group invite/membership management - a group has exactly one member (its founder) until
      this ships
- [ ] Polish pass: particle/sound effects, full config-surface tuning

### Deliberately cut (previously built, removed in a rescope)

Territory claiming (Altar consecration/Power/decay/desecration) and PvP kill contracts
(Bounty/Banishment/PvE contracts) were fully built at one point but have been removed - they duplicated
what dedicated plugins already do better. WorldGuard is the recommended replacement for territory
protection (see [WorldGuard Integration](docs/worldguard-integration.md)); no replacement is bundled for
bounty/kill-contract mechanics, though a dedicated PvP/bounty plugin can be paired instead. The "Notary"
NPC concept was folded into the general anchored-villager-NPC system rather than removed outright - see
[Anchored villager NPCs](#anchored-villager-npcs) above.

### Deliberately deferred (see master plan)

- Election Oaths — fully researched (condition-engine changes, a new `election` domain package,
  GUI/command/persistence surface) but deliberately deferred as too large a single pass; the
  implementation plan is saved in [`election-oaths-plan.md`](./election-oaths-plan.md) if revisited.
- Per-relationship trust tracking (only a single global Honor score is planned)

---

## Development

```bash
./gradlew test          # run the test suite
./gradlew build          # build the shaded plugin jar (runs tests first)
./gradlew runServer      # boot a local Paper test server with the plugin installed
```

The domain layer (`oath`, `group`, `economy`, `contract`, `diplomacy` packages) has zero Bukkit
dependencies by design — it's plain, JUnit-testable Java. Bukkit-specific glue (commands, event
listeners, inventory GUIs, item (de)serialization) lives in its own packages (`command`, `listener`,
`gui`, `bukkit`) on top of that domain layer. `worldguard` isolates every WorldGuard/WorldEdit API call
behind a soft-dependency wrapper so the rest of the plugin never touches those types directly.
