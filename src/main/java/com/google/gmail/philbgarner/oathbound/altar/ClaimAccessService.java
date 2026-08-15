package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.GroupPermission;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Pure logic for whether a block break/place is inside an Altar's live claim radius, and if so
 * whether the acting player is allowed to build there - no Bukkit dependency. */
public final class ClaimAccessService {

    private ClaimAccessService() {
    }

    /** The altar (if any) whose live radius currently covers {@code blockLoc}. Radius is recomputed
     * live per altar from its current Power (see {@link AltarPowerMath}) and {@link AltarRadiusCalculator}.
     * If several altars cover the same point, the smallest current radius wins; ties are broken by the
     * smallest {@link com.google.gmail.philbgarner.oathbound.group.GroupTier} ordinal, then by the
     * altar's id as an arbitrary deterministic fallback. This answers "whose ruleset applies to this
     * exact block right now" - a distinct, ongoing question from new-altar placement legality, which
     * {@link AltarNestingService} handles separately. */
    public static Optional<Altar> coveringAltar(AltarLocation blockLoc, Collection<Altar> altars,
                                                 Instant now, int decayDays,
                                                 AltarRadiusCalculator calculator, OwnershipResolver.GroupLookup lookup) {
        Objects.requireNonNull(blockLoc, "blockLoc");
        Objects.requireNonNull(altars, "altars");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(calculator, "calculator");
        Objects.requireNonNull(lookup, "lookup");

        Altar closest = null;
        int closestRadius = Integer.MAX_VALUE;
        GroupTier closestTier = null;
        for (Altar altar : altars) {
            if (!altar.location().worldId().equals(blockLoc.worldId())) {
                continue;
            }
            long power = altar.currentPower(now, decayDays);
            GroupTier tier = AltarRadiusCalculator.tierOf(altar.owner(), lookup);
            int radius = calculator.radiusFor(power, tier);
            if (radius <= 0) {
                continue;
            }
            if (!altar.location().isWithin(blockLoc, radius)) {
                continue;
            }
            boolean better = closest == null
                    || radius < closestRadius
                    || (radius == closestRadius && tier.ordinal() < closestTier.ordinal())
                    || (radius == closestRadius && tier.ordinal() == closestTier.ordinal()
                            && altar.id().compareTo(closest.id()) < 0);
            if (better) {
                closest = altar;
                closestRadius = radius;
                closestTier = tier;
            }
        }
        return Optional.ofNullable(closest);
    }

    /** Like {@link #coveringAltar}, but also excludes an altar that's mid-reconsecration-cooldown - it's
     * unprotected even though its Power/radius already read as sufficient. Used by every live
     * protection check (build gating, monster-spawn suppression); {@link #coveringAltar} itself stays
     * cooldown-agnostic since some callers (debug info) want the raw covering altar regardless. */
    public static Optional<Altar> protectedAltar(AltarLocation blockLoc, Collection<Altar> altars,
                                                  Instant now, int decayDays,
                                                  AltarRadiusCalculator calculator, OwnershipResolver.GroupLookup lookup) {
        return coveringAltar(blockLoc, altars, now, decayDays, calculator, lookup)
                .filter(altar -> altar.cooledDown(now));
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
}
