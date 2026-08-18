# Permissions & Access Gating

How a locked chest/door decides who gets in, and how block break/place is gated inside an altar's live
claim radius. Both ultimately bottom out in `ProtectionGroup.hasPermission`
(see [Groups & Ownership](groups-ownership.md)). Paste into [mermaid.live](https://mermaid.live).

## Chest / door locking and access

```mermaid
flowchart TD
    Interact["Player right-clicks a\nchest or door"] --> Holding{"Holding the lock tool?\n(protection.lock-tool-material,\ndefault TRIPWIRE_HOOK)"}

    Holding -->|yes| OpenGui["Cancel vanilla interaction,\nopen ProtectionLockGui"]
    OpenGui --> ListGroups["List groups where the actor already\nholds this block type's required\npermission (OPEN_CONTAINERS/OPEN_DOORS)"]
    ListGroups --> Choice{Player picks}
    Choice -->|a group| SaveProtection["Create/overwrite the Protection\nrecord: this location -> that group.\nPersisted async."]
    Choice -->|Unlock| RemoveProtection["Delete the Protection record"]

    Holding -->|no| Existing{"Protection record bound\nto this exact block?"}
    Existing -->|no| Allow1["Allow - unprotected block,\nnormal vanilla interaction"]
    Existing -->|yes| GroupLookup{Bound group still resolvable?}
    GroupLookup -->|no, deleted| DenyFailSafe["Deny - fail closed"]
    GroupLookup -->|yes| PermCheck{"group.hasPermission(player,\nrequiredPermission)?"}
    PermCheck -->|yes| Allow2[Allow interaction]
    PermCheck -->|no| Deny["Cancel event -\n\"This container/door is locked.\""]
```

Doors normalize the top/bottom half to one canonical location before lookup, so locking either half
locks the whole door.

## Altar build gating (block break/place inside a claim)

```mermaid
flowchart TD
    BuildAttempt["BlockBreakEvent /\nBlockPlaceEvent"] --> FindAltar["ClaimAccessService.protectedAltar:\nfind the altar(s) whose live radius\ncovers this block"]
    FindAltar --> Overlap{Multiple altars overlap here?}
    Overlap -->|yes| Resolve["Smallest current radius wins.\nTie -> smallest GroupTier ordinal.\nStill tied -> altar UUID compare\n(arbitrary but deterministic)."]
    Overlap -->|no| Single[That one altar, if any]
    Resolve --> Cooldown
    Single --> Cooldown{"Altar mid reconsecration\ncooldown? (altar.cooledDown)"}
    Cooldown -->|yes| Excluded["Excluded from consideration -\ntreated as if no altar covers\nthis block right now"]
    Cooldown -->|no| OwnerType{Altar owner is...}
    OwnerType -->|a specific player| ExactMatch{Actor == that player?}
    OwnerType -->|a group| GroupPerm{"group.hasPermission(actor, BUILD)?\nfail-safe deny if group unresolved"}
    ExactMatch -->|yes| AllowBuild[Allow]
    ExactMatch -->|no| DenyBuild[Cancel event]
    GroupPerm -->|yes| AllowBuild
    GroupPerm -->|no| DenyBuild
```

New-altar placement legality (a one-time check at consecration, not ongoing) lives in
[Altars](altar.md) since it's part of the consecration flow, not everyday access gating.

## Notes

- `ClaimAccessService` reuses `AltarRadiusCalculator` rather than a second territory system - claim
  radius and build-gating radius are always the exact same live number.
- Because both radius and cooldown are live-computed, no periodic sweep is needed to keep access
  decisions correct - every check reads current truth.
- **Known gap:** no in-game command/GUI exists yet to view *why* a build was denied beyond the chat
  message - a player has to reason about which altar/group is involved themselves.

## Update this diagram when touching

`protection/ProtectionType.java`, `protection/Protection.java`, `protection/ProtectionAccessService.java`,
`listener/ProtectionLockListener.java`, `gui/ProtectionLockGui.java`,
`altar/ClaimAccessService.java`, `altar/AltarRadiusCalculator.java`, `listener/ClaimBuildGuardListener.java`.
