package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which parties have manually confirmed which oath - the backend behind
 * {@link Condition.ManualConfirm}. In-memory only for now: nothing reachable in-game creates a
 * {@code ManualConfirm}-gated clause yet (the named-party oath builder always uses {@code Immediate}),
 * so this doesn't need restart durability until that changes.
 */
public final class ManualConfirmStore {

    private record Key(UUID oathId, PlayerRef party) {
        private Key {
            Objects.requireNonNull(oathId, "oathId");
            Objects.requireNonNull(party, "party");
        }
    }

    private final Set<Key> confirmed = ConcurrentHashMap.newKeySet();

    public void confirm(UUID oathId, PlayerRef party) {
        confirmed.add(new Key(oathId, party));
    }

    public boolean isConfirmed(UUID oathId, PlayerRef party) {
        return confirmed.contains(new Key(oathId, party));
    }
}
