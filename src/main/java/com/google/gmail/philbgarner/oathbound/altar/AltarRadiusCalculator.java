package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

import java.util.Map;
import java.util.Objects;

/**
 * Radius is a live function of current Power - recomputed on demand, never stored. The exact curve
 * (sqrt: diminishing returns per unit Power) is a placeholder per the design doc's "tune-later, don't
 * hardcode" guidance; only the shape (0 at 0 Power, tier multiplies the same Power into a bigger
 * radius) matters until the sacrifice/decay system is actually built.
 */
public final class AltarRadiusCalculator {
    private final double powerRadiusScale;
    private final Map<GroupTier, Double> tierMultipliers;

    public AltarRadiusCalculator(double powerRadiusScale, Map<GroupTier, Double> tierMultipliers) {
        this.powerRadiusScale = powerRadiusScale;
        this.tierMultipliers = Map.copyOf(tierMultipliers);
    }

    public int radiusFor(Altar altar, OwnershipResolver.GroupLookup lookup) {
        return radiusFor(altar.power(), tierOf(altar.owner(), lookup));
    }

    public int radiusFor(long power, GroupTier tier) {
        if (power <= 0) {
            return 0;
        }
        double multiplier = tierMultipliers.getOrDefault(tier, 1.0);
        return (int) Math.floor(powerRadiusScale * multiplier * Math.sqrt(power));
    }

    public static GroupTier tierOf(EntityRef owner, OwnershipResolver.GroupLookup lookup) {
        Objects.requireNonNull(owner, "owner");
        if (owner instanceof ProtectionGroupRef groupRef) {
            return lookup.findById(groupRef.groupId()).map(ProtectionGroup::tier).orElse(GroupTier.INDIVIDUAL);
        }
        return GroupTier.INDIVIDUAL;
    }
}
