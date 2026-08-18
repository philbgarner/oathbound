package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AltarTest {

    private final PlayerRef owner = new PlayerRef(UUID.randomUUID());
    private final AltarLocation location = new AltarLocation(UUID.randomUUID(), 0, 64, 0);
    private final Instant consecratedAt = Instant.now();

    @Test
    void fourArgConstructorStartsAtZeroPower() {
        Altar altar = new Altar(UUID.randomUUID(), owner, location, consecratedAt);

        assertEquals(0L, altar.currentPower(consecratedAt, 5));
        assertEquals(AltarVulnerabilityTier.CRITICAL,
                AltarVulnerability.classify(altar.currentPower(consecratedAt, 5), 10L, 100L));
    }

    @Test
    void fiveArgConstructorStartsAtTheGivenPower() {
        Altar altar = new Altar(UUID.randomUUID(), owner, location, consecratedAt, 150L);

        assertEquals(150L, altar.currentPower(consecratedAt, 5));
        assertEquals(AltarVulnerabilityTier.NORMAL,
                AltarVulnerability.classify(altar.currentPower(consecratedAt, 5), 10L, 100L));
    }

    @Test
    void startingPowerIsNotCountedAsARealSacrifice() {
        Altar altar = new Altar(UUID.randomUUID(), owner, location, consecratedAt, 150L);

        assertEquals(0L, altar.lastSacrificeValue());
    }

    @Test
    void startingPowerDecaysOnTheNormalClock() {
        Altar altar = new Altar(UUID.randomUUID(), owner, location, consecratedAt, 150L);

        Instant halfwayThroughDecay = consecratedAt.plus(Duration.ofDays(2).plusHours(12));
        assertEquals(75L, altar.currentPower(halfwayThroughDecay, 5));

        Instant fullyDecayed = consecratedAt.plus(Duration.ofDays(5));
        assertEquals(0L, altar.currentPower(fullyDecayed, 5));
    }

    @Test
    void fiveArgConstructorHasNoReconsecrationCooldown() {
        Altar altar = new Altar(UUID.randomUUID(), owner, location, consecratedAt, 150L);

        assertTrue(altar.cooledDown(consecratedAt));
    }

    @Test
    void sixArgConstructorSeedsTheReconsecrationCooldown() {
        Duration cooldown = Duration.ofSeconds(300);
        Altar altar = new Altar(UUID.randomUUID(), owner, location, consecratedAt, 150L, cooldown);

        assertFalse(altar.cooledDown(consecratedAt));
        assertFalse(altar.cooledDown(consecratedAt.plusSeconds(299)));
        assertTrue(altar.cooledDown(consecratedAt.plusSeconds(300)));
    }

    @Test
    void raidRazeReplaceNoLongerGrantsInstantProtection() {
        // A raider destroys a Critical altar (Desecration hard-resets Power and deletes the record) and
        // immediately rebuilds barrel+capstone+candle on the same spot - this is exactly that rebuild.
        Duration cooldown = Duration.ofSeconds(300);
        Altar rebuilt = new Altar(UUID.randomUUID(), owner, location, consecratedAt, 150L, cooldown);

        assertFalse(rebuilt.cooledDown(consecratedAt),
                "a freshly (re)consecrated altar must not bypass the reconsecration cooldown");
    }
}
