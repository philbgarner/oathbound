package com.google.gmail.philbgarner.oathbound.honor;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Cosmetic title ladder derived from a player's Honor score - e.g. "Faithbroken" for deeply negative
 * Honor, "Oathkeeper" once positive, "Renowned" at the top. Thresholds are config-driven (tune-later,
 * don't hardcode), not baked into an enum. */
public final class HonorTiers {

    public record Tier(long threshold, String title) {
        public Tier {
            Objects.requireNonNull(title, "title");
        }
    }

    private final List<Tier> descendingByThreshold;

    public HonorTiers(List<Tier> tiers) {
        Objects.requireNonNull(tiers, "tiers");
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("At least one honor tier must be configured");
        }
        this.descendingByThreshold = tiers.stream()
                .sorted(Comparator.comparingLong(Tier::threshold).reversed())
                .toList();
    }

    /** The highest tier whose threshold the given Honor score meets or exceeds; falls back to the
     * lowest configured tier if the score is below every threshold. */
    public String titleFor(long honorValue) {
        for (Tier tier : descendingByThreshold) {
            if (honorValue >= tier.threshold()) {
                return tier.title();
            }
        }
        return descendingByThreshold.get(descendingByThreshold.size() - 1).title();
    }
}
