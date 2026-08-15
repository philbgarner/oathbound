package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeathRecord(UUID id, PlayerRef player, Instant timestamp) {
    public DeathRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
