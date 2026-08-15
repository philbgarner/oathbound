package com.google.gmail.philbgarner.oathbound.group;

import java.util.Objects;
import java.util.UUID;

public record PlayerRef(UUID playerId) implements EntityRef {

    /** Well-known actor recorded on ledger entries produced by automated systems (e.g. the condition
     * engine auto-fulfilling an oath) rather than a real player action. */
    public static final PlayerRef SYSTEM = new PlayerRef(new UUID(0, 0));

    public PlayerRef {
        Objects.requireNonNull(playerId, "playerId");
    }
}
