package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

/**
 * Who a PvP {@link Bounty} is placed against - a specific player, or every member of a group (with an
 * explicit quantity). Deliberately separate from
 * {@link com.google.gmail.philbgarner.oathbound.group.EntityRef} - that type is the ownership primitive
 * used by transfer clauses and group owners, and this is a different concept that happens to overlap for
 * these two cases. PvE contracts target a mob type instead (see {@link PveContractDefinition}) - kept as
 * a separate, non-persisted config type entirely rather than a third variant here, since a
 * {@code org.bukkit.entity.EntityType} field would drag a Bukkit type into this sealed interface, and
 * Gson eagerly resolves every registered subtype's fields the moment any {@link Bounty} is
 * (de)serialized - poisoning even Solo/Group-only persistence in a domain layer that's meant to stay
 * Bukkit-free.
 */
public sealed interface BountyTarget {
    record Solo(PlayerRef player) implements BountyTarget {
    }

    record Group(ProtectionGroupRef group) implements BountyTarget {
    }
}
