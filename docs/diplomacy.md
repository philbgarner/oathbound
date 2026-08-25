# Diplomacy

A `DiplomaticRelation` exists only between two **root** `ProtectionGroup`s (a group not owned by any
other group) - a vassal's diplomatic standing is always its liege's, resolved live via
`OwnershipResolver.resolveRootGroup` (see [Groups & Ownership](groups-ownership.md)). Only `REGION` and
`KINGDOM`-tier root groups may hold a relation at all. Paste into [mermaid.live](https://mermaid.live).

## States

```mermaid
stateDiagram-v2
    [*] --> NEUTRAL : default for any pair with\nno recorded relation -\nNEUTRAL is never itself\npersisted

    NEUTRAL --> WAR : declare-war (unilateral, free)
    NEUTRAL --> PEACE : treaty sealed (DiplomacyClause)
    NEUTRAL --> ALLIANCE : treaty sealed

    PEACE --> WAR : declare-war (unilateral) -\nbetrayal, costs Honor
    ALLIANCE --> WAR : declare-war (unilateral) -\nbetrayal, costs Honor
    WAR --> WAR : declare-war again - no-op cost

    WAR --> PEACE : treaty sealed
    WAR --> ALLIANCE : treaty sealed
    PEACE --> ALLIANCE : treaty sealed
    ALLIANCE --> PEACE : treaty sealed
```

`DiplomacyService.setState` itself has no state-machine restriction - any state can be set to any
other; the "betrayal" cost above is a side effect applied only by the unilateral war-declaration path,
not a hard block.

## Triggers, authority, and side effects

```mermaid
flowchart TD
    Action["Diplomacy-changing action names\ngroupA/groupB (vassal or liege)"] --> Root["rootOf(): resolve each to its\nroot group via OwnershipResolver"]
    Root --> TierGate{"Both roots are\nREGION or KINGDOM tier?"}
    TierGate -->|no| Reject1[Rejected]
    TierGate -->|yes| Trigger{Which trigger?}

    Trigger -->|"Unilateral:\n/oathbound-debug diplomacy declare-war"| PermCheck{"Actor holds\nACCEPT_ON_BEHALF\non root A?"}
    PermCheck -->|no| Reject2[Denied]
    PermCheck -->|yes| PriorState{Prior relation was\nPEACE or ALLIANCE?}
    PriorState -->|yes| Betray["Backstab: actor loses\ndiplomacy.betrayal-honor-penalty\nHonor (default 20)"]
    PriorState -->|no, NEUTRAL or WAR| NoCost[No Honor cost]
    Betray --> SetWar["setState(rootA, rootB, WAR, now)"]
    NoCost --> SetWar

    Trigger -->|"Treaty:\nClause.DiplomacyClause on an Oath"| CondCheck{"Condition met?\n(evaluated by the\nConditionEngine each tick)"}
    CondCheck -->|yes| SetTreaty["executeDiplomacy:\nsetState(rootA, rootB, newState, now)"]

    SetWar --> Persist["DiplomaticRelation persisted -\ngroupA/groupB canonically sorted\nso a pair never double-stores"]
    SetTreaty --> Persist

    Persist --> PvpFlag{"pvp.restrict-to-declared-wars\nenabled? (default true)"}
    PvpFlag -->|yes| PvpGuard["DiplomaticPvpGuardListener:\nPvP damage between two players only\nallowed if their groups' root relation\nis WAR. Groupless players always protected."]
    PvpFlag -->|no| NoEffect[Relation is informational only if\nan admin opts back out]
```

## Notes

- A treaty (via `DiplomacyClause`) requires mutual consent because it rides on the normal Oath
  propose/seal handshake - both signatories have to sign before the clause can ever execute.
  Declaring war does not require the other side's consent at all.
- `NEUTRAL` cannot be set via a treaty clause - it's purely the implicit default for an unrecorded
  pair.
- **Known limitation:** PvP restriction is currently the *only* mechanical consequence of a relation - no
  build/claim interaction and no automatic Oath-Board posting exists yet for a diplomacy change.

## Update this diagram when touching

`diplomacy/DiplomaticState.java`, `diplomacy/DiplomaticRelation.java`, `diplomacy/DiplomacyService.java`,
`oath/Clause.java` (`DiplomacyClause`), `oath/ConditionEngine.java` (`executeDiplomacy`),
`listener/DiplomaticPvpGuardListener.java`, `command/OathboundDebugCommand.java` (diplomacy subcommands).
