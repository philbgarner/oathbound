package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BanishmentServiceTest {

    private final PlayerRef victim = new PlayerRef(UUID.randomUUID());
    private final ReturnLocation returnLocation = new ReturnLocation(UUID.randomUUID(), 1.0, 64.0, 1.0, 0f, 0f);
    private BanishmentService service;

    @BeforeEach
    void setUp() {
        // min=1h, max=72h, hoursPerCurrencyUnit=10 (hours = amount / 10), stackCap=168h
        service = new BanishmentService(1, 72, 10, 168);
    }

    @Test
    void hoursForClampsToMinimum() {
        assertEquals(1, service.hoursFor(0L));
        assertEquals(1, service.hoursFor(5L));
    }

    @Test
    void hoursForClampsToMaximum() {
        assertEquals(72, service.hoursFor(10_000L));
    }

    @Test
    void hoursForScalesLinearlyWithinBounds() {
        assertEquals(20, service.hoursFor(200L));
    }

    @Test
    void beginOrExtendStartsANewSentenceWhenNoneExists() {
        Instant now = Instant.now();
        Banishment banishment = service.beginOrExtend(Optional.empty(), victim, UUID.randomUUID(), 200L, now, returnLocation);

        assertEquals(now, banishment.servingSince());
        assertEquals(now.plus(Duration.ofHours(20)), banishment.releaseAt());
        assertFalse(banishment.released());
    }

    @Test
    void beginOrExtendExtendsRatherThanResetsAnActiveSentence() {
        Instant now = Instant.now();
        Banishment existing = service.beginOrExtend(Optional.empty(), victim, UUID.randomUUID(), 200L, now, returnLocation);
        Instant firstReleaseAt = existing.releaseAt();

        Banishment extended = service.beginOrExtend(Optional.of(existing), victim, UUID.randomUUID(), 200L,
                now.plus(Duration.ofHours(5)), returnLocation);

        assertEquals(existing, extended);
        assertEquals(firstReleaseAt.plus(Duration.ofHours(20)), extended.releaseAt());
        assertEquals(now, extended.servingSince(), "stacking preserves the original servingSince");
    }

    @Test
    void stackingIsCappedAtStackCapHoursFromOriginalServingSince() {
        Instant now = Instant.now();
        Banishment banishment = service.beginOrExtend(Optional.empty(), victim, UUID.randomUUID(), 10_000L, now, returnLocation);
        assertEquals(now.plus(Duration.ofHours(72)), banishment.releaseAt());

        // Stack a second max-length (72h) sentence: 72 + 72 = 144h, still under the 168h cap.
        banishment = service.beginOrExtend(Optional.of(banishment), victim, UUID.randomUUID(), 10_000L,
                now.plus(Duration.ofHours(10)), returnLocation);
        assertEquals(now.plus(Duration.ofHours(144)), banishment.releaseAt());

        // A third max-length stack would push total duration past 168h from servingSince - absorbed at the cap.
        banishment = service.beginOrExtend(Optional.of(banishment), victim, UUID.randomUUID(), 10_000L,
                now.plus(Duration.ofHours(20)), returnLocation);
        assertEquals(now.plus(Duration.ofHours(168)), banishment.releaseAt());
    }

    @Test
    void beginOrExtendStartsFreshOnceAPreviousSentenceHasAlreadyBeenReleased() {
        Instant now = Instant.now();
        Banishment existing = service.beginOrExtend(Optional.empty(), victim, UUID.randomUUID(), 200L, now, returnLocation);
        existing.release();

        Instant later = now.plus(Duration.ofDays(5));
        Banishment fresh = service.beginOrExtend(Optional.of(existing), victim, UUID.randomUUID(), 200L, later, returnLocation);

        assertTrue(fresh != existing);
        assertEquals(later, fresh.servingSince());
    }

    @Test
    void reduceSentenceClampsAtNow() {
        Instant now = Instant.now();
        Banishment banishment = service.beginOrExtend(Optional.empty(), victim, UUID.randomUUID(), 200L, now, returnLocation);

        banishment.reduceSentence(Duration.ofHours(1000), now);

        assertEquals(now, banishment.releaseAt());
    }
}
