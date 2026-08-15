package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.GroupPermission;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Pure logic for whether a block break/place is inside an Altar's live claim radius, and if so
 * whether the acting player is allowed to build there - no Bukkit dependency. */
public final class ClaimAccessService {

    private ClaimAccessService() {
    }

    /** The altar (if any) whose live radius currently covers {@code blockLoc}. Radius is recomputed
     * live per altar, never stored - see {@link AltarRadiusCalculator}. If several altars cover the
     * same point, the one with the smallest current radius wins (closest approximation of the master
     * plan's smallest-scope-first rule without full nesting resolution, which is a later phase). */
    public static Optional<Altar> coveringAltar(AltarLocation blockLoc, Collection<Altar> altars,
                                                 AltarRadiusCalculator calculator, OwnershipResolver.GroupLookup lookup) {
        Objects.requireNonNull(blockLoc, "blockLoc");
        Objects.requireNonNull(altars, "altars");
        Objects.requireNonNull(calculator, "calculator");
        Objects.requireNonNull(lookup, "lookup");

        Altar closest = null;
        int closestRadius = Integer.MAX_VALUE;
        for (Altar altar : altars) {
            if (!altar.location().worldId().equals(blockLoc.worldId())) {
                continue;
            }
            int radius = calculator.radiusFor(altar, lookup);
            if (radius <= 0) {
                continue;
            }
            if (isWithinRadius(altar.location(), blockLoc, radius) && radius < closestRadius) {
                closest = altar;
                closestRadius = radius;
            }
        }
        return Optional.ofNullable(closest);
    }

    /** Fail-safe deny: an altar owned by a group that no longer resolves, or whose owning group lacks
     * {@link GroupPermission#BUILD} for this player, denies the build. A directly player-owned altar is
     * always buildable by that specific player. */
    public static boolean canBuild(Altar altar, PlayerRef player, OwnershipResolver.GroupLookup lookup) {
        Objects.requireNonNull(altar, "altar");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(lookup, "lookup");

        EntityRef owner = altar.owner();
        if (owner instanceof PlayerRef ownerPlayer) {
            return ownerPlayer.equals(player);
        }
        if (owner instanceof ProtectionGroupRef groupRef) {
            return lookup.findById(groupRef.groupId())
                    .map(group -> group.hasPermission(player, GroupPermission.BUILD))
                    .orElse(false);
        }
        return false;
    }

    private static boolean isWithinRadius(AltarLocation center, AltarLocation point, int radius) {
        long dx = center.x() - point.x();
        long dz = center.z() - point.z();
        return dx * dx + dz * dz <= (long) radius * radius;
    }
}
