package com.google.gmail.philbgarner.oathbound.protection;

import java.util.Objects;
import java.util.UUID;

/** Block coordinates of a locked chest/door, independent of any Bukkit World reference. */
public record ProtectedLocation(UUID worldId, int x, int y, int z) {
    public ProtectedLocation {
        Objects.requireNonNull(worldId, "worldId");
    }
}
