# Condition Engine

A periodic tick (`ConditionEngine.tick`) walks every `ACTIVE` oath and turns each *met* condition into
its real effect - this is what separates "the engine can tell you a condition would be true" from
"the engine actually did something about it." Paste into [mermaid.live](https://mermaid.live).

```mermaid
flowchart TD
    Tick([Periodic tick: ConditionEngine.tick]) --> ForEachOath{For each oath}
    ForEachOath -->|state != ACTIVE| SkipOath[Ignored]
    ForEachOath -->|state == ACTIVE| ForEachClause{For each clause,\nin order}

    ForEachClause -->|CustomFlagClause| NoEffect[No condition, no effect -\nnever blocks fulfillment]
    ForEachClause -->|already fulfilled| NextClause[Skip]

    ForEachClause -->|TransferClause| EvalTransfer{Condition met?}
    EvalTransfer -->|yes| ExecTransfer["OwnershipResolver.reassignOwner\non the subject group - the group is\nalso added to TickResult.transferredGroups()\n(e.g. for a WorldGuard region-owner sync,\nsee worldguard-integration.md)"] --> MarkFulfilled
    EvalTransfer -->|no| NotDone[allAutoResolvableAndDone = false]

    ForEachClause -->|EscrowClause| EvalEscrow{Release-schedule condition met?\nAND of every ReleaseStep's condition,\nfires atomically, not incrementally}
    EvalEscrow -->|yes| ExecEscrow[Currency -> recipient's balance directly.\nItems -> new EscrowClaim, added to\nthe claimable pool] --> MarkFulfilled
    EvalEscrow -->|no| NotDone

    ForEachClause -->|KillCountClause| EvalKill{DeathTracker: target's death\ncount since activation >= quantity?}
    EvalKill -->|yes| MarkFulfilled
    EvalKill -->|no| NotDone

    ForEachClause -->|MobKillClause| EvalMob{MobKillTracker: obligor's kills\nof mobType since activation >= quantity?}
    EvalMob -->|yes| MarkFulfilled
    EvalMob -->|no| NotDone

    ForEachClause -->|DiplomacyClause| EvalDip{Condition met?}
    EvalDip -->|yes| ExecDip["DiplomacyService.setState(\nrootOf(groupA), rootOf(groupB), newState)"] --> MarkFulfilled
    EvalDip -->|no| NotDone

    MarkFulfilled[oath.markClauseFulfilled] --> NextClause
    NextClause --> AllDone{Every clause processed\nAND allAutoResolvableAndDone\nstill true?}
    AllDone -->|yes| Fulfill["OathService.fulfill(oath, SYSTEM)\n-> FULFILLED"]
    AllDone -->|no| Wait[Oath stays ACTIVE,\nre-evaluated next tick]
```

Condition evaluation itself (`ConditionEvaluator`) is a small recursive tree over
`Condition`'s sealed variants:

```mermaid
flowchart TD
    Eval{Condition type} -->|Immediate| True1[Always true]
    Eval -->|TimeElapsed| Time{now >= activatedAt + duration?}
    Eval -->|DeathCount / PvpDeathCount| Death{DeathTracker count for\ntarget since activation >= count?}
    Eval -->|MobKillCount| Mob{MobKillTracker count\nsince activation >= count?}
    Eval -->|PaymentReceived| Pay{Sum of this oath's own\nEscrowClause deposits in\nthat currency >= amount?}
    Eval -->|ManualConfirm| Manual{ManualConfirmStore has an\nentry for requiredParty+oath?}
    Eval -->|VoteTally| Stub["Always false - honest no-op stub,\nno Election Oaths system yet"]
    Eval -->|Compound AND| And["All subconditions true"]
    Eval -->|Compound OR| Or["Any subcondition true"]
    Eval -->|Compound NOT| Not["The single subcondition is false"]
```

## Notes

- `KillCountClause` and `MobKillClause` have **no execution side effect of their own** - resolving the
  condition *is* fulfilling the clause (unlike Transfer/Escrow/Diplomacy, which do something on top of
  the condition becoming true).
- A clause type the engine doesn't recognize (there currently isn't one, but the `switch` has a
  `default -> false`) permanently blocks auto-fulfillment - the oath just sits `ACTIVE` forever unless
  someone force-resolves it via `/oathbound-debug oath fulfill|breach`.
- `PaymentReceived` only sees **this oath's own** escrow deposits, so a same-oath `TransferClause` can
  gate on its own `EscrowClause` having been paid in.
- Legacy/rehydrated oaths with no `activatedAt` timestamp are skipped entirely (nothing safe to
  evaluate a duration/count against).

## Update this diagram when touching

`ConditionEngine.java`, `ConditionEvaluator.java`, `Condition.java`, `Clause.java`,
`DomainConditionContext.java`, `DeathTracker.java`, `MobKillTracker.java`, `ManualConfirmStore.java`.
