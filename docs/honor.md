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

    Gain["HonorCalculator.fulfillGain(oath)"] --> Severity1
    Loss["HonorCalculator.breachLoss(oath)"] --> Severity1

    Severity1["OathSeverity.of(oath) =\nclause count + sum of escrowed\ncurrency amounts (min 1)"] --> Scale["delta = base * severity\n(fulfill-gain-base=10 or\nbreach-loss-base=20)"]
    Scale --> BloodCheck{oath.bloodOath?}
    BloodCheck -->|yes| Amplify["delta *= honor.blood-oath-multiplier\n(default 2.0) - both directions"]
    BloodCheck -->|no| Skip[No amplification]
    Amplify --> Apply
    Skip --> Apply

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

- **Known limitation:** the domain model has no fault-attribution concept, so Honor moves apply to
  **every party** of the oath on both `FULFILLED` and `BROKEN` - not just whoever was actually at
  fault. `BROKEN` itself is still only reachable via `/oathbound-debug oath breach` - there's no
  automated "unmet deadline" detection yet.
- Item stakes have no scalar value system yet (the same gap Altar sacrifice valuation solves for
  enchantments), so `OathSeverity` only counts clause count and escrowed *currency* - item-heavy oaths
  under-score relative to their real stakes today.
- Cosmetic titles are display-only via `/oathbound-debug honor info` - no chat-prefix or Oath-Board
  integration exists yet.

## Update this diagram when touching

`honor/HonorService.java`, `honor/HonorCalculator.java`, `honor/OathSeverity.java`,
`honor/HonorTiers.java`, `listener/HonorLedgerListener.java`.
