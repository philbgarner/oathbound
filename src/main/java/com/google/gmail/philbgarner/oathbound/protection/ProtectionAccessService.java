package com.google.gmail.philbgarner.oathbound.protection;

import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure access-check logic for locked chests/doors - no Bukkit dependency. */
public final class ProtectionAccessService {

    private ProtectionAccessService() {
    }

    /** Fail-safe deny: a {@link Protection} whose bound group no longer exists denies everyone rather
     * than silently falling back to unprotected. */
    public static boolean canAccess(Protection protection, PlayerRef player, OwnershipResolver.GroupLookup lookup) {
        Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(lookup, "lookup");
        return lookup.findById(protection.group().groupId())
                .map(group -> group.hasPermission(player, protection.type().requiredPermission()))
                .orElse(false);
    }

    /** Groups the actor may lock a new block of the given type to - they must already be able to
     * access that type of protected block on the group. */
    public static List<ProtectionGroup> bindableGroups(PlayerRef player, ProtectionType type,
                                                         Collection<ProtectionGroup> allGroups) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(allGroups, "allGroups");
        return allGroups.stream()
                .filter(group -> group.hasPermission(player, type.requiredPermission()))
                .sorted(Comparator.comparing(ProtectionGroup::name))
                .toList();
    }
}
