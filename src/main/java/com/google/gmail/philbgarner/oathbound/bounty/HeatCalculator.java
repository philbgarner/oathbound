package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathState;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Stateless bounty-placement economics. Heat - how "hot" a target already is - is never persisted, like
 * Altar Power: it's recomputed live from the existing {@link Bounty} collection every time it's needed,
 * as a linear decay per overlapping active bounty over a configured window. The first bounty on a target
 * is cheap; stacking a fifth is expensive; heat cools back down given time.
 */
public final class HeatCalculator {
    private HeatCalculator() {
    }

    public static double heatOf(BountyTarget target, Collection<Bounty> allBounties,
                                 Function<UUID, Optional<ProtectionGroup>> groupLookup,
                                 Instant now, Duration decayWindow) {
        double heat = 0.0;
        for (Bounty bounty : allBounties) {
            if (bounty.status() != BountyStatus.ACTIVE || !overlaps(bounty.target(), target, groupLookup)) {
                continue;
            }
            Duration age = Duration.between(bounty.placedAt(), now);
            if (age.isNegative() || age.compareTo(decayWindow) >= 0) {
                continue;
            }
            heat += 1.0 - (double) age.toMillis() / decayWindow.toMillis();
        }
        return heat;
    }

    /** Two targets overlap if they're the same player or the same group, or if one is a Solo player
     * who's a current member of the other's targeted Group. */
    private static boolean overlaps(BountyTarget a, BountyTarget b, Function<UUID, Optional<ProtectionGroup>> groupLookup) {
        if (a instanceof BountyTarget.Solo soloA && b instanceof BountyTarget.Solo soloB) {
            return soloA.player().equals(soloB.player());
        }
        if (a instanceof BountyTarget.Group groupA && b instanceof BountyTarget.Group groupB) {
            return groupA.group().equals(groupB.group());
        }
        PlayerRef solo = a instanceof BountyTarget.Solo soloA ? soloA.player()
                : b instanceof BountyTarget.Solo soloB ? soloB.player() : null;
        BountyTarget.Group group = a instanceof BountyTarget.Group groupA ? groupA
                : b instanceof BountyTarget.Group groupB ? groupB : null;
        if (solo == null || group == null) {
            return false;
        }
        return groupLookup.apply(group.group().groupId())
                .map(g -> g.members().stream().anyMatch(member -> member.player().equals(solo)))
                .orElse(false);
    }

    public static long feeFor(double heat, long baseFee, double heatFeeMultiplier) {
        return Math.round(baseFee * (1.0 + heat * heatFeeMultiplier));
    }

    public static int placementsInLast24h(PlayerRef placer, Collection<Bounty> allBounties, Instant now) {
        Instant cutoff = now.minus(Duration.ofHours(24));
        int count = 0;
        for (Bounty bounty : allBounties) {
            if (bounty.placer().equals(placer) && bounty.placedAt().isAfter(cutoff)) {
                count++;
            }
        }
        return count;
    }

    /** Approximates "the target broke an oath with the placer recently" as "any oath between the pair
     * transitioned to BROKEN within the window," regardless of who was actually at fault - the Ledger has
     * no fault-attribution concept (same known limitation as the Honor system's own breach handling). */
    public static boolean recentBreachBetween(PlayerRef placer, BountyTarget target, Collection<Oath> allOaths,
                                               Function<UUID, Optional<ProtectionGroup>> groupLookup,
                                               Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        for (Oath oath : allOaths) {
            if (oath.state() != OathState.BROKEN || oath.resolvedAt() == null || oath.resolvedAt().isBefore(cutoff)) {
                continue;
            }
            if (!oath.parties().contains(placer)) {
                continue;
            }
            if (targetInvolvedIn(target, oath.parties(), groupLookup)) {
                return true;
            }
        }
        return false;
    }

    private static boolean targetInvolvedIn(BountyTarget target, List<PlayerRef> parties,
                                             Function<UUID, Optional<ProtectionGroup>> groupLookup) {
        if (target instanceof BountyTarget.Solo solo) {
            return parties.contains(solo.player());
        }
        if (target instanceof BountyTarget.Group group) {
            return groupLookup.apply(group.group().groupId())
                    .map(g -> g.members().stream().anyMatch(member -> parties.contains(member.player())))
                    .orElse(false);
        }
        return false;
    }
}
