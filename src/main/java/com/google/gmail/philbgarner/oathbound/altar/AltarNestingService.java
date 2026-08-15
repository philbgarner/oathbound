package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Legality check for placing a brand-new altar, run once at consecration time against the live state
 * of every other altar at that instant - never re-validated later, so a larger claim shrinking from
 * decay never retroactively invalidates something already nested inside it.
 *
 * Checked smallest-scope-first ({@link GroupTier} ordinal): a new altar is illegal if it falls inside
 * an existing altar's live radius whose tier is the same or smaller (peer-or-smaller overlap blocks -
 * you can't plant your claim inside someone else's claim of comparable or lesser scope), but legal if
 * it only overlaps a strictly larger tier (nesting under a liege - the intended federation/vassalage
 * case).
 */
public final class AltarNestingService {

    private AltarNestingService() {
    }

    public record NestingResult(boolean legal, Optional<Altar> blockingAltar) {
        static NestingResult ok() {
            return new NestingResult(true, Optional.empty());
        }

        static NestingResult blockedBy(Altar altar) {
            return new NestingResult(false, Optional.of(altar));
        }
    }

    public static NestingResult checkPlacement(AltarLocation newLocation, GroupTier newTier,
                                                Collection<Altar> existingAltars, Instant now, int decayDays,
                                                AltarRadiusCalculator calculator, OwnershipResolver.GroupLookup lookup) {
        Objects.requireNonNull(newLocation, "newLocation");
        Objects.requireNonNull(newTier, "newTier");
        Objects.requireNonNull(existingAltars, "existingAltars");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(calculator, "calculator");
        Objects.requireNonNull(lookup, "lookup");

        List<Altar> smallestTierFirst = existingAltars.stream()
                .filter(altar -> altar.location().worldId().equals(newLocation.worldId()))
                .sorted(Comparator.comparingInt(altar -> AltarRadiusCalculator.tierOf(altar.owner(), lookup).ordinal()))
                .toList();

        for (Altar existing : smallestTierFirst) {
            GroupTier existingTier = AltarRadiusCalculator.tierOf(existing.owner(), lookup);
            long power = existing.currentPower(now, decayDays);
            int radius = calculator.radiusFor(power, existingTier);
            if (radius <= 0) {
                continue;
            }
            if (!existing.location().isWithin(newLocation, radius)) {
                continue;
            }
            if (existingTier.ordinal() <= newTier.ordinal()) {
                return NestingResult.blockedBy(existing);
            }
        }
        return NestingResult.ok();
    }
}
