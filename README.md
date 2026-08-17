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
> detection, and chest-GUI flows for both open trade contracts and named-party oaths are implemented and
> tested. Everything else in the [Roadmap](#roadmap) is still on the way — see that section for exactly
> what's live today.

---

## What Is It?

A few moments this looks like in practice:

- **A trade nobody has to trust.** Two players who've never met agree to swap a diamond pickaxe for a
  stack of blaze rods. Neither wants to go first. Instead, each deposits their side into a chest-GUI
  trade contract - the plugin holds both, swaps them the instant the second deposit lands, and queues
  delivery safely even if someone's offline when it clears. No middleman, no risk of a ninja logout.
- **A marriage alliance, sealed and witnessed.** Two rival Houses swear a Blood Oath at the Notary to
  cement a truce, with the whole court named as witnesses. It posts to the Public Oath Board the moment
  it's sealed - and if either side breaks it later, that breach posts too, broadcasts server-wide, and
  costs the breaker real Honor plus a temporary curse. Reputation stops being a roleplay conceit and
  starts being a ledger anyone can check.
- **A border fort that can actually fall.** A frontier kingdom raises an altar and feeds it enchanted
  gear to keep its claim radius wide enough to matter. Let sacrifices lapse, though, and Power decays -
  drop below Critical and the altar's protection collapses, leaving its barrel open to being looted or
  destroyed outright by whoever gets there first. Territory has to be maintained, not just claimed once.
- **A price on a serial oath-breaker's head.** Tired of a rival who keeps breaking deals, a player posts
  a bounty on them at the Notary board. Anyone who lands the qualifying kill turns in the dropped head
  for the payout, and the target does real time in a purpose-built prison before they're teleported back
  to where they died.

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
  (Individual → Company → Town → Region → Kingdom). Right-click the barrel to sacrifice enchanted items
  and charge Power, which decays back to zero over a configurable number of days if left unmaintained;
  a Critical-Power altar loses protection entirely and its barrel can be Destroyed (a Desecration) or
  Looted.
- **Chest-GUI open trade contracts** — post an item-for-item barter offer with no named counterparty
  through an in-world chest interface; anyone can browse the open contract board and fulfill it. The
  first player to accept becomes the oath's second party and the swap happens automatically, with
  delivery held safely if either side is offline at the moment of completion.
- **Chest-GUI named-party oath builder** — draft an oath against a specific counterparty and attach
  transfer, custom-flag, kill-count, and escrow clauses to it through an in-world chest interface, then
  propose it. The named counterparty reviews and signs (or declines) it from their own pending-oaths
  board.
- **Live condition-engine wiring** — a periodic engine watches every active oath and executes a
  transfer or escrow clause's effect, or resolves a kill-count clause, the moment its condition is
  actually met (death counts, elapsed time, manual confirmation, or currency already escrowed), rather
  than just being able to evaluate whether it *would* be met.
- **Virtualized Escrow** — items and currency staked as part of an oath are withdrawn from the depositor
  immediately; currency pays straight into the recipient's balance on release, items go into a claimable
  pool with a configurable expiry that returns them to the depositor if nobody claims them.
- **Bounty / Kill Contracts** — place a PvP bounty on a player or every member of a group through the
  Notary's Bounty Board; the fee scales with how "hot" the target already is and decays over time, with a
  hard daily placement cap and a discount if they recently broke an oath with you. A qualifying kill force
  drops a tagged head — turning it in at any Notary is the fulfillment action (no kill-attribution
  tracking), paying per head for group contracts, and banishing the victim to a purpose-built End pen for
  a duration scaled by the bounty paid, with stacking sentences capped at a configurable maximum. Standing,
  repeatable, admin-authored PvE kill contracts share the same Bounty Board for a currency-driven mob-kill
  economy tap.
- **Everything persists** — SQLite-backed via a pluggable storage adapter, with an in-memory cache for
  fast permission checks and async writes so gameplay never blocks on disk I/O.

See [`oathbound-master-plan.md`](./oathbound-master-plan.md) for the full design doc, including
systems not built yet (elections and the full counter-offer negotiation chain).

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

This is deliberately a narrow, self-contained feature rather than the general-purpose Escrow system
described in the master plan (§4) — it doesn't use the generic `EscrowClause`/release-schedule model
(that's built now, see below, but the trade board still doesn't use it - it's a simpler direct swap).

### Named-party oaths (the general-purpose builder)

`/oathbound-oath create <player>` drafts a two-party oath against a named counterparty and opens a chest
GUI for building it up: click a button to add a **transfer** clause (reassigns one of your
`ProtectionGroup`s' ownership), a **custom flag** (free-text roleplay clause with no mechanical effect),
a **kill count** (a target player and a required kill tally - not executed yet, see the Roadmap), or an
**escrow** clause (deposit items and/or currency, released to the counterparty once the oath is signed).
A separate **Add Witness** button (not a clause - witnesses don't affect the oath's terms) lets you name
players who'll see this oath post to any Oath Board once it's sealed, fulfilled, or broken; unwitnessed
oaths never post anywhere, for privacy. Since chest GUIs have no text field, any free text or numbers a
clause needs (flag wording, a target's name, a quantity, a currency amount, a witness's name) are
collected by closing the GUI and typing the answer in chat, which the plugin intercepts. Once at least
one clause is attached, **Propose** sends the draft to the
named counterparty; they review it and sign or decline it from their own `/oathbound-oath pending` board.
Signing carries the oath straight through `SEALED` into `ACTIVE`, same as accepting an open contract does.

Every clause added this way is gated by an `Immediate` condition (the builder doesn't expose a condition
picker yet), but once the oath is `ACTIVE` a periodic condition engine actually executes it: transfer
clauses reassign ownership, and escrow clauses pay their currency straight into the recipient's balance
and drop any items into a claimable pool (`/oathbound-oath claim`). Kill-count clauses still have no
execution behind them - see the [Roadmap](#roadmap).

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
being claimable by the original depositor instead. Escheat-to-Notary and breach-split abandonment
policies from the master plan aren't implemented, since neither a Notary nor a breach-split system exists
yet - "return to depositor" is the only policy for now.

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
the spot, owned by whoever placed the candle, at **zero Power**, provided the location doesn't fall
inside another altar's claim of the same or smaller `GroupTier` (nesting inside a strictly larger one -
federating under a liege - is allowed). Its claim radius is computed live from current Power — at zero
Power, that's zero radius.

Right-clicking the barrel opens a chest-GUI **sacrifice interface**: deposit enchanted items (item type
doesn't matter, only the enchantment profile does) and confirm to consume them permanently and add to
Power. Power is never stored as a raw number — like radius, it's recomputed live from the last
sacrifice's total and a configurable "days full-to-empty" decay rate, so it visibly drains if the altar
isn't maintained. Three vulnerability tiers gate everything else: **Normal** and **Decaying** (a
configurable warning threshold triggers a login nudge to the owner) both keep full protection; only
**Critical** (Power at or below a small configurable threshold) drops protection to zero and makes the
barrel interactable for an outcome — **Destroy** it (a Desecration: Honor penalty for the breaker, a
dramatic server-wide broadcast, and the altar record is deleted outright, requiring the structure to be
physically rebuilt) or **Loot** it (XP orbs worth the most recent sacrifice's value, no Honor penalty by
default). Any successful top-up starts a configurable reconsecration cooldown (5 minutes by default)
before protection re-engages, even if Power already reads as sufficient.

### Notary

`/oathbound-notary install <name>` spawns a rooted, invulnerable Villager at your location — no
wandering AI, no vanilla trading. Right-click it to open a small hub: start a new named-party oath draft
(you type the counterparty's name in chat) or review oaths proposed to you, from any Notary you
interact with — the mailbox is scoped to you, not the physical NPC. The Sealing Table (right-click the
configured block, a lectern by default) is a face-to-face shortcut straight to the draft prompt, skipping
the hub. Proposals respect a per-recipient pending-offer cap and auto-void, neutrally, after sitting
unanswered past a configurable number of days. Recipients still only sign or decline from the existing
pending-oath board — editable counter-offers (the master plan's full negotiation state machine) aren't
implemented yet.

### Bounty / Kill Contracts

The Notary's hub gains a fourth button, the **Bounty Board**: browse every active bounty, cancel your
own (refunds the unpaid remainder — the placement fee is a sunk cost), or place a new one. Placing a
bounty types the target's player or group name in chat, a quantity if it's a group (capped at that
group's current member count), and a total reward, then confirms a heat-scaled Notary fee before any
currency moves. Heat is never stored — like Altar Power, it's recomputed live from every other
active/recent bounty on the same target, decaying over a configurable window, so the first bounty on
someone is cheap and stacking several gets expensive fast; a hard daily cap on placements applies
independent of heat, and a discount kicks in if the target broke an oath with you recently (approximated
as "any oath between you two went `BROKEN`" — the Ledger has no fault-attribution concept, same known
limitation as Honor).

Bounties are placed *unilaterally* — the target never consents — so they aren't `Oath`s: dying to a
qualifying kill force-drops a tagged player head (native item, no kill-attribution needed) and starts a
banishment sentence for the victim. **Turning the head in at any Notary is the fulfillment action** —
whoever holds and returns it gets paid, not necessarily who landed the kill. Group contracts pay per head
in even installments, the contract staying open until every head is turned in. Banishment teleports the
victim to a fixed, admin-built End pen (`/oathbound-debug banishment set-pen` records the coordinates)
for real-world hours scaled by the bounty amount just paid, clamped to a min/max; a subsequent qualifying
kill while already serving extends the remaining time rather than resetting it, capped at a configurable
maximum total. The pen holds the player even across logout/respawn until release, which auto-teleports
them back to where they died — checked on a schedule and on login if it elapsed while offline.

The same board also lists **standing PvE contracts** — admin-authored, repeatable kill-a-number-of-this-
mob quests for currency, configured in `config.yml` rather than built per-instance. These use native
Bukkit kill-attribution (unlike bounties) since there's no reason to avoid it for a server-owned economy
tap, and pay out (then reset for the next batch) the moment the required kill count is reached.

A one-time login notice tells a player they have a bounty on their head the first time they log in after
it's placed (not every login); `/oathbound-bounty list [player]` is the always-available pull command,
and `/oathbound-bounty notify off` opts out of the login notice entirely.

### Villager Shops

Thirteen `/oathbound-<role>` commands (armorer, butcher, cartographer, cleric, farmer, fisherman,
fletcher, leatherworker, librarian, mason, shepherd, toolsmith, weaponsmith — every vanilla Villager
profession that actually trades) each spawn the same kind of rooted, invulnerable Villager the Notary
uses, skinned for that role. Right-click one to open a fixed buy/sell chest menu instead of vanilla
trading — every NPC of a given role shares that role's stock, a plain admin-edited list of items and
prices in `config.yml` (`villagers.<role>.sells` / `.buys`), not a per-instance builder. Buying withdraws
from your balance and grants the item; selling takes the item and deposits your balance, both through the
same `EconomyService` the rest of the plugin uses. Four roles (fisherman, mason, farmer, librarian) ship
with a small default stock out of the box; the rest start empty until an admin configures them.

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

All state — oaths, groups, ledger entries, balances, altars, trade offers, death records, escrow claims,
protections, Honor, notaries, oath boards, villager shop NPCs, bounties, banishments, PvE contract
progress — is stored via a `DataStore` adapter interface. The only implementation today is SQLite (embedded, file-based, bundled
inside the plugin jar — there is nothing separate to install or run). The interface is adapter-based
specifically so a flat-file/YAML backend can be added later without touching any calling code.

---

## Commands

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

### Notary NPC (in-game)

```
/oathbound-notary install <name...>   # spawn a rooted, invulnerable Notary Villager at your location
```

Right-click an installed Notary to open its menu (start a new named-party oath draft, review oaths
proposed to you, or open the Bounty Board), or right-click the configured Sealing Table block (a lectern
by default) for a face-to-face shortcut straight to the draft prompt.

### Bounty / Kill Contracts (in-game GUI + commands)

```
/oathbound-bounty list [playerName]   # show active bounties on you (or another player)
/oathbound-bounty notify <on|off>     # toggle the one-time login notice
```

Placement itself has no command — open the Bounty Board from any Notary, click "Place New Bounty," and
follow the chat prompts (target, quantity if a group, reward) through to the fee-preview confirm screen.
Fulfillment is just holding a tagged head and right-clicking any Notary.

### Villager Shops (in-game)

```
/oathbound-fisherman install <name...>      # and armorer, butcher, cartographer, cleric, farmer,
/oathbound-mason install <name...>          # fletcher, leatherworker, librarian, shepherd, toolsmith,
/oathbound-farmer install <name...>         # weaponsmith - same "install <name...>" shape for all 13
/oathbound-librarian install <name...>
```

Right-click an installed shop NPC to open its buy/sell menu. Stock and prices are fixed per role in
`config.yml`, not set per-NPC.

### Public Oath Board (in-game)

No install command - right-click the configured board block (an oak sign by default) anywhere to open
its hub and bind it (regional, to one of your groups) or leave/make it capital, then browse its feed.

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

/oathbound-debug altar list
/oathbound-debug altar info <altarId>

/oathbound-debug honor info [player]
/oathbound-debug honor adjust <player> <delta>

/oathbound-debug notary list
/oathbound-debug notary info <notaryId>
/oathbound-debug notary remove <notaryId>

/oathbound-debug board list
/oathbound-debug board info <boardId>
/oathbound-debug board remove <boardId>

/oathbound-debug villager list
/oathbound-debug villager info <villagerNpcId>
/oathbound-debug villager remove <villagerNpcId>

/oathbound-debug bounty list [player]
/oathbound-debug bounty info <bountyId>
/oathbound-debug bounty cancel <bountyId>
/oathbound-debug bounty heat <player>            # preview the current heat-scaled fee for a target

/oathbound-debug banishment list
/oathbound-debug banishment info <player>
/oathbound-debug banishment release <player>     # admin override, releases + teleports back immediately
/oathbound-debug banishment set-pen              # records your current location as the End pen destination
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

escrow:
  # Days an escrow release sits unclaimed before it's returned to the depositor instead of the
  # recipient. Currency releases are unaffected - only unclaimed items expire.
  claim-expiry-days: 30

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
  negotiation-expiry-days: 7

oath-board:
  material: OAK_SIGN
  feed-size: 50

bounty:
  fee-base: 100
  heat-fee-multiplier: 0.5
  heat-decay-hours: 72
  max-placements-per-24h: 3
  breach-discount-window-days: 14
  breach-discount-fraction: 0.5
  abandon-inactivity-days: 30

banishment:
  hours-per-currency-unit: 50
  min-hours: 1
  max-hours: 72
  stack-cap-hours: 168
  # Fixed End-pen destination - build the pen by hand, then run
  # /oathbound-debug banishment set-pen at the release point instead of editing these by hand.
  pen:
    world: world_the_end
    x: 0.5
    y: 64.0
    z: 0.5
    yaw: 0.0
    pitch: 0.0

# Standing, repeatable, admin-authored PvE kill contracts - empty by default.
# pve-contracts:
#   - id: spider-cull
#     display-name: "Spider Cull"
#     mob: SPIDER
#     quantity: 20
#     reward:
#       coin: 200

villagers:
  # Each role's shop NPCs all share one fixed buy/sell list, edited here - not a per-instance builder.
  fisherman:
    sells:
      - {material: COD, price: 2}
      - {material: FISHING_ROD, price: 15}
    buys:
      - {material: TROPICAL_FISH, price: 1}
  # ...mason, farmer, librarian ship with a small default stock too; the other 9 roles start empty
  # (sells: [] / buys: []) until configured.
```

`capstone-material`, `lock-tool-material`, `sealing-table-material`, and `oath-board.material` all accept
any valid Bukkit `Material` name (each must be distinct), and `blood-oath-breach-debuff-effect` accepts
any valid Bukkit `PotionEffectType` name. Each `villagers.<role>.sells`/`.buys` entry's `material` also
accepts any valid Bukkit `Material` name.

---

## Roadmap

Tracking against the [master design plan](./oathbound-master-plan.md)'s build order.

### Done

- [x] Core Oath data model, lifecycle state machine, and Ledger
- [x] Native currency + ranked `ProtectionGroup` (player-or-group owner, cycle-safe live resolver)
- [x] Condition engine primitives (manual-confirm, time-elapsed, death-count, payment-received,
      vote-tally, and `AND`/`OR`/`NOT` composition) and evaluation logic
- [x] SQLite-backed persistence adapter with async writes and an in-memory cache
- [x] Altar structure detection (barrel + capstone + candle) and altar creation at zero Power, with a
      tier-scaled, Power-based radius formula
- [x] Chest-GUI contract builder — open (no-named-counterparty) oaths, and an item-for-item trade
      contract built on top of them (post via GUI, browse a board, fulfill, auto-swap).
- [x] General-purpose chest-GUI builder for named-party oaths — attach transfer, custom-flag,
      kill-count, and escrow clauses, propose to a named counterparty, and sign/decline from a
      pending-oaths board.
- [x] Condition-engine wiring for transfer and escrow clauses — a periodic engine evaluates every
      `ACTIVE` oath's clauses against real backends (a persisted, timestamped death log feeding
      `DeathCount`, wall-clock time for `TimeElapsed`, a manual-confirm store, `Immediate`, and
      `AND`/`OR`/`NOT` composition) and executes the effect the moment a condition is met — ownership
      reassignment for `TransferClause`, currency-to-balance and item-to-claim for `EscrowClause` — then
      auto-carries the oath to `FULFILLED` once every clause it contains has resolved this way.
      A release schedule with several steps fires atomically (everything releases once every step's
      condition holds at once), not incrementally per step. `VoteTally` is still an honest no-op stub
      (needs Election Oaths, still below), and `KillCountClause` is left untouched by this engine — an
      oath containing one is deliberately never auto-fulfilled.
- [x] Virtualized Escrow — items/currency deposited into an escrow clause are withdrawn from the
      depositor immediately; currency releases straight to the recipient's balance, items go into a
      claimable pool (`/oathbound-oath claim`, plus a login nudge) since delivery needs a real inventory.
      Unclaimed items past a configurable expiry (`escrow.claim-expiry-days`, default 30) flip back to
      claimable by the depositor instead. `PaymentReceived` now reports how much of a currency an oath's
      own escrow clauses hold, so a same-oath `TransferClause` can gate on it. Escheat-to-Notary and
      breach-split abandonment policies aren't implemented (no Notary or breach-split system exists yet)
      — "return to depositor" is the only policy for now.

- [x] Chest/door/claim access gating tied to `ProtectionGroup` — hold a configurable item (a
      tripwire hook by default) and right-click a chest or door to open a chest-menu GUI listing every
      group you're allowed to lock it to (you must already hold that group's `OPEN_CONTAINERS`/
      `OPEN_DOORS` permission to lock a new block to it); a locked block then denies interaction to
      anyone without that permission on the bound group. Separately, block break/place inside any
      Altar's live claim radius is gated by a new `BUILD` permission on the altar's owning group (or,
      for a player-owned altar, that specific player) — reuses the existing `AltarRadiusCalculator`
      rather than building a second territory system. This is functionally inert in real play until
      Altar Power/sacrifice exists (radius is always 0 today), but activates automatically once that
      lands.

- [x] Honor/reputation system + Blood Oath tier — a single global Honor score per player, moved by a
      `Ledger` listener whenever an oath resolves `FULFILLED` (gain) or `BROKEN` (loss, a larger swing
      than the gain), scaled by a rough severity score (clause count + escrowed currency) and amplified
      further for Blood Oaths in both directions; `VOIDED` stays neutral. A configurable minimum-Honor
      threshold gates swearing new Blood Oaths (default 0 - it only blocks players who've already gone
      Honor-negative). A Blood Oath breaking also applies a configurable temporary potion-effect
      "curse" debuff. Cosmetic title tiers ("Faithbroken"/"Oathkeeper"/etc., config-driven thresholds)
      are exposed via `/oathbound-debug honor info|adjust` - no chat-prefix/Oath-Board display yet since
      neither has a precedent or exists respectively. **Known limitation:** the domain model has no
      fault-attribution concept yet, so Honor/debuff effects apply to every party of the oath, not just
      whoever actually broke it - and `BROKEN` is still only reachable via the debug command (no
      automated "unmet deadline" detection exists). Both should narrow once a real breach-detection/
      reporting flow is built.
- [x] NPC Notary (rooted villager) + Sealing Table, and a lighter-weight async negotiation mailbox —
      `/oathbound-notary install <name>` spawns a rooted, invulnerable Villager; right-clicking it opens
      a small hub GUI to start a new named-party oath draft (type the counterparty's name in chat) or
      review oaths proposed to you, wherever they were sent from. The Sealing Table (right-click the
      configured block, a lectern by default) is a face-to-face shortcut straight to the draft prompt.
      Proposals now also respect a per-recipient pending-offer cap and auto-void (neutral, no Honor
      change) after a configurable number of days unanswered. **Scope note:** this deliberately does not
      implement the master plan's full `DRAFT → OFFERED → COUNTERED → ...` negotiation state machine —
      recipients still only Sign or Decline a proposal (the existing pending-oath board), not edit and
      counter-offer it. True counter-offering is left for a later pass.
- [x] Public Oath Board (regional + capital) — right-click the configured block (an oak sign by
      default) to open a hub: browse recent witnessed activity, bind the board to a group you're a
      member of (regional - shows postings where either party is a group member), or make it capital
      (unbound - shows everything). Postings aren't a separately stored feed; the feed GUI live-queries
      the Ledger each time it's opened, exactly like every other board screen in this plugin. Only
      *witnessed* oaths post (sealed, fulfilled, or broken - proposing/activating/voiding stay silent),
      which required adding an actual witnessing flow: the oath builder GUI now has an "Add Witness"
      button (type a name in chat) alongside its clause buttons, since nothing previously called
      `OathService.addWitness`. A witnessed Blood Oath breaking additionally broadcasts a server-wide
      message - the design doc's "special/dramatic formatting, possibly a broadcast" - while every other
      posting (sealed, fulfilled, non-blood breaks) is only visible by browsing a board, not broadcast,
      to avoid chat spam. **Not implemented:** "significant Honor tier crossings" as board postings -
      unlike oath state transitions, a tier crossing isn't reconstructable from the Ledger alone and
      would need its own persisted feed; left for a later pass. Altar desecration postings are out of
      scope until Altar desecration itself exists.
- [x] Altar sacrifice ritual (Power accrual), decay, vulnerability tiers, desecration outcomes,
      reconsecration cooldown, and claim nesting/overlap resolution — right-clicking a consecrated
      altar's barrel opens a chest-GUI sacrifice interface; only enchanted items count as artifacts,
      valued purely off their enchantment profile (base weight = a configurable scale divided by each
      enchantment's max level, so maxing out any enchantment is worth the same regardless of item type),
      summed per item with each repeated enchantment type within one sacrifice batch discounted by a
      configurable per-repeat multiplier to discourage volume-stuffing, and consumed permanently on
      confirm. Power is never stored as a raw counter - like radius, it's a live function recomputed
      from each altar's last-sacrifice baseline and a configurable "days full-to-empty" decay rate, so a
      single large sacrifice always outlasts the same total value drip-fed across several smaller ones
      (decay eats the gap between deposits). Three vulnerability tiers (Normal, Decaying, Critical) gate
      everything else: crossing into Decaying below a configurable warning threshold fires a login nudge
      to the owner; only below a configurable critical threshold does protection actually drop to zero
      and the barrel become interactable for an outcome - Destroy (a Desecration: Honor penalty for the
      breaker, a server-wide chat broadcast mirroring the existing Blood-Oath-break broadcast, and the
      altar record is deleted outright, requiring the barrel/capstone/candle structure to be physically
      rebuilt, not just re-funded) or Loot (XP orbs worth the most recent single sacrifice's value,
      configurable conversion rate, no Honor penalty by default). Any successful top-up - recovering
      from Critical or a fresh reconsecration - starts a configurable cooldown (baseline 5 minutes)
      during which protection and surface monster-spawn suppression both stay suppressed even though
      Power/radius already read as sufficient, closing the panic-deposit-mid-raid loophole. New-altar
      placement is blocked if it overlaps an existing altar of the same or smaller `GroupTier`
      (Individual < Company < Town < Region < Kingdom) but is explicitly allowed to nest inside a
      strictly larger one (federating under a liege); this check runs once, at consecration time, and a
      larger claim later shrinking from decay never retroactively invalidates anything already nested
      inside it. The separate, ongoing "which claim's rules apply here" build-gating lookup is unchanged
      in shape (still smallest live radius wins) but now tie-breaks by smallest `GroupTier` when radii
      match exactly. An Oath Board posting for decay warnings/desecration was considered and deliberately
      skipped, same reasoning as the Honor-tier-crossing gap noted above - not reconstructable from the
      Ledger, would need its own persisted feed; the chat broadcast covers the "dramatic" requirement on
      its own, and an ambient in-world signal is left for the existing Polish-pass roadmap bullet.
      Multiple altars per owner were already fully independent (separate Power/radius/decay/cooldown per
      record) with no extra work needed.
- [x] Villager Shops — thirteen `/oathbound-<role>` commands, one per vanilla `Villager.Profession` that
      actually trades, each spawning the same rooted/invulnerable Villager trick as the Notary but skinned
      for its role via `setProfession`. Right-clicking one opens a fixed buy/sell chest menu (not a
      per-instance builder) backed by an admin-edited list of items and prices per role in `config.yml`;
      transactions go through the existing `EconomyService`, the same native currency the rest of the
      plugin uses.
- [x] Bounty / Kill Contracts — the condition-engine hookup for `KillCountClause` itself first: an
      Oath's kill-count clause resolves once `DeathTracker` confirms the required death count for its
      target, the same machinery `Condition.DeathCount` already provided, since resolving its condition
      *is* fulfilling it (no execution side effect of its own, unlike Transfer/Escrow). Bounties
      themselves are deliberately **not** built on `Oath`/`ConditionEngine` - a new `bounty` package with
      its own `BountyService`/`BanishmentService` instead, since a bounty's victim never consents
      (Oaths require a PROPOSED→SEALED handshake), its reward recipient is whoever turns a head in rather
      than a party named at draft time, and per-head installment payout is fundamentally incremental,
      which `ConditionEngine`'s release-schedule model deliberately doesn't support. A Notary's hub gains
      a Bounty Board: quantity/group targeting (validated against current member count, capped there),
      heat-scaling Notary fees (live-computed like Altar Power, never persisted, decaying over a
      configurable window) with a hard 24h placement cap and a breach discount (approximated as "any
      oath between the pair went `BROKEN` recently," since the Ledger has no fault-attribution concept -
      same known limitation as Honor), and head-return fulfillment (a qualifying kill force-drops a
      tagged `PLAYER_HEAD`; turning it in at any Notary pays out, per-head for group contracts, with no
      kill-attribution tracking at all). A qualifying kill also starts or extends (never resets) a
      banishment sentence to a fixed, admin-built End pen (`/oathbound-debug banishment set-pen`),
      real-hours scaled by the bounty amount just paid and clamped to a min/max, stacking capped at a
      configurable maximum total duration; the pen holds the player across logout, and release
      auto-teleports them back to where they died, checked on a periodic sweep and on login for sentences
      that elapsed while offline. A group bounty's required quantity auto-decrements when a targeted
      member is flagged abandoned via a live `OfflinePlayer.getLastPlayed()` inactivity check (no new
      persisted flag). The same board also lists standing, repeatable, admin-authored **PvE contracts**
      (`config.yml`'s `pve-contracts`, not persisted - only per-player progress is) using native Bukkit
      kill-attribution instead, since there's no bounty-style attribution problem to sidestep for a
      server-owned economy tap. A one-time login notice (not every-login spam) tells a player they have a
      bounty on their head; `/oathbound-bounty list [player]` is the always-available pull command, and
      `/oathbound-bounty notify off` opts out of the notice.
- [ ] Polish pass: particle/sound effects, Notary flavor/skin system, full config-surface tuning

### Deliberately deferred (see master plan)

- Election Oaths — fully researched (condition-engine changes, a new `election` domain package,
  GUI/command/persistence surface) but deliberately deferred as too large a single pass; the
  implementation plan is saved in [`election-oaths-plan.md`](./election-oaths-plan.md) if revisited.
- Notary skin/flavor customization
- Per-relationship trust tracking (only a single global Honor score is planned)
- Release-oath hooks for reducing active banishment sentences
- Altar Power pooling across multiple altars
- Any siege/conquest mechanics beyond altar desecration

### Reach Goal
- Ceremony Designer: Bind agreements between a group and an individual using a special item (like a scepter) and right-click. Individual accepts if they say specified confirmation phrase.  Avoids time setting up contracts that interrupts roleplay, simply whip out the scepter and dub them on the spot and then they get the appropriate ownership/permissions for a knight (or whatever).

---

## Development

```bash
./gradlew test          # run the test suite
./gradlew build          # build the shaded plugin jar (runs tests first)
./gradlew runServer      # boot a local Paper test server with the plugin installed
```

The domain layer (`oath`, `group`, `economy`, `altar`, `contract`, `bounty` packages) has zero Bukkit
dependencies by design — it's plain, JUnit-testable Java. Bukkit-specific glue (commands, event
listeners, inventory GUIs, item (de)serialization) lives in its own packages (`command`, `listener`,
`gui`, `bukkit`) on top of that domain layer.
