package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
