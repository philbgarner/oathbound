# Notary Negotiation & Public Oath Board

How a named-party oath gets drafted and proposed, and how a witnessed oath becomes visible to other
players. Paste into [mermaid.live](https://mermaid.live).

## Drafting and proposing

```mermaid
flowchart TD
    Entry{Entry point} -->|"Right-click a Notary\n(/oathbound-notary install)"| Hub["Notary hub GUI:\nstart a draft, review oaths\nproposed to you (any Notary -\nmailbox is scoped to the player,\nnot the physical NPC), or open\nthe Bounty Board"]
    Entry -->|"Right-click the Sealing Table\n(configured block, default LECTERN)"| SkipHub["Face-to-face shortcut -\nskips the hub, straight to\nthe draft prompt"]

    Hub --> Draft
    SkipHub --> Draft["Type counterparty's name in chat\n-&gt; DRAFT oath created"]
    Draft --> Build["Chest-GUI clause builder:\nTransfer / CustomFlag / KillCount /\nEscrow buttons, plus Add Witness\n(free text/numbers collected via\nclose-GUI-then-type-in-chat)"]
    Build --> HasClause{At least one clause attached?}
    HasClause -->|yes| Propose["Propose"]
    HasClause -->|no| Build

    Propose --> CapCheck{"Recipient already at\nnotary.pending-offer-cap-per-player\n(default 10)?"}
    CapCheck -->|yes| Rejected[Proposal rejected]
    CapCheck -->|no| Proposed["Oath -&gt; PROPOSED,\nappears on recipient's\n/oathbound-oath pending board"]

    Proposed --> RecipientAction{"Recipient acts within\nnotary.negotiation-expiry-days\n(default 7)?"}
    RecipientAction -->|Sign| Sealed["-&gt; SEALED -&gt; ACTIVE\n(same as accepting an open contract)"]
    RecipientAction -->|Decline| Voided1["-&gt; VOIDED"]
    RecipientAction -->|"No response in time"| AutoVoid["NegotiationExpiryService sweep:\n-&gt; VOIDED, neutral, no Honor change"]
```

Recipients can only **sign or decline** today - the master plan's full
`DRAFT → OFFERED → COUNTERED → ...` negotiation state machine (editable counter-offers) isn't
implemented.

## Public Oath Board posting

```mermaid
flowchart TD
    Transition["An oath transitions state\n(recorded in the Ledger)"] --> Witnessed{oath.witnesses\nnon-empty?}
    Witnessed -->|no| Silent1["Never posts anywhere -\nunwitnessed oaths stay private"]
    Witnessed -->|yes| ToState{toState}
    ToState -->|"SEALED / FULFILLED / BROKEN"| Postable["OathBoardEligibility.isPostable\n= true"]
    ToState -->|"PROPOSED / ACTIVE / VOIDED"| Silent2["Silent - proposing, activating,\nand voiding never post"]

    Postable --> BloodBreak{"Witnessed Blood Oath\nbreaking BROKEN?"}
    BloodBreak -->|yes| Broadcast["Server-wide dramatic\nchat broadcast\n(mirrors Altar desecration)"]
    BloodBreak -->|no| BoardOnly["Visible only by browsing\na board - no broadcast,\nto avoid chat spam"]

    Broadcast --> Query
    BoardOnly --> Query["Board GUI opened by a player -\nlive-queries the Ledger + oath\ncache fresh every time\n(oath-board.feed-size entries\nscanned back, default 50)"]
    Query --> BoardType{Board is bound\nregional or capital?}
    BoardType -->|"regional (bound to\na ProtectionGroup)"| Regional["Shows only postings where\neither party is currently\na member of that group"]
    BoardType -->|"capital (unbound)"| Capital["Shows everything postable"]
```

## Notes

- Nothing about a posting is separately stored - both the Notary mailbox and the Oath Board recompute
  their feed from the Ledger/oath cache every time they're opened, the same pattern every board-style
  GUI in this plugin uses.
- **Not implemented:** "significant Honor tier crossings" as board postings - unlike an oath state
  transition, a tier crossing isn't reconstructable from the Ledger alone and would need its own
  persisted feed.

## Update this diagram when touching

`notary/*.java`, `oath/OathBoardEligibility.java`, `oath/NegotiationExpiryService.java`,
`board/*.java`, `listener/CeremonyChatListener.java` isn't part of this flow but shares the same
propose/seal/activate mechanics - see [Ceremony Designer](ceremony.md).
