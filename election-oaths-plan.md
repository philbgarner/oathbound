# Election Oaths — researched implementation plan (parked)

**Status: designed, not implemented.** This was researched in depth against the actual codebase but
deliberately deferred — the scope (condition-engine changes, a new domain package, persistence, GUI,
commands, and debug tooling all at once) was judged too large to take on in one pass. This doc exists
so a future session can pick it up without redoing the research. See `README.md`'s "Deliberately
deferred" roadmap section for the pointer back here.

## Context

Master plan §11 (`oathbound-master-plan.md`) specifies Election Oaths as built entirely on existing
primitives: candidacy is a `CustomFlagClause` oath per candidate (platform promises, no mechanical
effect), a ballot is time-boxed with voter eligibility gated by `role.canVote`, `TimeElapsed`
triggers a tally (`VoteTally` condition), and the winning candidate's `TransferClause` reassigns the
contested group's `owner` field — the same mechanism as any other ownership transfer. Losing
candidates' escrow (if staked) auto-returns.

The scaffolding for this already exists in the codebase, stubbed out: `Condition.VoteTally(UUID
ballotRef)` exists, `ConditionEvaluator` delegates to `ConditionContext.isBallotDecided(ballotRef)`,
and `DomainConditionContext.isBallotDecided` is hardcoded `return false` with a javadoc admitting
it's a stub pending this exact feature. `GroupPermission.VOTE` and `GroupPermission.BE_CANDIDATE`
also already exist in the enum but are unused anywhere in the codebase today.

