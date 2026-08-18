# Oathbound Wargame — Repeatable Test Procedure

A wargame is a role-played stress test of the plugin's incentive structure: build a small cast of
player archetypes, narrate what they'd actually do given real Minecraft resource-time pacing and the
*current* config/code, and see where the mechanics produce outcomes nobody designed on purpose. Round 1
(recovered summary below) found real bugs this way and led directly to commit `464da58`. This doc makes
the procedure repeatable instead of living only in chat history, and keeps a running ledger so each new
round knows what's already open, patched, or ruled "working as intended."

## How to run a round

1. **Re-ground every time.** Read `docs/*.md` and `src/main/resources/config.yml` fresh — don't reuse
   numbers from memory or from a past round's write-up. Code changes between rounds; the findings ledger
   below is where continuity lives, not assumption.
2. **Build a small cast (4-6 archetypes).** At minimum: a cautious solo newcomer, a day-one pair forming
   a group together, a systems-literate veteran willing to read `config.yml` for fun (this is your
   informal "adversary" role — optimal play, not necessarily malice), and an admin/steward. An
   opportunist (PvP/economy-focused) is useful for bounty and mailbox mechanics. Use fresh names each
   round unless a specific round is deliberately testing continuity (e.g. "does this returning player
   remember the last raid").
3. **Narrate in day/week-numbered vignettes.** Every outcome must cite the real config key or code path
   that produced it — no invented numbers. If you can't point at the line, don't claim the behavior.
4. **Pace it with real Minecraft resource-time expectations.** Solo diamond gear: on the order of an
   hour or two. A working enchant setup (table + 15 bookshelves): another session. A full netherite
   loadout with rare enchants (Mending, Silk Touch, Fortune): realistically several real-world days solo,
   faster with a group pooling effort or villager trading. Use this to decide what a persona can
   plausibly afford to sacrifice/stake/spend at a given point in the timeline — don't hand-wave wealth.
5. **Verify anything that smells like a bug against the actual source before writing it down as one.**
   A wargame anecdote is only as good as the mechanic behind it — read the relevant `.java` file, don't
   just extrapolate from the docs. Docs can drift from code; the ledger entries below were each confirmed
   against source, not just inferred from `docs/*.md`.
6. **Close every round with "What the wargame actually surfaced."** Written as a systems reviewer, not a
   narrator: name the mechanic, say plainly whether it's a bug, a balance tradeoff, or working-as-intended,
   and suggest (don't mandate) a fix direction.
7. **Update the findings ledger.** Check off anything patched since the last round (cite the commit) and
   carry forward what's still open. A finding that's a clear bug (not a judgment call) is a good candidate
   to spin into its own implementation plan doc — see `fault-attribution-plan.md` for the template — rather
   than fixing it inline mid-wargame.

## Findings ledger

| Finding | First surfaced | Status | Notes |
|---|---|---|---|
| Fresh altars born at 0 Power (immediately Critical) | Round 1, pass 1 | **Patched** | `altar.starting-power: 150` grace baseline, commit `464da58` |
| Enchantment weight formula misread as overvaluing single-level enchants | Round 1, pass 1 | Not a bug | Corrected same session: single-level enchants (Mending, Silk Touch) hit max value on the first copy by design, not overvalued |
| Predatory `FULFILLED` oaths grant Honor to the exploiter too (fault-blind) | Round 1, pass 1 | **Open** | `fault-attribution-plan.md` currently scopes only the `BROKEN` side; a lopsided-but-"honestly"-fulfilled predatory oath isn't covered — flagged there as deliberately out of scope, needing its own design pass |
| Diplomacy `WAR` is bookkeeping-only unless `pvp.restrict-to-declared-wars` is enabled | Round 1, pass 1 | Working as intended, but confusing to players | Consider a clearer in-world signal when the flag is off |
| Sub-Region/Kingdom groups have no diplomatic agency (bandwagon incentive) | Round 1, pass 1 | Working as intended (documented) | `docs/diplomacy.md`: only REGION/KINGDOM-tier roots hold relations |
| Bounty payout goes to whoever turns in the head, not whoever landed the kill | Round 1, pass 1 | Working as intended (documented) | `docs/bounty-banishment.md` — deliberate, sidesteps kill-attribution entirely |
| Reconsecration cooldown skipped on fresh/re-consecration (`cooldownUntil = consecratedAt`, not `+cooldown`) | Round 1, pass 2 | **Open — reconfirmed against source** | `altar/Altar.java:45`, `listener/AltarConsecrationListener.java:72-73` |
| `welcome-pact` (any `CustomFlagClause`-only oath) auto-`FULFILLED`s instantly — free repeatable Honor | Round 1, pass 2 | **Open — reconfirmed against source** | `oath/ConditionEngine.java:98-99,142-145` — `CustomFlagClause` never sets `allAutoResolvableAndDone = false` |
| Rarity multiplier widens rich/poor altar gap over the long run | Round 1, pass 2 | Working as intended, flagged to watch | `altar.enchantment-rarity-multiplier` |
| Fault attribution missing for `BROKEN` oaths (Honor/curse hits every party) | Round 1 (both passes) | **Open, plan parked** | `fault-attribution-plan.md` |
| Free mailbox-spam DoS: proposing an oath costs nothing, `pending-offer-cap-per-player` is per-recipient | Round 2 | **Open — new, verified against source** | `gui/OathBuilderListener.java:162-174`; a single trivial `CustomFlagClause` oath satisfies the "at least one clause" check for free, 10 of them lock out a recipient's inbox for up to `negotiation-expiry-days: 7` |

## Round history

### Round 1 (undated — recovered from prior session transcript, not originally saved to the repo)

Two passes, cast: Juniper (solo, cautious), Doran & Ash (day-one Company), Talia (systems-literate
veteran), the Steward (admin), Wren (a fresh newcomer added for pass 2). Pass 1 tested pre-`464da58`
code and found the zero-Power altar hole and the predatory-Blood-Oath Honor gain. Pass 2 re-tested after
the `464da58` patch specifically to check whether the fixes held up, and surfaced the reconsecration
cooldown bug and the `welcome-pact` auto-fulfill faucet as new behavior exposed by the fix itself. Full
narrative text lives only in that session's transcript, not in this repo; the findings above are the
durable output. `fault-attribution-plan.md` is the direct design artifact of this round.

### Round 2 — 2026-08-18 — fresh cast, current logic

**Cast:** Fenn (cautious newcomer), Marisol & Cade (day-one Company, "Marisol & Cade's Trading Co."),
Osric (systems-literate veteran), the Warden (admin/steward, runs an NPC greeter script at the capital
gate that hands out `welcome-pact`), Priya (unaligned opportunist).

**Day 1.** Fenn spawns at the capital gate and is greeted by the Warden's script, which hands him a
`welcome-pact` charter and walks him through the confirm phrase. It fulfills mechanically the instant
he says "i do" — `ConditionEngine.tickOne` sees a single `CustomFlagClause`, which is `continue`d past
without ever setting `allAutoResolvableAndDone = false`, so the oath auto-`FULFILLED`s on the very next
tick with no waiting period. Fenn gets +10 Honor (`honor.fulfill-gain-base`). So does the Warden — the
oath's parties are `[initiatorRef, targetRef]` (`CeremonyChatListener.java:205`), and the Warden is the
initiator on every single greeting. By the end of Day 1 the script has greeted eleven newcomers; the
Warden's Honor is already +110, for standing at a gate and clicking confirm eleven times.

Marisol & Cade consecrate their Company's altar the same afternoon: `starting-power: 150` puts them
straight at `NORMAL`, radius `4.0 × 1.25 × √150 ≈ 61 blocks` — a real, if modest, claim from hour one.

**Day 3.** Osric, poking through `config.yml` the way he does most days, notices the Warden's Honor
climbing suspiciously fast for someone who hasn't sealed a real oath all week, and works out why. He
says nothing yet — free Honor is useful to know about before it's useful to report.

Priya tests something else: she opens ten trivial `CustomFlagClause` drafts ("I acknowledge Priya is
very cool") and proposes all ten at Cade, one at a time. `OathBuilderListener.proposeOath` only checks
`oath.clauses().isEmpty()` — a single no-stakes flag clause clears that bar for free — and the pending
cap (`notary.pending-offer-cap-per-player: 10`) is counted per *recipient*, not per sender. By the tenth
proposal Cade's mailbox is full. Any real offer — a trading partner, a genuine alliance feeler — now
bounces with "they already have too many pending oath offers" until Cade manually declines Priya's junk
or seven days pass (`notary.negotiation-expiry-days`). Priya spent nothing to do this.

**Week 1.** Cade, irritated, wants to hit back — literally. He places a bounty on Priya. `fee-base: 100`,
no existing heat on her, no recent breach between them, so it's the full fee, no discount. He can afford
it; Marisol's altar income (villager shop sales through the mason NPC — cobblestone at 1 coin/unit) has
been covering incidentals. The bounty lands two days later when Osric, unaffiliated with either side,
happens to kill Priya in an unrelated skirmish and takes her head. He turns it in at the nearest Notary —
`BountyService.turnInHead` pays *whoever holds the head*, not whoever placed the winning blow in some
attribution sense (there was no contest here, but the mechanic is the same one Round 1 flagged: kill
attribution was never the point). Osric pockets Cade's bounty for a fight Cade didn't throw a single hit
in.

Priya, now serving a banishment sentence (`bountyAmountPaid / 50`, clamped `[1, 72]` hours — Cade's
bounty was modest, so this lands well under the cap, a few hours only), has time to think about softer
targets. Fenn, still Honor `0 → Unproven`, looks like one.

**Week 2.** Osric, richer and slightly bored, runs the classic play: he proposes a Blood Oath to Fenn —
"three trials to prove yourself worthy of my mentorship" — structured so that "success" mechanically
means Fenn hands over his best gear and does Osric's fetch quests. Fenn, new and flattered to be
noticed, signs. All three trials resolve `FULFILLED`. `OathSeverity` scores it by clause count and
escrowed currency, `HonorCalculator.fulfillGain` doesn't know or care that the terms were lopsided in
Osric's favor — both of them gain Honor, Osric more than Fenn did (his cumulative oath count is higher),
and nothing about the Ledger records that this was extraction dressed as mentorship. This is the exact
shape Round 1 flagged and `fault-attribution-plan.md` explicitly declined to cover (see its "lopsided-
but-honestly-fulfilled oaths" exclusion) — still true, still open, now with a second independent
confirmation.

Meanwhile Marisol & Cade's altar, untouched since Day 1, has been decaying against its 150 baseline for
two weeks — long since past both `decaying-threshold: 100` (crossed on day ~1.7) and `critical-threshold:
10` (crossed on day ~4.7, `decay-days: 5`). It's sat Critical, raidable, for over a week and nobody's
touched it — a quiet reminder that "Critical" doesn't mean "imminent," it means "open," and an
un-interesting claim can just sit there unlooted because nobody's looking.

**Week 3.** Osric finally does loot it (no reason not to — `loot-honor-penalty: 0`), then, testing
something Round 1's pass 2 flagged and nobody's fixed, breaks the barrel outright a minute later
(Desecration: `desecration-honor-penalty: 50` against him, server broadcast, altar record deleted) and
immediately rebuilds barrel + capstone + candle on the same spot. `AltarConsecrationListener` constructs
a brand-new `Altar` with `cooldownUntil = consecratedAt` (`Altar.java:45`) — there's no offset added for
the reconsecration-cooldown case, unlike the sacrifice-top-up path (`applySacrifice` correctly sets
`cooldownUntil = now.plus(cooldown)`). `cooledDown(now)` is true the instant the candle goes down. Osric's
freshly-placed altar reads full `NORMAL` protection immediately — the exact panic-response window the
300-second cooldown (`altar.reconsecration-cooldown-seconds`) exists to close, skipped entirely because
this is a *new* altar object, not a top-up of an old one. He's taken Marisol & Cade's spot for himself
in under two minutes, penalty-free the moment protection re-engages.

Osric finally does mention the Warden's Honor faucet — not out of principle, but because he wants the
Warden's attention on Marisol & Cade's complaint instead of on him.

## What the wargame actually surfaced

- **`welcome-pact`, and any oath shaped like it, is a free Honor faucet for both parties, not just the
  recipient.** `ConditionEngine` never blocks on a `CustomFlagClause`, so a single-clause oath resolves
  the instant it activates. This isn't `welcome-pact`-specific — *any* admin-authored template with only
  flag clauses has the same property, and the initiator (whoever hands it out) benefits every time, not
  just the target. Fix direction: either give `CustomFlagClause` a minimum dwell time before auto-
  resolving, or exclude system/ceremony-initiated oaths with no real clauses from Honor scoring entirely.
- **Proposing an oath is free, and the pending-offer cap is a per-recipient inbox lock, not a spam
  guard.** Ten trivial proposals from one sender, at zero cost, fill a target's queue for up to
  `negotiation-expiry-days` (7 days) and block real offers arriving from anyone else. Fix direction: cap
  pending *outgoing* proposals per sender-recipient pair (e.g. 1-2), separately from the existing
  per-recipient total cap.
- **The reconsecration cooldown only protects the top-up path, not fresh/re-consecration.** `Altar`'s
  5-argument constructor sets `cooldownUntil = consecratedAt` with no offset, while `applySacrifice`
  correctly offsets by the configured cooldown. Anyone (owner or raider) who destroys a Critical altar and
  immediately rebuilds gets instant full protection, defeating the exact loophole the cooldown exists to
  close — and, as this round shows, it doubles as a way for someone other than the original owner to
  claim the spot with an unbeatable head start. Fix direction: `AltarConsecrationListener` should pass
  `Instant.now().plus(reconsecrationCooldown)` as the initial `cooldownUntil`, same as the sacrifice path.
- **Lopsided-but-"honestly"-fulfilled predatory oaths still mint Honor for the predator.** Reconfirmed
  from Round 1: `fault-attribution-plan.md` deliberately doesn't cover this case (only `BROKEN`
  attribution). Worth its own design pass if it's a priority — noted there already, still true.
- Bounty payout-to-holder-not-killer and diplomacy-without-PvP-gating are both reconfirmed as working
  exactly as documented — not new information, just still true and still worth a player-facing callout
  somewhere (a tooltip, a Notary hint) so it doesn't read as a bug to someone who hasn't read the docs.

Three concrete, source-verified bugs came out of this round (Honor faucet, mailbox DoS, reconsecration-
cooldown bypass) — all small, localized fixes. Good candidates for a single patch pass, the same shape as
`464da58`, whenever that's wanted.
