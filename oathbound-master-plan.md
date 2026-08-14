# Oathbound — Master Design Plan

A Paper (Minecraft server) plugin for diegetic, mechanically-binding agreements between players. Built for a fantasy-kingdom roleplay server. Fully native/in-house — **no external plugin dependencies** (no Vault, no LuckPerms, no external claim plugins). Oathbound owns its own economy, permissions, and territory systems.

## Design Pillars

- **Diegetic first.** Every mechanic should have an in-world, roleplay-legible representation — a book, an altar, an NPC, a dropped head — not just a slash command and a database row.
- **The plugin is the source of truth.** Physical/visual representations (contract books, barrels) are either the actual data store (Altar) or cosmetic proof of state the plugin already owns (contract books) — never a second copy that can desync.
- **Consequences are real but bounded.** Breaking oaths, losing territory, and getting bountied all have escalating costs, but every escalating system (banishment, bounty heat, altar decay) has a cap or cooldown so nothing spirals unbounded.
- **Reuse the same primitives everywhere.** Ownership transfer, escrow, conditions, and reputation are generic systems. Elections, bounties, and altars are *built on top of* those primitives, not separate subsystems.

---

## 1. Core Oath System

### Lifecycle

```
DRAFT → PROPOSED → SEALED → ACTIVE → FULFILLED / BROKEN / VOIDED
```