**Key design problem, and its resolution:** a plain "has this ballot decided" boolean is
insufficient. If every candidate in a race has their own oath with a `TransferClause` gated on the
same `VoteTally(ballotRef)`, then the moment the ballot decides, *every* candidate's condition
would evaluate true simultaneously (none of them reference *which* candidate specifically) and all
would race to reassign the same group to different targets. The fix: extend
`Condition.VoteTally` to carry the candidate it's gating for — `VoteTally(UUID ballotRef, PlayerRef
candidate)` — and change the seam to resolve the winner (`Optional<PlayerRef>
ballotWinner(UUID ballotRef)`) rather than a bare boolean. This is a breaking change to an
already-scaffolded record, but the project is pre-release ("Status: early development") with no
back-compat obligation, and this codebase's convention is to make clean changes over compat shims.

**Two scope decisions already made, if this is picked back up:**
- **Candidacy staking (escrow stake + auto-refund for losers) should stay deferred.** There's no
  existing refund/void-triggered escrow-return mechanism anywhere in the codebase to build on (the
  only precedent, `EscrowExpiryService`, is a claim-timeout sweep on a different trigger — unclaimed
  items past expiry flip back to the depositor, not a void/loss-triggered refund). A first pass
  should ship candidacy (platform statement), voting, tally, and the `VoteTally → TransferClause`
  wiring cleanly; document staking as a known gap, matching how other "Done" roadmap entries document
  limitations (Notary's missing counter-offer chain, Honor's missing fault-attribution).
- **Should ship with a full GUI + command surface**, matching the completeness bar of every other
  shipped roadmap item (Altars, Notary, Trade Contracts, Named-Party Oaths, Oath Board all got real
  player-facing surfaces, not just debug commands).

Two design corrections, verified against the actual code:
- No separate `BallotStore` class. `Election` is structurally identical to `Oath`/`ProtectionGroup`
  (one full CRUD domain object per id), so it should get a normal `electionCache` on
  `OathboundPlugin` exactly like `oathCache`/`groupCache`, and `ConditionEngine` should get a 7th
  constructor parameter `Function<UUID, Optional<Election>> electionLookup`, mirroring the existing
  `groupLookup` pattern — not a second wrapper store that could drift out of sync with the cache.
- `Election.votes` (a `Map<PlayerRef, PlayerRef>`) must **not** be Gson-serialized as a map with a
  record key — `GsonFactory` never calls `enableComplexMapKeySerialization()`, so a populated map
  serializes fine but throws `JsonSyntaxException` on read-back. `ElectionDto` should represent votes
  as `List<Vote>` (a small `Vote(PlayerRef voter, PlayerRef candidate)` record), matching how
  `GroupDto` already stores `members`/`roles` as lists rather than id-keyed maps.

Verified directly against the code (not assumed, as of this writing): `OathService.TRANSITIONS`
already allows `ACTIVE → VOIDED`, so voiding a losing candidate's `ACTIVE` oath needs no
state-machine change. `PlayerRef.SYSTEM` already exists and nothing in `OathService`'s
transition/draft methods requires parties to be online/real, so `[candidate, PlayerRef.SYSTEM]` is
safe to use as a 2-party candidacy oath driven synchronously through `DRAFT → PROPOSED → SEALED →
ACTIVE` in one service call. Gson round-trips added record components automatically (via
`PolymorphicTypeAdapterFactory`'s reflective adapter resolution), so extending `VoteTally` needs no
`GsonFactory.java` change.

## Implementation plan

### Phase A — extend the condition engine seam

1. **`oath/Condition.java`** — `VoteTally(UUID ballotRef)` → `VoteTally(UUID ballotRef, PlayerRef candidate)`.
2. **`oath/ConditionContext.java`** — replace `boolean isBallotDecided(UUID ballotRef)` with
   `Optional<PlayerRef> ballotWinner(UUID ballotRef)`.
3. **`oath/ConditionEvaluator.java`** — the `VoteTally` case becomes:
   ```java
   case Condition.VoteTally(var ballotRef, var candidate) ->
           context.ballotWinner(ballotRef).map(candidate::equals).orElse(false);
   ```
4. **`oath/DomainConditionContext.java`** — add a 7th field `Function<UUID, Optional<Election>>
   electionLookup` (new dependency on the `election` package), threaded through the constructor;
   implement `ballotWinner` as `electionLookup.apply(ballotRef).filter(e -> e.state() ==
   ElectionState.DECIDED).map(Election::winner)`. Update the class javadoc — no longer a stub.
5. **`oath/ConditionEngine.java`** — add the same `electionLookup` as a constructor
   field/param (next to `groupLookup`), thread it into the `new DomainConditionContext(...)` call.
6. **`oath/ConditionEvaluatorTest.java`** — `FakeContext`'s `isBallotDecided` becomes `ballotWinner`
   returning `Optional.ofNullable(...)`; update the 5 `VoteTally` call sites to the 2-arg form and
   exercise both the matching- and non-matching-candidate branches.
7. **`oath/ConditionEngineTest.java`** — `setUp()`'s `ConditionEngine` construction gets the extra
   `electionLookup` argument (`id -> Optional.empty()` for existing tests that don't use `VoteTally`).

### Phase B — election domain package (`election/`, Bukkit-free, mirrors `oath/`)

8. **`election/ElectionState.java`** — `enum { OPEN, DECIDED, CANCELLED }`.
9. **`election/Candidacy.java`** — `record Candidacy(PlayerRef candidate, UUID candidacyOathId)`.
10. **`election/Election.java`** — mutable class mirroring `Oath.java`'s shape: public constructor
    for new elections, package-private `static reconstruct(...)` for rehydration, defensive-copy
    accessors, package-private mutators (`addCandidacy`, `recordVote` — overwrite semantics so a
    voter can change their mind until close, `setState`, `setWinner`, `setResolvedAt`). Fields: `id`,
    `groupId: ProtectionGroupRef`, `createdBy`, `candidates: List<Candidacy>`, `votes: Map<PlayerRef,
    PlayerRef>`, `opensAt`/`closesAt`/`createdAt: Instant`, `state: ElectionState`, `winner:
    PlayerRef` (null until decided), `resolvedAt`.
11. **`election/ElectionService.java`** — Bukkit-free validation + orchestration, constructor takes
    `OathService` as its one collaborator:
    - `createElection(ProtectionGroup group, PlayerRef creator, Duration votingPeriod)` — requires
      `group.hasPermission(creator, GroupPermission.TRANSFER_OWNERSHIP)` (reused, no new permission).
    - `declareCandidacy(Election election, ProtectionGroup group, PlayerRef candidate, String
      platformText)` — requires `BE_CANDIDATE`, election still open, not already registered; builds
      the candidacy oath: `createDraft([candidate, PlayerRef.SYSTEM], false)`, adds a
      `CustomFlagClause(platformText)` and a `TransferClause(candidate, election.groupId(),
      VoteTally(election.id(), candidate))`, then `propose/seal/activate` with `candidate` as actor,
      registers the `Candidacy` on the election, returns the oath for the caller to cache/persist.
    - `castVote(Election election, ProtectionGroup group, PlayerRef voter, PlayerRef candidate)` —
      requires `VOTE`, election open, candidate registered; overwrites any prior vote from that voter.
    - Package-private `closeElection`/`cancelElection`, called only by `ElectionCloseService`.
12. **`election/ElectionCloseService.java`** — stateless `sweep(Collection<Election> elections,
    Instant now, ElectionService electionService)` mirroring `oath/NegotiationExpiryService.java`'s
    shape: for every `OPEN` election past `closesAt`, tally votes per candidate, winner = most votes
    (strict `>` comparison over candidates in declaration order preserves an earliest-declared
    tie-break), `cancelElection` if there are no candidates at all; returns the resolved elections.

### Phase C — persistence

13. **`persistence/dto/ElectionDto.java`** — record mirroring `Election`'s fields, with a nested
    `Vote(PlayerRef voter, PlayerRef candidate)` record for the votes list (not a map — see Context),
    `from()`/`toDomain()`, matching `OathDto`'s convention (`Election` is structurally closer to
    `Oath` than to the plain-record `OathBoard`).
14. **`db/migrations/0010_elections.sql`** — `CREATE TABLE IF NOT EXISTS elections (id TEXT PRIMARY
    KEY, data TEXT NOT NULL)`, matching `0009_oath_boards.sql`'s minimal shape.
15. **`persistence/DataStore.java`** — add `saveElection`/`loadElection`/`loadAllElections`/
    `deleteElection`, all `throws DataStoreException`.
16. **`persistence/sqlite/SqliteDataStore.java`** — append the new migration to `MIGRATIONS`; add the
    four methods following the existing `saveOath`/`loadOath`/`loadAllOaths`/`deleteOath` block
    exactly, using `ElectionDto`.

### Phase D — `OathboundPlugin` wiring

17. Fields: `electionService`, `electionCloseService`, `electionCache: Map<UUID, Election>`
    (`ConcurrentHashMap`).
18. `onEnable()`: construct `electionService`/`electionCloseService`; add the `electionLookup` arg
    (`id -> Optional.ofNullable(electionCache.get(id))`) to the `ConditionEngine` constructor call;
    register `/oathbound-election`'s executor/tab-completer (4-line block, matching existing
    commands); register `ElectionGuiListener`.
19. `loadExistingState()`: populate `electionCache` from `dataStore.loadAllElections()`.
20. Add `persistElectionAsync(Election)` helper, matching `persistOathAsync`'s shape.
21. Add `electionService()`/`electionCache()` getters.
22. `runConditionEngineTick()`: **before** the existing `conditionEngine.tick(...)` call, add a new
    try/catch block calling `electionCloseService.sweep(electionCache.values(), now,
    electionService)`; for each resolved election, `persistElectionAsync`, and for a `DECIDED`
    election, void every non-winning candidate's `ACTIVE` oath (`oathService.voidOath(losing,
    PlayerRef.SYSTEM)` + `persistOathAsync`) so losing candidacies don't sit `ACTIVE` forever. Running
    the sweep before `tick()` means the winner's `VoteTally` condition is already satisfiable in the
    very same scheduler cycle (no extra 5s lag), since `sweep` mutates the same `Election` objects
    held in `electionCache` that `electionLookup` reads from.

### Phase E — GUI (`gui/` package)

23–24. `ElectionBoardHolder`/`ElectionBoardGui` — browse all `OPEN` elections, mirroring
   `TradeBoardHolder`/`TradeBoardGui`; click → detail view.
25–26. `ElectionDetailHolder`/`ElectionDetailGui` — one icon per registered candidate (name,
   platform text pulled from their candidacy oath's `CustomFlagClause`, current tally once
   votes are visible), a per-candidate vote-cast click gated on `VOTE`, and a "Declare Candidacy"
   button gated on `BE_CANDIDATE` and not already registered.
27. `ElectionGuiListener` — click routing (`instanceof` dispatch, cancel top-inventory clicks)
   mirroring `OathBuilderListener`; owns the chat-prompt capture for the platform-statement text
   using the same `PromptKind`/`PendingPrompt`/`ConcurrentHashMap`/`AsyncChatEvent`-cancel-and-hop-
   to-main-thread pattern. Vote casting needs no prompt (a direct click calls `castVote`
   synchronously).

### Phase F — command

28. **`command/OathboundElectionCommand.java`** (`CommandExecutor, TabCompleter`, mirrors
    `OathboundTradeCommand`): `/oathbound-election call <groupId> [days]`, `/oathbound-election
    board`, bare command defaults to `board`.
29. **`plugin.yml`** — register `oathbound-election` with description/usage.

### Phase G — debug commands

30. **`command/OathboundDebugCommand.java`** — add `"election"` to `TOP_LEVEL`, a new `ELECTION_SUB`
    list (`call`, `candidacy`, `vote`, `close`, `info`, `list`), a `handleElection` dispatcher
    following the existing `oathCreate`-style shape (parse args → call service → cache → persist →
    confirm), a `findElection` lookup helper mirroring `findOath`/`findAltar`, and tab-completion
    wiring. `close` force-closes for testing by calling `electionCloseService.sweep(List.of(election),
    election.closesAt(), electionService)` (passing the election's own close time as "now" so the
    sweep's guard passes immediately, without needing a settable `closesAt`).

### Phase H — config, plugin.yml, README

31. **`config/OathboundConfig.java`** — add `election.default-voting-period-days` (default 7),
    exposed as `electionDefaultVotingPeriod(): Duration`, following the existing
    `notary.negotiation-expiry-days` field/loader pattern.
32. **`src/main/resources/config.yml`** and **README §Configuration** — document the new section.
33. **README §Roadmap** — flip the Election Oaths bullet to `[x]`, with prose in the same density as
    other "Done" entries, explicitly noting candidacy staking/escrow-refund is deferred (candidacy
    oaths carry no `EscrowClause` yet).

### Phase I — tests

34. **`election/ElectionServiceTest.java`** — permission checks for create/declare/vote, duplicate
    candidacy rejection, re-vote overwrite semantics, correct oath shape from `declareCandidacy`.
35. **`election/ElectionCloseServiceTest.java`** — majority winner, tie → earliest-declared candidate,
    zero candidates → `CANCELLED`, not-yet-closed elections left `OPEN`, idempotent on
    already-resolved elections.
36. **`oath/ConditionEngineTest.java`** — new integration test: two candidacy-shaped oaths sharing one
    ballot id, each `TransferClause` gated on `VoteTally(ballotId, thatCandidate)`, an
    `electionLookup` returning a decided election with one winner; `tick()` and assert only the
    winner's group-ownership reassignment and oath `FULFILLED` transition fire — the loser's oath
    stays `ACTIVE` untouched by the generic engine (proving no cross-fire between candidates on the
    same ballot, which is the entire reason `VoteTally` needed the second component).

## Verification

Run the targeted and full test suites:
```
./gradlew test --tests "*.election.*"
./gradlew test --tests "*.oath.ConditionEvaluatorTest" --tests "*.oath.ConditionEngineTest"
./gradlew test --tests "*.persistence.sqlite.SqliteDataStoreTest"
./gradlew test
```

Manual smoke test via `./gradlew runServer`:
1. Create a group and add a couple of members with roles granting `VOTE`/`BE_CANDIDATE`.
2. `/oathbound-election call <groupId> <days>` (or the debug equivalent for a controllable window).
3. As two different members, declare candidacy through the GUI (chat-prompt for platform text);
   confirm `/oathbound-debug oath info` shows each candidacy oath `ACTIVE` with a `CustomFlagClause`
   and a `VoteTally`-gated `TransferClause`.
4. Cast votes for a clear majority winner via the GUI.
5. Force/wait for close (`/oathbound-debug election close <id>` or waiting out the window); after the
   next scheduler tick, confirm `/oathbound-debug election info` shows the winner,
   `/oathbound-debug group info <groupId>` shows the group's owner reassigned to the winner, the
   winner's oath is `FULFILLED`, and the loser's oath is `VOIDED`.
6. Restart the server and re-check `election info`/`group info` to confirm the SQLite round-trip
   preserves the decided state, winner, and vote list.

### Critical files
- `src/main/java/com/google/gmail/philbgarner/oathbound/oath/Condition.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/oath/DomainConditionContext.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/oath/ConditionEngine.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/OathboundPlugin.java`
- `src/main/java/com/google/gmail/philbgarner/oathbound/persistence/sqlite/SqliteDataStore.java`
