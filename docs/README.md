# Oathbound Logic Diagrams

Visual documentation of how Oathbound's systems actually work, for players and server owners who want
the flow rather than the source. Each doc is a short intro plus one or more
[Mermaid](https://mermaid.js.org/) diagrams - paste any code block into
[mermaid.live](https://mermaid.live) to view, zoom, or export it as an image.

These are derived directly from the code in [`src/`](../src/main/java/com/google/gmail/philbgarner/oathbound)
as of when each doc was last touched, not from the design doc or aspirational behavior - anything
marked "known limitation" or "not implemented" in a diagram's notes is a real, current gap.

## Core mechanics

- [Oath Lifecycle](oath-lifecycle.md) - the `DRAFT → PROPOSED → SEALED → ACTIVE → FULFILLED/BROKEN/VOIDED`
  state machine every oath moves through, including the open-oath and ceremony shortcuts.
- [Condition Engine](condition-engine.md) - how an `ACTIVE` oath's clauses get evaluated and executed
  every tick, and how each `Condition` primitive (including `AND`/`OR`/`NOT` composition) resolves.
- [Escrow](escrow.md) - deposit-on-add, atomic release-schedule firing, the currency-vs-item split, and
  claim expiry.

## Territory, groups, and access

- [Groups & Ownership](groups-ownership.md) - the `ProtectionGroup` tier hierarchy, live cycle-safe
  ownership-chain resolution, and the membership/role/permission model.
- [Permissions & Access Gating](permissions-access.md) - chest/door locking and altar build-gating,
  including claim-overlap resolution.
- [Altars](altar.md) - consecration and nesting legality, the Power decay curve, the three
  vulnerability tiers, and the Destroy/Loot desecration outcomes.

## Diplomacy and social systems

- [Diplomacy](diplomacy.md) - the `NEUTRAL/WAR/PEACE/ALLIANCE` relation states, unilateral war
  declarations vs. treaty oaths, root-of-chain authority, and the PvP-gating side effect.
- [Ceremony Designer](ceremony.md) - templated item/block-trigger oaths that skip the usual
  propose-and-wait handshake.
- [Honor Scoring](honor.md) - how `FULFILLED`/`BROKEN` move a player's global Honor, severity scaling,
  Blood Oath amplification, and cosmetic title tiers.
- [Notary Negotiation & Public Oath Board](notary-oath-board.md) - drafting/proposing a named-party
  oath, and how witnessed activity becomes visible on a board.

## PvP economy

- [Bounty / Kill Contracts & Banishment](bounty-banishment.md) - placement/heat-fee pricing, head-drop
  fulfillment, banishment sentencing, and the separate PvE contract path.

## Keeping these current

**If you touch logic under [`src/`](../src/main/java/com/google/gmail/philbgarner/oathbound) that a
diagram above documents, update that diagram in the same change.** Each doc ends with an
"Update this diagram when touching" list of the exact files it's derived from - check that list before
you assume a change is out of scope for the docs.
