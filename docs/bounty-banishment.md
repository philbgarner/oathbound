# Bounty / Kill Contracts & Banishment

Bounties are placed **unilaterally** - the target never consents - so they're deliberately not built
on `Oath`/`ConditionEngine` at all. A separate `bounty` package (`BountyService`/`BanishmentService`)
handles placement, fulfillment, and the End-pen sentence. Paste into
[mermaid.live](https://mermaid.live).

## Placement

```mermaid
flowchart TD
    Open["Open the Bounty Board\nfrom any Notary"] --> Place["Place New Bounty:\ntype target (player or group),\nquantity if a group (capped at\ncurrent member count), reward"]
    Place --> Quote["BountyService.quote:\nHeatCalculator.heatOf - live-computed\nfrom every other active/recent bounty\non the same target, linearly decaying\nover bounty.heat-decay-hours (72)"]
    Quote --> Fee["fee = bounty.fee-base * (1 +\nheat * heat-fee-multiplier) -\nfirst bounty on someone is cheap,\nstacking several gets expensive fast"]
    Fee --> Discount{"Placer and target had an oath\ngo BROKEN within\nbreach-discount-window-days (14)?\n(approximated - no fault-attribution)"}
    Discount -->|yes| Discounted["fee *= (1 - breach-discount-fraction)\n(default half price)"]
    Discount -->|no| FullFee[Full fee]
    Discounted --> Confirm
    FullFee --> Confirm{"Confirm at preview screen"}
    Confirm --> DailyCap{"Placer already at\nmax-placements-per-24h (3)?"}
    DailyCap -->|yes| Reject["BountyPlacementLimitException -\nrejected before any currency moves"]
    DailyCap -->|no| Charge["Fee charged via EconomyService,\nBounty created, status ACTIVE"]

    Cancel["Placer cancels their own bounty"] --> Refund["Refunds the unpaid remainder -\nthe placement fee itself is a sunk cost"]
```

## Fulfillment

```mermaid
flowchart TD
    Kill["A qualifying kill lands on\nthe bounty's target"] --> DropHead["Victim force-drops a tagged\nPLAYER_HEAD (native item,\nno kill-attribution tracking -\nanyone can pick it up)"]
    DropHead --> Sentence["Banishment sentence begins/extends\nfor the VICTIM (see below),\nregardless of who turns the head in later"]

    DropHead --> HoldHead["Anyone holding the tagged head\nright-clicks ANY Notary"]
    HoldHead --> TurnIn["BountyService.turnInHead:\npays the holder - not necessarily\nwhoever landed the kill"]
    TurnIn --> GroupOrSolo{Solo or group contract?}
    GroupOrSolo -->|solo| FullPayout["Full reward paid,\nstatus -&gt; FULFILLED"]
    GroupOrSolo -->|group| PerHead["Even per-head installment paid,\nremainingQuantity -= 1.\nContract stays ACTIVE until\nevery head is turned in."]

    Abandon["Login-check sweep:\nBountyAbandonmentSweepService"] --> Inactive{"A group-bounty-targeted member's\nOfflinePlayer.getLastPlayed() exceeds\nbounty.abandon-inactivity-days (30)?"}
    Inactive -->|yes| AutoDecrement["remainingQuantity -= 1\nautomatically, no head needed"]
```

## Banishment

```mermaid
flowchart TD
    Sentence["Qualifying kill against\nthe victim lands"] --> Hours["BanishmentService.hoursFor:\nbountyAmountPaid / hours-per-currency-unit (50),\nclamped to [min-hours(1), max-hours(72)]"]
    Hours --> Existing{Victim already\nserving a sentence?}
    Existing -->|no| Begin["New Banishment: servingSince=now,\nreleaseAt=now+hours,\nteleport to the fixed End pen\n(banishment.pen coordinates,\nset via /oathbound-debug banishment set-pen)"]
    Existing -->|yes| Extend["Extend remaining time -\nNEVER resets it - capped so total\nduration from servingSince never\nexceeds stack-cap-hours (168)"]

    Begin --> Hold["Pen holds the player across\nlogout/respawn"]
    Extend --> Hold
    Hold --> Sweep["Periodic sweep + login check"]
    Sweep --> Elapsed{now &gt;= releaseAt?}
    Elapsed -->|yes| Release["forceRelease: auto-teleport back\nto where the player died\n(checked even if the sentence\nelapsed while offline)"]
    Elapsed -->|no| Hold
```

## Release paths

A sentence is no longer a pure fire-and-forget timer - two independent ways to shorten or forgive one
exist, both driving the same `Banishment.reduceSentence` seam:

```mermaid
flowchart TD
    Sentence["Active Banishment"] --> ReleaseOath["Oath-based: an ally negotiates\nwith whoever holds authority over\nthe sentence via a normal Oath\ncarrying a BanishmentReleaseClause\n(target, reduction/fullRelease, condition) -\n/oathbound-debug oath addbanishmentrelease"]
    Sentence --> Prayer["Altar-based: shift-right-click any\nNORMAL/DECAYING barrel, pick the target\nfrom the Altar of Intercession board,\nsacrifice enchanted items - value converts\nto hours via banishment.prayer-hours-per-power\n(see Altars doc)"]
    ReleaseOath --> Reduce["ConditionEngine.executeBanishmentRelease\nonce the clause's condition resolves"]
    Prayer --> Reduce
    Reduce --> Clamp["Banishment.reduceSentence:\nnever goes before 'now' - a large\nenough reduction ends it immediately"]
```

The release oath reuses the normal propose/seal handshake for consent (the placer has to actually agree
to the terms of whatever the ally offers), same as a `DiplomacyClause` treaty; the prayer ritual needs no
one's consent at all, just a big enough sacrifice - the two are deliberately different in that respect,
mirroring how a treaty requires mutual agreement but a sacrifice at an altar doesn't.

## PvE contracts (separate, no attribution problem)

```mermaid
flowchart LR
    Board["Same Bounty Board GUI"] --> PvE["Standing, repeatable,\nadmin-authored pve-contracts\n(config.yml, not persisted -\nonly per-player progress is)"]
    PvE --> NativeKill["Native Bukkit kill-attribution\n(unlike bounties - no reason\nto avoid it for a\nserver-owned economy tap)"]
    NativeKill --> Complete{Required kill count reached?}
    Complete -->|yes| PayReset["Pays out, then resets\nfor the next batch"]
```

## Notes

- A one-time login notice tells a player they have a bounty the first time they log in after it's
  placed (not every login); `/oathbound-bounty list [player]` is the always-available pull, and
  `/oathbound-bounty notify off` opts out of the notice entirely.
- Heat and Power (Altars) share the same design pattern deliberately: never persisted, always
  recomputed live from history + a decay window.

## Update this diagram when touching

`bounty/Bounty.java`, `bounty/BountyService.java`, `bounty/HeatCalculator.java`,
`bounty/BountyTargeting.java`, `bounty/Banishment.java`, `bounty/BanishmentService.java`,
`bounty/BanishmentSweepService.java`, `bounty/BountyAbandonmentSweepService.java`,
`bounty/PveContractService.java`, `oath/Clause.java` (`BanishmentReleaseClause`),
`oath/ConditionEngine.java` (`executeBanishmentRelease`), `command/OathboundDebugCommand.java`
(`addbanishmentrelease`), `gui/BanishmentPrayerBoardGui.java`, `gui/PrayerAltarGui.java`,
`gui/BanishmentPrayerGuiListener.java`.
