package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MobKillRecord(UUID id, PlayerRef killer, String mobTypeName, Instant timestamp) {
    public MobKillRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(killer, "killer");
        Objects.requireNonNull(mobTypeName, "mobTypeName");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
