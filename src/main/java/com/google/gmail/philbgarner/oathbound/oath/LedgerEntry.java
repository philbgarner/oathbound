package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(UUID id, UUID oathId, OathState fromState, OathState toState, Instant timestamp,
                           PlayerRef actor) {
    public LedgerEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(oathId, "oathId");
        Objects.requireNonNull(toState, "toState");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
