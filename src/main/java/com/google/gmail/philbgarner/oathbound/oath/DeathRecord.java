package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** {@code killer} is null for deaths with no player killer (mobs, fall damage, etc.) - old persisted
 * records predating this field deserialize with a null killer, same as a fresh non-PvP death;
 * mirrors {@link Oath}'s plain-nullable-field convention for optional persisted state (e.g.
 * {@code sealedAt}) rather than wrapping in {@link java.util.Optional}, which Gson doesn't natively
 * round-trip. */
public record DeathRecord(UUID id, PlayerRef player, PlayerRef killer, Instant timestamp) {
    public DeathRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
