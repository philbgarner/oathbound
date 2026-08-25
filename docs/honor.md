# Honor Scoring

A single global Honor score per player (not per-relationship), moved by `HonorLedgerListener` reacting
to `LedgerEntry` events - a domain-layer listener registered on the `Ledger` itself, not a Bukkit event.
Paste into [mermaid.live](https://mermaid.live).

```mermaid
flowchart TD
    Ledger["Ledger records a state\ntransition (every oath transition\nis logged)"] --> ToState{entry.toState}
    ToState -->|FULFILLED| Gain
    ToState -->|BROKEN| Loss
    ToState -->|"SEALED / ACTIVE / VOIDED"| NoOp["No-op - VOIDED is deliberately\nneutral (mutual cancellation)"]

    Gain["HonorCalculator.fulfillGain(oath)"] --> StakesCheck
    Loss["HonorCalculator.breachLoss(oath)"] --> StakesCheck

    StakesCheck{"Non-empty clause list,\nAND every clause is a\nCustomFlagClause?"}
    StakesCheck -->|yes, no real stakes| ZeroDelta["delta = 0 - a pure RP pledge\n(e.g. the built-in welcome-pact)\nmoves no Honor either way"]
    StakesCheck -->|no| Severity1

    Severity1["OathSeverity.of(oath) =\nclause count + sum of escrowed\ncurrency amounts (min 1)"] --> Scale["delta = base * severity\n(fulfill-gain-base=10 or\nbreach-loss-base=20)"]
    Scale --> BloodCheck{oath.bloodOath?}
    BloodCheck -->|yes| Amplify["delta *= honor.blood-oath-multiplier\n(default 2.0) - both directions"]
    BloodCheck -->|no| Skip[No amplification]
    Amplify --> Apply
    Skip --> Apply
    ZeroDelta --> Apply

    Apply["For EVERY party on the oath:\nHonorService.adjust(party, ±delta)\n(persisted async)"] --> BreachDebuff{"BROKEN and\noath.bloodOath?"}
    BreachDebuff -->|yes| Debuff["Every party gets a temporary\npotion-effect curse\n(honor.blood-oath-breach-debuff-*,\ndefault WEAKNESS, 300s, amplifier 1)"]
    BreachDebuff -->|no| Done[Done]
```

## Reading a score

```mermaid
flowchart LR
    Score["Player's Honor (can go\nnegative - it's reputation,\nnot a spendable balance)"] --> Tiers["HonorTiers.titleFor:\nhighest configured threshold\nthe score meets or exceeds"]
    Tiers --> Titles["-1000 Faithbroken · 0 Unproven\n500 Oathkeeper · 2000 Renowned\n(config-driven, tune freely)"]
```

## Gating a new Blood Oath

```mermaid
flowchart TD
    CreateBlood["/oathbound-oath create &lt;player&gt; blood"] --> Check{"Current Honor &gt;=\nhonor.min-honor-for-blood-oath?\n(default 0 - only blocks\nplayers already Honor-negative)"}
    Check -->|yes| Allow[Draft created]
    Check -->|no| Deny[Blocked]
```

## Notes

- **No-stakes oaths score zero Honor movement.** `HonorCalculator.scale` short-circuits to `delta = 0`
  before computing `OathSeverity` for any oath whose clause list is non-empty but made up entirely of
  `CustomFlagClause`s (a zero-clause oath is unaffected - that's the baseline "not fleshed out yet" case,
  still scored at severity 1). Without this, a zero-stakes oath like the built-in `welcome-pact` ceremony
  minted the full `fulfill-gain-base` for both parties on every use, repeatably and for free - closed by
  this check rather than by delaying `CustomFlagClause`'s (deliberately instant) auto-fulfillment.
- **Known limitation:** the domain model has no fault-attribution concept, so Honor moves apply to
  **every party** of the oath on both `FULFILLED` and `BROKEN` - not just whoever was actually at
  fault. `BROKEN` itself is still only reachable via `/oathbound-debug oath breach` - there's no
  automated "unmet deadline" detection yet.
- Item stakes have no scalar value system yet, so `OathSeverity` only counts clause count and escrowed
  *currency* - item-heavy oaths under-score relative to their real stakes today.
- Cosmetic titles are display-only via `/oathbound-debug honor info` - no chat-prefix or Oath-Board
  integration exists yet.

## Update this diagram when touching

`honor/HonorService.java`, `honor/HonorCalculator.java`, `honor/OathSeverity.java`,
`honor/HonorTiers.java`, `listener/HonorLedgerListener.java`.
