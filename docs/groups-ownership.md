# Groups & Ownership

Every claim, faction, company, or kingdom is a `ProtectionGroup`. Its `owner` field is an `EntityRef` -
either a specific player (`PlayerRef`) or *another group* (`ProtectionGroupRef`) - which is how a
company can belong to a town, a town to a region, and so on. Paste into
[mermaid.live](https://mermaid.live).

## Tier hierarchy

`GroupTier` ordinal order drives every nesting/tie-break decision in the plugin (altar claim overlap,
new-altar nesting legality):

```mermaid
flowchart LR
    INDIVIDUAL --> COMPANY --> TOWN --> REGION --> KINGDOM
```

## Resolving who ultimately owns something

`OwnershipResolver.resolveTerminalOwner` walks the `owner` chain live, on every read - nothing about
the resolved owner is cached or stored:

```mermaid
flowchart TD
    Start[EntityRef current = start] --> Check{current is a\nProtectionGroupRef?}
    Check -->|no - it's a PlayerRef| Resolved["Resolved(terminal owner, hop count)"]
    Check -->|yes| Lookup{Referenced group\nexists in lookup?}
    Lookup -->|no| Unresolved1["Unresolved - fail safe\n(dangling reference)"]
    Lookup -->|yes| Hop[hops += 1] --> Depth{"hops >= ownership.\nresolver-depth-cutoff\n(default 10)?"}
    Depth -->|yes| Unresolved2["Unresolved - fail safe\n(chain too deep / suspected cycle)"]
    Depth -->|no| Follow[current = group.owner] --> Check
```



`resolveRootGroup` is the same walk restricted to group-owned-by-group links, used by
[Diplomacy](diplomacy.md) to find "the most senior group in the chain" that actually holds relations.

## Changing ownership safely

`OwnershipResolver.reassignOwner` is the **only sanctioned way** to mutate a group's `owner` -
`ProtectionGroup.setOwner` is package-private specifically to force every write through this check:

```mermaid
flowchart TD
    Call["reassignOwner(group, newOwner)"] --> CycleCheck{"wouldCreateCycle(group, newOwner)?\nwalks newOwner's chain up to\nresolver-depth-cutoff hops looking\nfor group's own id"}
    CycleCheck -->|cycle found, or chain\nunresolvable within cutoff| Reject["Reject: throw IllegalArgumentException\n(fail safe, not silent allow)"]
    CycleCheck -->|no cycle| Mutate[group.setOwner persisted]
```

This is also the exact mechanism `ConditionEngine` uses to execute a `TransferClause` (see
[Condition Engine](condition-engine.md)) and what `/oathbound-debug group transfer` calls directly.

## Membership & permissions

```mermaid
flowchart LR
    Group[ProtectionGroup] -->|has many| Member["Member(player, roleName)"]
    Group -->|has many| Role["Role(name, rankWeight,\nSet&lt;GroupPermission&gt;)"]
    Member -.roleName lookup.-> Role
    Role --> Perms["8 permissions:\nOPEN_CONTAINERS · OPEN_DOORS · BUILD\nINVITE · VOTE · BE_CANDIDATE\nTRANSFER_OWNERSHIP · ACCEPT_ON_BEHALF"]
```

`ProtectionGroup.hasPermission(player, permission)` finds the player's `Member`, resolves their `Role`
by name, and checks membership in that role's permission set - defaulting to `false` (deny) if either
lookup fails.

## Notes

- **Known gap:** the only code path that creates groups/roles/members today is
  `/oathbound-debug group create <name> [tier]`, which makes the creator an "Owner" role holding every
  permission. There is no in-game GUI or command yet for inviting members, assigning roles, or editing
  a role's permission set after creation.
- Cycle-safety is enforced **at write time only** - a plain read never re-validates an already-stored
  chain.

## Update this diagram when touching

`group/ProtectionGroup.java`, `group/GroupTier.java`, `group/GroupPermission.java`, `group/Role.java`,
`group/Member.java`, `group/EntityRef.java`, `group/OwnershipResolver.java`.
