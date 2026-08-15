package com.google.gmail.philbgarner.oathbound.honor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HonorTiersTest {
    private final HonorTiers tiers = new HonorTiers(List.of(
            new HonorTiers.Tier(-1000L, "Faithbroken"),
            new HonorTiers.Tier(0L, "Unproven"),
            new HonorTiers.Tier(500L, "Oathkeeper"),
            new HonorTiers.Tier(2000L, "Renowned")
    ));

    @Test
    void exactThresholdMatchesThatTier() {
        assertEquals("Oathkeeper", tiers.titleFor(500L));
    }

    @Test
    void justBelowAThresholdFallsToThePreviousTier() {
        assertEquals("Unproven", tiers.titleFor(499L));
    }

    @Test
    void wellBelowEveryThresholdFallsBackToTheLowestTier() {
        assertEquals("Faithbroken", tiers.titleFor(-999_999L));
    }

    @Test
    void aboveTheTopThresholdUsesTheTopTier() {
        assertEquals("Renowned", tiers.titleFor(1_000_000L));
    }
}
