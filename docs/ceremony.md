# Ceremony Designer

A templated, item-driven shortcut for binding a group-to-individual agreement with no menus or
commands on the receiving end - just a right-click and a chat reply. Templates are admin-authored,
static config (`ceremony-templates` in `config.yml`; empty by default, so the feature is inert until a
server admin writes at least one). Paste into [mermaid.live](https://mermaid.live).

```mermaid
flowchart TD
    Author["Admin authors a ceremony-templates\nentry in config.yml"] --> Give["/oathbound-debug ceremony give\n&lt;templateId&gt; &lt;groupId&gt; [player]"]
    Give --> Item["Tagged item created:\ntemplateId + liegeGroupId embedded"]

    Item --> UsePath{How is the item used?}

    UsePath -->|Right-click another player| Interact["CeremonyInteractListener cancels\nthe vanilla interaction"] --> Prompt
    UsePath -->|"Right-click an already-placed\npressure plate / button"| BlockFlag{ceremony-block-triggers-enabled?}
    BlockFlag -->|no| Ignored[No effect]
    BlockFlag -->|yes| CreateTrigger["CeremonyTriggerListener.tryBind:\ncreate + persist a CeremonyTrigger\nat that block. Item is NOT consumed."]
    CreateTrigger --> LaterStep["Later: any player steps on\n(pressure plate) or presses\n(button) the bound block"]
    LaterStep --> Activate["activate(): the original installer\nstands in as initiator, even if\nthey're offline right now"]
    Activate --> Prompt

    Prompt["CeremonyChatListener.beginPrompt:\nsend dialogue lines to the target,\nhold a PendingCeremony in memory\nwith an expiresAt timeout"] --> Reply{Target's next chat message}

    Reply -->|matches a decline-phrase| Decline["Both parties notified.\nNothing created."]
    Reply -->|matches a confirm-phrase| Confirm[handleConfirm]
    Reply -->|"anything else / no reply\nbefore prompt-timeout-seconds"| Drop["Message ignored (falls through) /\npending entry silently expires"]

    Confirm --> ResolveGroup["Resolve target's sole\npersonally-owned territory group\n(for any TransferSpec clause) -\nerrors if they own 0 or &gt;1 groups"]
    ResolveGroup --> Withdraw["Withdraw any TributeSpec items\nfrom the target's inventory"]
    Withdraw --> Materialize["CeremonyService.materialize:\nspecs -&gt; real Clauses -\nTransferClause+PvpDeathCount,\nEscrowClause, MobKillClause, CustomFlagClause"]
    Materialize --> CreateOath["OathService.createDraft\n(bloodOath flag from template)"]
    CreateOath --> AddClauses[Add every materialized clause]
    AddClauses --> SealNow["propose -&gt; seal -&gt; activate,\nall in one call - both sides already\nconsented via the ceremony itself"]

    BreakBlock["Trigger block broken"] --> Unbind["CeremonyTriggerBreakListener\ndeletes the CeremonyTrigger record"]
```

## Notes

- Ceremonies are purely a **front-end for the Oath system** - every completed ceremony produces and
  immediately seals/activates a real `Oath`, same lifecycle as any other (see
  [Oath Lifecycle](oath-lifecycle.md)), just skipping the usual propose-then-wait gap.
- A template's clause list can include a `DiplomacyClause` (see [Diplomacy](diplomacy.md)) per the
  data model, though no shipped example currently uses one.
- No connection exists to Altars anywhere in the ceremony code.
- **Known gaps:** `ceremony-templates` ships empty, so nothing works until an admin authors YAML. There
  is no admin command to unbind a block trigger short of physically breaking the block, and no
  cooldown/rate-limit on re-triggering a plate beyond the "already has a pending prompt" guard.

## Update this diagram when touching

`ceremony/*.java`, `listener/CeremonyInteractListener.java`, `listener/CeremonyChatListener.java`,
`listener/CeremonyTriggerListener.java`, `listener/CeremonyTriggerBreakListener.java`,
`db/migrations/0014_ceremony_triggers.sql`, the `ceremony-templates` config shape.
