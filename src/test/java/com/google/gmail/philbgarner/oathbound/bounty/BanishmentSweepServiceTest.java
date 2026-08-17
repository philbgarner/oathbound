package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BanishmentSweepServiceTest {

    private final PlayerRef victim = new PlayerRef(UUID.randomUUID());
    private final ReturnLocation returnLocation = new ReturnLocation(UUID.randomUUID(), 1.0, 64.0, 1.0, 0f, 0f);
    private final BanishmentSweepService sweepService = new BanishmentSweepService();

    @Test
    void releasesOnceNowReachesReleaseAtAndNotBefore() {
        Instant now = Instant.now();
        Banishment banishment = new Banishment(UUID.randomUUID(), victim, UUID.randomUUID(), now,
                now.plus(Duration.ofHours(5)), returnLocation, false);

        List<Banishment> notYet = sweepService.sweep(List.of(banishment), now.plus(Duration.ofHours(4)));
        assertTrue(notYet.isEmpty());
        assertTrue(!banishment.released());

        List<Banishment> released = sweepService.sweep(List.of(banishment), now.plus(Duration.ofHours(5)));
        assertEquals(1, released.size());
        assertTrue(banishment.released());
    }

    @Test
    void isIdempotentOnceAlreadyReleased() {
        Instant now = Instant.now();
        Banishment banishment = new Banishment(UUID.randomUUID(), victim, UUID.randomUUID(), now,
                now.plus(Duration.ofHours(5)), returnLocation, false);

        sweepService.sweep(List.of(banishment), now.plus(Duration.ofHours(5)));
        List<Banishment> secondSweep = sweepService.sweep(List.of(banishment), now.plus(Duration.ofHours(6)));

        assertTrue(secondSweep.isEmpty());
    }
}
