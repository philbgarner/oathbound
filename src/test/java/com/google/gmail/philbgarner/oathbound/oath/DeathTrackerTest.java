package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DeathTrackerTest {

    private final PlayerRef player = new PlayerRef(UUID.randomUUID());

    @Test
    void countsOnlyDeathsAtOrAfterSince() {
        DeathTracker tracker = new DeathTracker();
        Instant since = Instant.now();

        tracker.loadExisting(new DeathRecord(UUID.randomUUID(), player, since.minusSeconds(60)));
        assertEquals(0, tracker.countSince(player, since));

        tracker.recordDeath(player);
        tracker.recordDeath(player);
        assertEquals(2, tracker.countSince(player, since));
    }

    @Test
    void doesNotCountOtherPlayers() {
        DeathTracker tracker = new DeathTracker();
        PlayerRef other = new PlayerRef(UUID.randomUUID());
        Instant since = Instant.now();

        tracker.recordDeath(other);
        assertEquals(0, tracker.countSince(player, since));
    }
}
