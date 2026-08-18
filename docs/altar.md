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
    TierCompare -->|"no altar blocks it -\nall covering altars are\nstrictly larger tier"| Consecrate["Consecrated at altar.starting-power\n(default 150), owned by whoever\nplaced the candle"]
```

This check runs **once, at consecration time only** - a larger claim later shrinking from decay never
retroactively invalidates anything already nested inside it.

## Power, decay, and vulnerability

Power is never stored as a raw number - it's recomputed live every time from `powerBaseline` (the
total as of the last sacrifice) and a linear decay over `altar.decay-days`. Radius is likewise always
`power-radius-scale * tier-multiplier * sqrt(currentPower)`.

```mermaid
stateDiagram-v2
    [*] --> NORMAL : consecrated at altar.starting-power,\ndefault 150 - comfortably above\ndecaying-threshold, so NORMAL from\nthe moment it's placed
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

- **Grace period:** a freshly consecrated altar starts at `altar.starting-power` (default 150, not a
  real sacrifice - it doesn't set a Loot-able `lastSacrificeValue`) rather than zero, so it reads
  `NORMAL` - full protection, real claim radius - from the moment it's placed, decaying on the normal
  clock like any other Power. Set `altar.starting-power: 0` to restore the old zero-grace behavior.
- Enchantment sacrifice valuation deliberately ignores item type - only the enchantment profile counts.
  Per enchantment, `baseWeight = (enchantment-weight-scale / maxLevel) * rarityMultiplier`, so maxing
  out an enchantment is worth `enchantment-weight-scale * rarityMultiplier` regardless of how many
  levels it has - and rarer enchantments (by Minecraft's own selection weight, bucketed into
  COMMON/UNCOMMON/RARE/VERY_RARE via `altar.enchantment-rarity-multiplier`) are worth more than common
  ones even when both are equally "maxed out." Repeated enchantment types within one sacrifice batch
  are discounted (`altar.repeat-enchantment-decay`) to discourage volume-stuffing; the rarity multiplier
  applies once per enchantment type, not per repeated occurrence.
- Rarity is bucketed from `Enchantment.getWeight()` via `EnchantmentRarityTier.of(...)`, not Bukkit's
  own `EnchantmentRarity`/`getRarity()` - those are deprecated for removal on the Paper API version this
  plugin builds against, so valuation is intentionally built on the plain, non-deprecated weight int
  instead, bucketed using vanilla's own historical weight bands (COMMON=10, UNCOMMON=5, RARE=2,
  VERY_RARE=1).
- Multiple altars per owner are fully independent - no shared Power/radius/decay/cooldown state.

## Update this diagram when touching

`altar/Altar.java`, `altar/AltarPowerMath.java`, `altar/AltarVulnerability.java`,
`altar/AltarVulnerabilityTier.java`, `altar/AltarNestingService.java`, `altar/AltarRadiusCalculator.java`,
`altar/SacrificeValuationService.java`, `altar/EnchantmentRarityLookup.java`,
`altar/EnchantmentRarityTier.java`, `listener/AltarInteractListener.java`,
`listener/AltarDesecrationListener.java`, `listener/AltarWarningListener.java`,
`listener/AltarConsecrationListener.java`, `gui/AltarSacrificeGuiListener.java`,
`config/OathboundConfig.java` (altar section).
