package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Banishment#reduceSentence} is the release-oath/prayer-of-intercession seam - both
 * {@code Clause.BanishmentReleaseClause} (an ally's Oath with the placer) and the altar prayer ritual
 * (a direct sacrifice) call it. Covered independently of either caller so its clamping semantics stay
 * correct regardless of who drives it. */
final class BanishmentTest {

    private final PlayerRef player = new PlayerRef(UUID.randomUUID());
    private final ReturnLocation returnLocation = new ReturnLocation(UUID.randomUUID(), 0, 64, 0, 0f, 0f);

    private Banishment banishment(Instant servingSince, Instant releaseAt) {
        return new Banishment(UUID.randomUUID(), player, UUID.randomUUID(), servingSince, releaseAt, returnLocation, false);
    }

    @Test
    void reduceSentenceCutsExactlyTheGivenAmount() {
        Instant now = Instant.now();
        Instant releaseAt = now.plus(Duration.ofHours(10));
        Banishment sentence = banishment(now.minus(Duration.ofHours(1)), releaseAt);

        sentence.reduceSentence(Duration.ofHours(4), now);

        assertEquals(releaseAt.minus(Duration.ofHours(4)), sentence.releaseAt());
        assertTrue(sentence.active(now));
    }

    @Test
    void reduceSentenceNeverGoesBeforeNow() {
        Instant now = Instant.now();
        Banishment sentence = banishment(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)));

        sentence.reduceSentence(Duration.ofHours(100), now);

        assertEquals(now, sentence.releaseAt());
        assertFalse(sentence.active(now));
    }

    @Test
    void reduceSentenceToExactlyNowEndsItImmediately() {
        Instant now = Instant.now();
        Instant releaseAt = now.plus(Duration.ofHours(5));
        Banishment sentence = banishment(now.minus(Duration.ofHours(1)), releaseAt);

        sentence.reduceSentence(Duration.ofHours(5), now);

        assertFalse(sentence.active(now));
    }
}
