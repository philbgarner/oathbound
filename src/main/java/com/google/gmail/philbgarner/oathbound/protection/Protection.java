package com.google.gmail.philbgarner.oathbound.protection;

import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Binds one real-world chest/door block to a {@link ProtectionGroupRef}. One binding per block -
 * {@link #location()} is the natural lookup key when a player interacts with the block. */
public record Protection(UUID id, ProtectedLocation location, ProtectionType type, ProtectionGroupRef group,
                          Instant boundAt) {
    public Protection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(boundAt, "boundAt");
    }
}