- **DRAFT** — private, being edited by its creator, not visible to the counterparty yet.
- **PROPOSED** — sent to a counterparty (via Sealing Table face-to-face, or async via a Notary — see §6). Can be `COUNTERED` (spawns a new linked draft), `ACCEPTED` (→ SEALED), `DECLINED`, or it can `EXPIRE`.
- **SEALED** — both parties (and optional witnesses) have signed. Binding. Contract book issued to both parties as a portable receipt (cosmetic — the plugin's Oath record is the real state).
- **ACTIVE** — sealed and its conditions are now being tracked/ticked (death counts, timers, payment watchers, etc).
- **FULFILLED** — all clauses resolved successfully. Triggers Oath Board posting (if witnessed) and Honor gain scaled by oath stakes/severity.
- **BROKEN** — a clause's terms were violated or a time-boxed condition expired unmet. Triggers configured breach resolution, Honor loss (amplified if Blood Oath tier), and Oath Board posting.
- **VOIDED** — mutually agreed cancellation. Neutral — no Honor change for either party.

### Data model

**Oath**
- `id: UUID`
- `parties: List<PlayerRef>`
- `witnesses: List<PlayerRef>` (optional)
- `clauses: List<Clause>`
- `state: OathState`
- `bloodOath: boolean`
- `createdAt`, `sealedAt`, `resolvedAt`
- `negotiationChain: NegotiationRef` (links back to the offer/counter-offer history that produced this oath, if any)

**Clause** (tagged union)
- `TransferClause` — `target: PlayerRef | ProtectionGroupRef`, `subjectGroup: ProtectionGroupRef` (the group whose `owner` field gets reassigned), `condition: Condition`
- `EscrowClause` — `depositor: PlayerRef`, `recipient: PlayerRef`, `items: SerializedItemStack[]`, `currency: Map<CurrencyType, long>`, `releaseSchedule: List<(fraction, Condition)>` (supports partial releases, e.g. 3 up front / 7 on fulfillment)
- `CustomFlagClause` — `text: String`, no mechanical effect. Pure RP record (platform promises, personal vows).
- `KillCountClause` — see §8 (Bounty/Contracts)

**Condition** (tagged union)
- `Immediate`
- `TimeElapsed(duration)`
- `DeathCount(target: PlayerRef | ProtectionGroupRef, count: int)`
- `PaymentReceived(amount, currency)`
- `VoteTally(ballotRef)` — see §9 (Elections)
- `ManualConfirm(requiredParty: PlayerRef)`
- `Compound(operator: AND|OR|NOT, subconditions: List<Condition>)`

### Ledger

Append-only log of every Oath state transition. Backs the Public Oath Board (§7) and any future audit/history features. Should be the single source that both the Board and any `/oathbound history` command query.

---

## 2. ProtectionGroup

The universal ownership/permission primitive. Used for chest/door/claim gating, factions, kingdoms, dynasties, companies — everything.

- `id: UUID` — permanent identity, independent of display name (supports rename without losing continuity of ownership/history).
- `name: String`
- `owner: PlayerRef | ProtectionGroupRef` — **tagged union.** This is what enables dynastic succession: a territory can be owned by "the ruling house" (a group), and when that house's own leadership changes, the territory's owner reference never has to change — only the house's internal state does.
- `members: List<Member>`, each with a `role: Role`
- `roles: List<Role>` — group-defined, each with a `rankWeight: int` and a permission set: `canOpenContainers`, `canInvite`, `canVote`, `canBeCandidate`, `canTransferOwnership`, `canAcceptOnBehalf`, etc.
- `tier: Individual | Company | Town | Region | Kingdom` — used for Altar radius scaling (§10) and nesting resolution (§10).

### Ownership resolution

- **Live-walk the ownership chain** on every permission/ownership check, with a **fixed depth cutoff** (e.g. 10 hops).
- **Cycle prevention enforced at write-time** — reject any ownership assignment that would make a group its own ancestor. Never attempt cycle detection at read-time.
- If chain resolution exceeds the depth cutoff, fail safe: treat as unresolved, log a warning, surface as a data-integrity flag to admins rather than silently granting or denying access.
- **Group-targeted transfer clause acceptance**: does **not** require the group's fully-resolved terminal owner specifically — any member holding sufficient rank/permission (`canAcceptOnBehalf`) on the immediately-relevant group can finalize on the group's behalf (a steward can seal a deal for the crown without the monarch being online).

---

## 3. Currency & Economy

- Fully native, plugin-owned per-player balance(s).
- Support for multiple named currencies if desired for flavor (e.g. "gold crowns," "silver marks") — single currency is a fine MVP default.
- No external economy plugin dependency.

---

## 4. Escrow

Admin-controlled, **fully virtual** holding system for items/currency staked as part of an oath's terms. Deliberately *not* backed by any real block or chest.

- Backing data: serialized `ItemStack[]` (via Paper's native `ConfigurationSerializable` support — no custom NBT encoding needed) + a currency balance, stored in the Oath/Clause record.
- Presented to players via a **chest-shaped inventory GUI** for diegetic familiarity, even though nothing is bound to a real-world block. This gets the "feels like a chest" experience without any of the downsides of real storage (no permalocking, no world footprint, no griefing surface, remotely reviewable via a Notary).
- Supports **partial release schedules** (e.g., recipient gets 3 diamonds up front, 7 more on fulfillment of remaining terms).
- **Abandonment handling**: once a release condition is met, an **expiry timer** starts (e.g. 30 real days) before the claimable portion resolves per a configured abandonment policy — return to depositor, escheat to the territory/group that owns the brokering Notary, or split per the oath's breach rules.
- A `/oathbound claim` command and/or an "unclaimed escrow" tab in the Notary GUI surfaces anything waiting for the player, plus a login nudge.

**Explicitly separate from ProtectionGroup ownership transfer.** Escrow moves items/currency; TransferClause moves access rights. An oath can use either or both (e.g., "pay 10 diamonds into escrow" + "transfer chest access" in the same contract).

---

## 5. Reputation (Honor)

- **Single global score per player.** No per-relationship tracking (deliberately simplified).
- Moves on:
  - Oath fulfilled → increase, scaled by the oath's stakes/severity (Blood Oaths matter more).
  - Oath broken → decrease, larger swing than the corresponding gain, amplified further if Blood Oath tier.
  - Oath voided by mutual consent → neutral, no change (lets people back out gracefully without punishment).
  - Desecration of an Altar (§10) → Honor penalty for the desecrator.
- Drives **cosmetic title tiers** (e.g. "Oathkeeper" / "Faithbroken") displayable in chat prefixes or on the Oath Board.
- Drives **gating thresholds** — e.g. minimum Honor required to be offered a Blood Oath, or to hold office via an Election Oath.

---

## 6. Blood Oaths

An oath-level **tier flag**, not a clause type — any oath can be sworn "in blood" at seal time.

- On breach: real mechanical penalty, configurable — temporary debuff, forced item drop, a curse-flavored potion effect, a visible "Marked" status other players can see.
- Amplified Honor penalty on breach relative to a normal oath.
- Special treatment on the Oath Board — most dramatic possible announcement/formatting, possibly a server-wide broadcast.
- Consider a **swearing cost** at seal time (currency, an item burned/consumed, etc.) to prevent Blood Oaths from being used trivially.

---

## 7. NPC Notary (MVP scope)

- Standard `Villager` entity.
- Custom display name (settable by whoever installs it — typically the territory owner).
- **Invulnerable** and **rooted to a fixed location** — no wandering AI.
- Right-click opens the same chest-menu contract builder used at a Sealing Table.
- Skin/profession/flavor-text customization ("The Imperial Judge," "The High Priest") is **explicitly deferred post-MVP.**

### Async negotiation

Players don't need to be online simultaneously to negotiate.

- **Mailbox is scoped to the player, not the physical Notary or a group.** A player can review pending offers addressed to them at *any* Notary they interact with — correspondence follows the player identity. (Groups still participate as transfer targets/sources, but the correspondence itself is always between individual players — avoids needing commands for a "corporate representative" to check a mailbox on a group's behalf.)
- State machine layered ahead of the Oath lifecycle, feeding into `PROPOSED`:

```
DRAFT → OFFERED → COUNTERED → OFFERED (reversed)
                → ACCEPTED → SEALED
                → DECLINED → (dead)
                → EXPIRED  → (dead)
```

- Each counter-offer links to its predecessor — a visible paper-trail of correspondence in the Notary UI, not just a mutable form.
- **Cap on pending offers per player** to prevent mailbox spam. Flat limit for MVP; room to raise it later as an earnable perk (Honor-tier bonus, paid Notary upgrade — decide later).

---

## 8. Escrow Vault (Legal-Seat Infrastructure)

- A **building/room** a territory owner constructs and registers as their region's legal seat — houses a Notary NPC, nothing more mechanically.
- **Purely diegetic infrastructure.** No items are physically stored here — Escrow (§4) is fully virtual. The Vault's role is narrative/functional legibility: it's where deals get brokered, not where goods sit.

---

## 9. Public Oath Board

- A lectern/sign/book-frame structure querying the Ledger (§1) for recent activity.
- Posts on:
  - Oath sealed (**witnessed oaths only** — respects privacy of unwitnessed deals)
  - Oath fulfilled
  - Oath broken
  - **Blood Oath broken** — special/dramatic formatting, possibly a broadcast
  - **Altar desecration** (§10) — similarly dramatic
  - Significant Honor tier crossings (into "Oathbreaker" territory, or up into a renowned tier)
- Supports **regional boards** (scoped to one ProtectionGroup's business) and a **capital/aggregate board** (everything), so small servers/groups aren't spammed with unrelated activity.

---

## 10. Territory — Altars

Territory claiming, fully diegetic, centered on a physical structure.

### Structure

- Built on top of a **barrel**, with cosmetic consecration requirements around/atop it (candles, amethyst, whatever fits the resource pack) to make an Altar visually unmistakable.
- Right-clicking (while properly consecrated and bound to a `ProtectionGroup`) opens a **sacrifice interface.**

### Sacrifice & valuation

- Only **enchanted items** count as artifacts — value is derived entirely from enchantments (level × per-enchantment weight, summed across the item, with diminishing returns on repeated enchantment types within one sacrifice to discourage volume-stuffing).
- Item type is irrelevant to value — an enchanted book and an enchanted tool with the same enchantment profile are worth the same.
- **Deposited artifacts are consumed permanently** — converted into an internal **Altar Power** value. This is an intentional economic sink: the barrel does not retain retrievable inventory under normal operation.

### Decay

- **Flat decay rate**, configured as "days of neglect to go from full to empty" (tunable per server; discussed baseline ~5 minutes was for the *reconsecration cooldown*, not decay — decay itself is measured in days).
- Because **claim radius is a non-linear (diminishing-returns) function of current Power**, a single large sacrifice buys disproportionately more time above the danger threshold than the same total value spread across many small deposits — big, meaningful sacrifices are explicitly rewarded over drip-feeding.

### Radius & tier scaling

- Radius is a **live function of current Power**, recalculated continuously as Power decays — the claimed border visibly recedes in real time.
- **The same Power value yields a larger radius the higher the owning group's tier**: `Individual < Company < Town < Region < Kingdom`. This is a deliberate incentive for communities to federate/pledge allegiance to a liege — banding together protects a larger hinterland than staying solo for the same upkeep effort.
- **Monster spawning is suppressed on the surface** within a claimed (non-critical) radius — a tangible safety payoff for maintained territory, beyond the legal/RP framing.

### Vulnerability tiers

1. **Normal** — full radius, full protection (chest/door/claim gating active as usual).
2. **Decaying** — radius actively shrinking as Power drops. Warning triggers as Power approaches the critical threshold (Oath Board post, login notice, ambient in-world signal — "the sacrifice grows cold...").
3. **Critical** (Power below a small threshold) — protection radius drops to **zero**; the claim becomes flavor-only and the land is fully raidable like unclaimed territory. **Only at this point does the barrel itself become openable.**

### At critical — barrel outcomes

- **Destroy it** → triggers a **Desecration event**: Honor penalty for the destroyer, dramatic Oath Board broadcast, Power hard-reset to zero (requires full reconsecration, not just a top-up).
- **Loot it** → drops **XP orbs equivalent to the Power gained by the most recent completed sacrifice** (not lifetime total), conversion rate configurable. **No Honor penalty by default** (configurable per-server if admins want to discourage even this).
- **Leave it** → territory remains flavor-only/unclaimed-in-practice until the owning group tops it up again.

### Reconsecration cooldown

- After any top-up (whether recovering from critical, or a fresh reconsecration post-desecration), there is a **flat cooldown period (baseline: 5 minutes)** before protections re-engage, even if Power itself already registers as sufficient. Prevents an owner from panic-depositing mid-raid to instantly nullify an in-progress siege.

### Nesting & overlap

- Overlap legality is checked **smallest-scope-first**: `Individual → Company → Town → Region → Kingdom`.
- A larger entity's claim shrinking (via decay) **never retroactively invalidates** a smaller entity's claim nested within it — validation only runs at the moment a *new* claim is placed, against the current state of larger claims at that instant. Keeps player-scale experience stable even as kingdom-scale borders fluctuate.

### Multiple altars per group

- **Independent, not pooled.** Each altar tracks its own Power/radius/decay separately. A group with several territories manages upkeep per-altar; losing one doesn't directly threaten the others.

---

## 11. Election Oaths

Built entirely on existing primitives — no separate election engine.

1. **Candidacy** — a `CustomFlagClause` oath per candidate (platform promises, no mechanical effect, but a durable public record — useful later for judging whether an elected ruler kept their platform).
2. **Ballot** — a time-boxed oath; eligible voters filtered via `role.canVote` on the relevant `ProtectionGroup` (a monarchy might grant this only to a "Duke" role; a democracy grants it broadly by default).
3. On the `TimeElapsed` condition firing, the plugin tallies votes (`VoteTally` condition) and the winning candidate's `TransferClause` reassigns the group's `owner` field — the same mechanism as any other ownership transfer.
4. Losing candidates' escrow (if they staked anything to run) auto-returns.

---

## 12. Bounty / Kill Contracts

Two front-ends over one underlying `KillCountClause` system: PvP bounties and PvE contracts (server economy tap).

### Targeting & quantity

- Every kill contract has an explicit **quantity** field.
  - Individual target → implicitly `1`.
  - Group target → placer must specify a quantity, validated at creation to be **≤ current group member count.**
  - PvE target (mob type) → arbitrary quantity, no group logic involved.
- If a targeted group member's account is later flagged **abandoned**, the contract's required quantity **auto-decrements by 1** — prevents a long-running group bounty from becoming permanently unfulfillable due to inactive members padding the original count.

### Notification

- On login, a player with an active bounty on their head gets notified (flavor-text driven, same style as Oath Board postings). Avoid forcing this on *every* login while active — a one-time notice plus a toggleable "show active bounties" command is preferable to session-spam.

### Fulfillment — head-return mechanism

- On a qualifying kill, the victim **force-drops a player head** (native Bukkit `PlayerHead` with owner metadata — no custom item needed).
- **Returning the head to a Notary is the fulfillment action** — modeled as a standard item-transfer/delivery, reusing existing escrow-payout machinery. This deliberately sidesteps kill-attribution/last-hit tracking entirely: whoever physically holds and turns in the head gets the reward.
- For group contracts with quantity > 1: **confirm before implementation** whether turning in one head partially fulfills (reward scales per-head, contract stays open) or the contract requires all heads collected before any payout — affects escrow payout structure (lump sum vs. installment).

### Guardrails — heat-scaling fees

- Placing a kill contract costs a **Notary fee**, separate from the reward, which **scales with how "hot" the target already is** (how many active/recent bounties already exist on them). Naturally self-limits pile-ons — the first bounty is cheap, stacking a fifth is prohibitively expensive.
- **Heat decays over time**, making this a soft dynamic cap rather than a hard rule.
- **Hard cap** on number of contracts a single player can place per 24 hours, independent of heat cost.
- **Discount**: if the target has broken an Oath with the placer within a configurable recent window, the placer receives a fee discount — ties bounty placement back into the reputation system, giving mechanical weight to "they wronged me, I have standing."

### Banishment ("Prison")

- On a qualifying bounty kill, the victim is teleported to a designated, purpose-built **End banishment pen** (fixed, contained, safe from Endermen/void — not just "dropped in the End").
- Duration is **real-world hours, scaled by the bounty amount paid** — a config curve (linear baseline: `hours = bountyAmount / scalingFactor`) with a **min and max cap.**
- **Timer runs in real time regardless of online status** — logging off does not pause or avoid the sentence.
- **Stacking with a hard cap**: a subsequent bounty death while already serving a sentence **extends** the remaining time (does not reset), but the total duration can never exceed a configured maximum — further stacking beyond the cap is absorbed/ignored until time served brings it back under.
- Auto-teleport back to the player's pre-banishment (or a designated return) location when the timer expires — checked on a schedule and on login if the timer already elapsed while offline.
- The pen should support basic survival (not a bare box) — fits "held for ransom/exile" RP framing better than pure punishment-by-boredom.
- **Future hook (not MVP-required):** a "release oath" — an ally negotiates with the bounty-placer via the normal Oath system to shorten or forgive remaining sentence time. Falls out naturally from the existing Oath system with no new mechanics, worth keeping in mind when designing the banishment data model so it's easy to hook a Clause into reducing sentence time later.

### PvE Contract NPCs (economy tap)

- A Contract NPC (or a Notary's alternate menu tab) offers standing, repeatable, admin-authored `KillCountClause` oaths against mobs (kill 20 spiders, 5 zombies, etc.) for currency/item rewards.
- Modeled as a **system-owned Oath** (the "other party" is the server/admin rather than a player) — reuses existing escrow payout and progress-tracking machinery rather than a parallel system.

---

## Build Order

1. Core Oath data model + lifecycle state machine + Ledger
2. Native currency + ranked `ProtectionGroup` (owner as player-or-group ref, live-walk resolver with depth cutoff)
3. Chest-GUI contract builder (immediate-only clauses first)
4. Condition engine: manual-confirm, time-elapsed, death-count, payment-received, vote-tally
5. Virtualized Escrow system + claim/expiry/abandonment handling
6. Chest/door/claim access gating tied to `ProtectionGroup`
7. Honor/reputation system + Blood Oath tier
8. NPC Notary (rooted villager) + Sealing Table as alternate entry point
9. Async negotiation state machine (player-scoped mailbox, offer cap)
10. Public Oath Board (regional + capital variants)
11. Altars: sacrifice/valuation, decay, tiered radius, vulnerability tiers, desecration, reconsecration cooldown, nesting resolution
12. Election Oath template (built entirely on existing primitives)
13. Bounty/Kill Contracts: quantity/group targeting, head-return fulfillment, heat-scaling fees, banishment (with stacking cap)
14. Polish pass: particle/sound effects on resolution, Notary flavor/skin system, offer-cap bonuses, config tuning for all curves/thresholds

## Explicit Config Surface (tune-later, don't hardcode)

- Altar decay rate (days full→empty), radius formula constants per tier, critical threshold, XP-loot conversion rate, reconsecration cooldown duration
- Bounty heat decay rate, fee-scaling curve, 24h placement cap, breach-discount window/amount
- Banishment hours-per-bounty-amount curve, min/max cap, stacking cap
- Escrow abandonment expiry duration and resolution policy
- Honor gain/loss magnitudes per oath severity tier, Blood Oath multiplier
- Notary pending-offer cap per player

## Deliberately Deferred (post-MVP)

- Notary skin/flavor customization system (custom professions, per-territory greeting text)
- Per-relationship trust tracking (only global Honor for now)
- Release-oath hooks for reducing active banishment sentences
- Altar Power pooling across multiple altars (currently independent by design, but worth flagging as a possible future group-level aggregate view)
- Any siege/conquest mechanics beyond what Altar desecration already provides
