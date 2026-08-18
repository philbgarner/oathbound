# Escrow

`EscrowClause` deposits are pulled from the depositor the moment the clause is added - not on sealing,
not on release - and currency vs. items take genuinely different paths afterward. Paste into
[mermaid.live](https://mermaid.live).

```mermaid
flowchart TD
    AddClause["Player adds an EscrowClause\nto a DRAFT oath"] --> Withdraw["Items pulled from inventory,\ncurrency pulled from balance,\nimmediately"]
    Withdraw --> DraftFate{What happens to the draft?}

    DraftFate -->|Cancelled before proposing| Refund["Full refund to depositor"]
    DraftFate -->|Proposed -&gt; sealed -&gt; activated| WaitActive["Oath is now ACTIVE -\ndeposit held by the clause"]

    WaitActive --> Tick["ConditionEngine tick:\nrelease-schedule condition evaluated\n(AND across every ReleaseStep -\nfires atomically, never incrementally)"]
    Tick --> Met{Condition met?}
    Met -->|no| WaitActive
    Met -->|yes| Split{Currency or items?}

    Split -->|currency| PayBalance["Pays straight into the\nrecipient's balance -\nno claim needed"]
    Split -->|items| CreateClaim["New EscrowClaim added to\nthe claimable pool -\nitems need a real inventory\nto land in"]

    CreateClaim --> Nudge["Recipient gets a login nudge\nif something's waiting"]
    Nudge --> ClaimCmd["/oathbound-oath claim"]
    ClaimCmd --> Claimed{"Claimed within\nescrow.claim-expiry-days\n(default 30)?"}
    Claimed -->|yes| Delivered[Delivered to recipient]
    Claimed -->|no| Expired["Flips back to claimable\nBY THE ORIGINAL DEPOSITOR instead"]
```

## Notes

- **Only unclaimed items expire** - a currency release is final and instant, since a balance isn't a
  delivery problem the way an inventory slot is.
- **Not implemented:** escheat-to-Notary and breach-split abandonment policies from the master plan -
  "return to depositor" is the only unclaimed-item policy today, since neither a Notary escheat system
  nor a breach-split system exists yet.
- Escrow release also feeds [Honor](honor.md) indirectly: `OathSeverity` counts escrowed currency
  toward an oath's stakes, scaling how much Honor moves on `FULFILLED`/`BROKEN`.

## Update this diagram when touching

`oath/Clause.java` (`EscrowClause`, `ReleaseStep`), `oath/EscrowClaim.java`,
`oath/EscrowExpiryService.java`, `oath/ConditionEngine.java` (`executeEscrowRelease`).
