# Fault attribution — researched implementation plan (parked)

**Status: designed, not implemented.** Researched in depth against the actual codebase but deliberately
parked rather than built in the same pass as the wargame follow-ups that prompted it, so it can be
picked up cleanly on its own. See `README.md`'s Roadmap `### Done` section for the pointer back here.

## Context

The gap is named three times already in this codebase, verbatim, without ever being designed:
`README.md`'s Honor Roadmap bullet ("the domain model has no fault-attribution concept yet, so Honor/
debuff effects apply to every party of the oath, not just whoever actually broke it"), its Bounty
Roadmap bullet (the breach discount is "approximated as 'any oath between the pair went BROKEN
recently,' since the Ledger has no fault-attribution concept - same known limitation as Honor"), and
`HonorLedgerListener`'s own class javadoc. A wargame wrote a concrete scenario for it: a `BROKEN` oath
today debits every party's Honor by the same amount and applies the Blood Oath curse to every party,
regardless of which of them actually violated it.

**Explicitly out of scope, decided up front:**
- **Automated "unmet deadline" breach detection.** A separate, already-documented gap
  (`README.md`: "`BROKEN` is still only reachable via the debug command - no automated ... detection
  exists"). This plan only makes *manually-declared* breaches fault-aware; it does not add a sweep that
  decides an oath has been broken on its own. The two gaps compound (a real breach-reporting flow would
  presumably want to *also* supply a faulted party), but they're separable and this plan only tackles
  attribution.
- **Lopsided-but-honestly-fulfilled oaths.** A `FULFILLED` oath where one party structured the deal to
  their own clear advantage isn't a fault question - nobody violated anything, the losing party agreed
  to the terms. If that's ever worth addressing it needs its own design pass (e.g. a reciprocity
  heuristic scoring whether an oath's clauses benefit both parties), not fault-tagging, and is
  deliberately not part of this plan.
- **A player-facing dispute/report flow.** Today only an admin can call `breach()` via the debug
  command; this plan keeps that constraint and just makes the debug command fault-aware. Letting a
  wronged player themselves flag fault (with or without admin approval) is a natural follow-on, noted
  here as future work, not designed.

**Key design decisions, verified against the actual code:**
- `LedgerEntry` (`oath/LedgerEntry.java`) already carries an `actor: PlayerRef` field, but it means
  "whoever called the transition method" - for `BROKEN`, that's always been whichever admin ran
  `/oathbound-debug oath breach`, never a real signal about which *party to the oath* was at fault.
  Fault needs to be a new, separate field, not overloaded onto `actor`.
- `ledger_entries` is stored as a single Gson JSON blob per row (`data` column - see
  `SqliteDataStore.java:264-267`, `appendLedgerEntry`/`loadLedgerEntries`/`loadRecentLedgerEntries`, all
  `gson.fromJson(rs.getString(1), LedgerEntry.class)`), exactly like `Altar`/`ProtectionGroup`. **A new
  field on `LedgerEntry` needs no migration** - Gson round-trips an added record component
  automatically (the same reasoning already verified and documented in `election-oaths-plan.md` for
  `Condition.VoteTally`'s added component). `PlayerRef` is already Gson-serialized inside `List`s
  elsewhere (`Oath.parties()`) without issue; a `Set<PlayerRef>` isn't a Gson *map-key* case (the
  pitfall `election-oaths-plan.md` flags for `Election.votes` is specific to `Map` keys, not `Set`/
  `List` elements), so no `GsonFactory.java` change is expected either - worth a quick round-trip unit
  test to confirm rather than assuming, same discipline the election plan applied.
- `OathService.breach(Oath oath, PlayerRef actor)` (`oath/OathService.java:102-104`) is a two-line
  wrapper over the private `transition(oath, OathState.BROKEN, actor)`, which itself just calls
  `ledger.record(oath.id(), current, target, actor)` (`Ledger.java:20-25`). Both are small, easy to
  extend with a fault-aware sibling without touching the existing symmetric-transition machinery
  `propose`/`seal`/`activate`/`fulfill`/`voidOath` all still share.
- `command/OathboundDebugCommand.java`'s `"breach"` case currently rides the shared generic
  `oathTransition(sender, args, OathService::breach)` dispatcher (line ~230) alongside every other
  transition (`propose`/`seal`/`activate`/`fulfill`/`void`), via a private `OathTransitionAction`
  functional interface (`(OathService, Oath, PlayerRef) -> void`, line ~360). `breach` needs extra,
  optional trailing arguments the others don't, so it needs to come out of that shared dispatcher into
  its own method - the same shape `oathCreate`/`oathAddDiplomacy`/`oathConfirm` already use for
  commands with their own argument parsing.
- `bounty/HeatCalculator.recentBreachBetween` (`bounty/HeatCalculator.java`) checks "did *any* oath
  between the placer and the target resolve `BROKEN` within the discount window" - which, without fault
  data, would currently discount the fee even when the *placer themselves* broke the oath. It reads
  `Oath.state()`/`parties()`/`resolvedAt()` off `Collection<Oath> allOaths` - it has no access to
  *why* an oath broke, because that lives on the `LedgerEntry` for the `BROKEN` transition, not on the
  `Oath` object itself. `BountyService` (`bounty/BountyService.java:34-43`) is constructed with
  `Supplier<Collection<Oath>> allOaths` today and has no `Ledger` reference; `OathboundPlugin` already
  holds a `Ledger` field with a public `ledger()` getter (`OathboundPlugin.java:114,823`), so threading
  it in at the one construction site (`OathboundPlugin.java:222`, `new BountyService(...)`) is a small,
  localized change.

## Implementation plan

### Phase A — data model

1. **`oath/LedgerEntry.java`** — add `Set<PlayerRef> faultedParties` as a new record component
   (defensive-copy in the compact constructor via `Set.copyOf(...)`, defaulting call sites to
   `Set.of()`). Empty means "fault unknown or shared" - the existing symmetric behavior - never `null`.
2. **`oath/Ledger.java`** — `record(UUID oathId, OathState fromState, OathState toState, PlayerRef actor)`
   stays exactly as-is (internally passes `Set.of()`). Add a fault-aware overload `record(UUID oathId,
   OathState fromState, OathState toState, PlayerRef actor, Set<PlayerRef> faultedParties)`.
3. A small Gson round-trip test (in whatever test class already covers `LedgerEntry`/`Ledger`
   persistence, or a new one) constructing a `LedgerEntry` with a non-empty `faultedParties`,
   serializing and deserializing it through the same `Gson` instance `SqliteDataStore` uses, asserting
   equality - confirms the "no migration, no `GsonFactory` change" assumption above before relying on it
   elsewhere.

### Phase B — service layer

4. **`oath/OathService.java`** — `breach(Oath oath, PlayerRef actor)` (line 102-104) stays, now
   delegating to a new private fault-aware `transition` overload with `Set.of()`. Add
   `breach(Oath oath, PlayerRef actor, Set<PlayerRef> faultedParties)`, validating (throw
   `IllegalArgumentException`) that every member of `faultedParties` is actually one of `oath.parties()`
   - a fault set naming a non-party is a caller bug, not something to silently accept. Add a private
   `transition(Oath oath, OathState target, PlayerRef actor, Set<PlayerRef> faultedParties)` sibling to
   the existing `transition(oath, target, actor)` (the latter now delegates to the former with
   `Set.of()`), calling the new `ledger.record(..., faultedParties)` overload.

### Phase C — command surface

5. **`command/OathboundDebugCommand.java`** — remove `case "breach" -> oathTransition(sender, args,
   OathService::breach);` from `handleOath`'s switch (line ~230); add `case "breach" ->
   oathBreach(sender, args);` instead. New method `oathBreach(Player sender, String[] args)`: usage
   `/oathbound-debug oath breach <oathId> [faultedPlayerName...]`, resolves `args[2]` via `findOath`
   exactly like `oathTransition` does, resolves any trailing names via `Bukkit.getOfflinePlayer` into a
   `Set<PlayerRef>` (empty set if no trailing names given - so the bare `/oathbound-debug oath breach
   <oathId>` form keeps working exactly as it does today, fully backward compatible), calls
   `plugin.oathService().breach(oath, actorRef, faultedSet)`, then persists/reports exactly like
   `oathTransition` does.

### Phase D — Honor

6. **`listener/HonorLedgerListener.java`** — in `onLedgerEntry`, when `entry.toState() ==
   OathState.BROKEN`: if `entry.faultedParties()` is non-empty, apply `breachLoss`/the curse debuff only
   to parties in `entry.faultedParties()` that are also in `oath.parties()` (defensive intersection -
   never trust a stored fault set blindly); every other party is held harmless (no Honor change, no
   debuff). If `entry.faultedParties()` is empty, keep exactly today's behavior (every party affected) -
   this is the fully-backward-compatible default for anyone not passing fault info.

### Phase E — Bounty breach discount

7. **`bounty/BountyService.java`** — add a `Ledger ledger` field/constructor param (alongside the
   existing `allOaths` supplier).
8. **`bounty/HeatCalculator.recentBreachBetween`** — add a `Ledger ledger` parameter. For each candidate
   `BROKEN` oath in the window, look up its `BROKEN` `LedgerEntry` via `ledger.entriesFor(oath.id())`
   (filter `toState() == BROKEN`, there should be at most one per oath since `BROKEN` is terminal).
   Upgrade the condition: if that entry's `faultedParties()` is non-empty, only count it when the
   *target* (not the placer) is in the faulted set; if empty (untagged, e.g. an old breach from before
   this feature existed), fall back to today's coarser "any breach between the pair" check, for
   consistency with the Honor listener's fallback.
9. **`OathboundPlugin.java:222`** — pass `ledger` into the `new BountyService(...)` call.

### Phase F — README

10. Flip the "Fault attribution" Roadmap bullet this plan is pointed at from `[ ]` to `[x]`, in the same
    prose density as neighboring "Done" entries, explicitly noting what's still out of scope (automated
    breach detection, lopsided-fulfillment, player-initiated disputes - see Context above).
11. Update the two existing "Known limitation" mentions (Honor and Bounty Roadmap bullets) to say
    fault is now tracked for manually-declared breaches, rather than removing the caveat outright - both
    still have the automated-detection gap.

## Verification

```
./gradlew test --tests "*.oath.LedgerTest" --tests "*.oath.OathServiceTest"
./gradlew test --tests "*.listener.HonorLedgerListenerTest"
./gradlew test --tests "*.bounty.*"
./gradlew test
```

Manual smoke test via `./gradlew runServer`:
1. Seal and activate a two-party oath with real stakes (e.g. an `EscrowClause`) so `OathSeverity` is
   non-trivial.
2. `/oathbound-debug oath breach <oathId> <oneOfThePartyNames>` - confirm only that player's Honor drops
   (`/oathbound-debug honor info <player>` for both parties) and, if it's a Blood Oath, only that player
   gets the curse debuff.
3. Repeat with `/oathbound-debug oath breach <oathId>` (no trailing name) on a fresh oath - confirm both
   parties' Honor drops, matching today's behavior exactly (regression check).
4. Place a bounty as the non-faulted party from step 2 against the faulted one within
   `bounty.breach-discount-window-days` - confirm the discount applies. Place a bounty the other
   direction (faulted party against the one who was wronged) - confirm it does *not* discount.

### Critical files
- `src/main/java/com/google/gmail/philbgarner/oathbound/oath/LedgerEntry.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/oath/Ledger.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/oath/OathService.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/command/OathboundDebugCommand.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/listener/HonorLedgerListener.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/bounty/BountyService.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/bounty/HeatCalculator.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/OathboundPlugin.java`
