package com.google.gmail.philbgarner.oathbound.altar;

import java.util.Objects;
import java.util.UUID;

/** Block coordinates of an altar's barrel, independent of any Bukkit World reference. */
public record AltarLocation(UUID worldId, int x, int y, int z) {
    public AltarLocation {
        Objects.requireNonNull(worldId, "worldId");
    }
}
