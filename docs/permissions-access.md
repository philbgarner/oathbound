# Permissions & Access Gating

How a locked chest/door decides who gets in - the plugin's only remaining block-level protection
mechanic (area/region-level protection is left to a dedicated plugin, e.g. WorldGuard - see
[WorldGuard Integration](worldguard-integration.md)). Bottoms out in `ProtectionGroup.hasPermission`
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

## Notes

- This is deliberately block-level only - a different granularity than a region-protection plugin like
  WorldGuard, so the two are complementary rather than redundant. Use both, or swap this out for a
  dedicated lock plugin (e.g. LWC) if you'd rather not maintain two systems.

## Update this diagram when touching

`protection/ProtectionType.java`, `protection/Protection.java`, `protection/ProtectionAccessService.java`,
`listener/ProtectionLockListener.java`, `gui/ProtectionLockGui.java`.
