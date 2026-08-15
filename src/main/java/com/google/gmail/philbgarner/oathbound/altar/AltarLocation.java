package com.google.gmail.philbgarner.oathbound.altar;

import java.util.Objects;
import java.util.UUID;

/** Block coordinates of an altar's barrel, independent of any Bukkit World reference. */
public record AltarLocation(UUID worldId, int x, int y, int z) {
    public AltarLocation {
        Objects.requireNonNull(worldId, "worldId");
    }

    /** Whether {@code point} falls within {@code radius} blocks of this location, in the same world
     * (flat, XZ-plane distance - Y is ignored, matching the existing claim-radius shape). */
    public boolean isWithin(AltarLocation point, int radius) {
        if (!worldId.equals(point.worldId())) {
            return false;
        }
        long dx = x - point.x();
        long dz = z - point.z();
        return dx * dx + dz * dz <= (long) radius * radius;
    }
}
