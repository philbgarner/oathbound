package com.google.gmail.philbgarner.oathbound.group;

import java.util.Objects;
import java.util.UUID;

public record PlayerRef(UUID playerId) implements EntityRef {
    public PlayerRef {
        Objects.requireNonNull(playerId, "playerId");
    }
}
