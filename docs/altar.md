# Altars

Territory power source and target: consecration, live Power decay, vulnerability tiers, and the
Destroy/Loot desecration outcomes. Paste into [mermaid.live](https://mermaid.live).

## Consecration & nesting legality

```mermaid
flowchart TD
    Stack["Barrel + capstone (default OBSIDIAN,\nconfigurable) + candle stacked vertically"] --> PlaceCandle["Candle placed - this is the trigger"]
    PlaceCandle --> StructureCheck{Structure directly\nbelow the candle is valid?}
    StructureCheck -->|no| NoOp[Nothing happens]
    StructureCheck -->|yes| NestingCheck["AltarNestingService.checkPlacement:\nfor each EXISTING altar whose live radius\ncovers this spot, smallest tier first"]
    NestingCheck --> TierCompare{"existingTier.ordinal() &lt;=\nnewTier.ordinal()?"}
    TierCompare -->|yes, same or smaller tier| Blocked["Consecration blocked"]
    TierCompare -->|"no altar blocks it -\nall covering altars are\nstrictly larger tier"| Consecrate["Consecrated at ZERO Power,\nowned by whoever placed the candle"]
```

This check runs **once, at consecration time only** - a larger claim later shrinking from decay never
retroactively invalidates anything already nested inside it.

## Power, decay, and vulnerability

Power is never stored as a raw number - it's recomputed live every time from `powerBaseline` (the
total as of the last sacrifice) and a linear decay over `altar.decay-days`. Radius is likewise always
`power-radius-scale * tier-multiplier * sqrt(currentPower)`.

```mermaid
stateDiagram-v2
    [*] --> CRITICAL : consecrated at Power 0 -\nimmediately CRITICAL until\nfirst sacrifice
    CRITICAL --> NORMAL : sacrifice raises Power\nabove decaying-threshold, default 100
    CRITICAL --> DECAYING : sacrifice raises Power\nbetween critical-threshold and decaying-threshold
    NORMAL --> DECAYING : Power decays to or below\ndecaying-threshold, default 100 -\nowner gets a one-time login warning
    DECAYING --> CRITICAL : Power decays to or below\ncritical-threshold, default 10 -\nprotection radius drops to zero,\nbarrel becomes interactable
    DECAYING --> NORMAL : sacrifice tops back up
    CRITICAL --> DECAYING : sacrifice raises Power -\nreconsecration cooldown starts
```

## Barrel interaction while Critical

```mermaid
flowchart TD
    RightClick["Right-click the barrel"] --> Tier{Live vulnerability tier}
    Tier -->|NORMAL or DECAYING| SacrificeGui["Open sacrifice chest-GUI:\ndeposit enchanted items, confirm to\nconsume permanently and raise Power"]
    Tier -->|CRITICAL| Loot["Loot outcome:\nXP orbs = value of most recent\nsacrifice * xp-loot-conversion-rate.\nNo Honor penalty by default\n(altar.loot-honor-penalty)."]

    BreakBarrel["Break the barrel block"] --> BreakTier{Live vulnerability tier}
    BreakTier -->|NORMAL or DECAYING| Prevented["Break cancelled -\n\"still consecrated\""]
    BreakTier -->|CRITICAL| Destroy["Destroy outcome (Desecration):\nHonor penalty for the breaker\n(altar.desecration-honor-penalty),\nserver-wide broadcast,\naltar record deleted outright -\nstructure must be physically rebuilt"]
```

A successful top-up (recovering from Critical, or a fresh post-desecration reconsecration) starts
`altar.reconsecration-cooldown-seconds` (default 300s) during which the *claim/build-gating radius*
stays suppressed even though Power already reads as sufficient - see
[Permissions & Access Gating](permissions-access.md). This closes the panic-deposit-mid-raid loophole.
The Loot/Destroy check itself is not affected by this cooldown - it looks only at live Power.

## Notes

- **Non-obvious behavior:** a freshly consecrated altar (Power = 0) is Critical from the instant it
  exists - it has zero claim radius and its barrel is already interactable (Loot only, since Destroy
  requires *breaking* the barrel and nothing stops that either) until someone performs a sacrifice.
  There is no grace period.
- Enchantment sacrifice valuation deliberately ignores item type - only the enchantment profile counts,
  and repeated enchantment types within one sacrifice batch are discounted
  (`altar.repeat-enchantment-decay`) to discourage volume-stuffing.
- Multiple altars per owner are fully independent - no shared Power/radius/decay/cooldown state.

## Update this diagram when touching

`altar/Altar.java`, `altar/AltarPowerMath.java`, `altar/AltarVulnerability.java`,
`altar/AltarVulnerabilityTier.java`, `altar/AltarNestingService.java`, `altar/AltarRadiusCalculator.java`,
`altar/SacrificeValuationService.java`, `listener/AltarInteractListener.java`,
`listener/AltarDesecrationListener.java`, `listener/AltarWarningListener.java`.
