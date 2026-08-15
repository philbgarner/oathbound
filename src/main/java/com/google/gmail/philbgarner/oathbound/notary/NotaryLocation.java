package com.google.gmail.philbgarner.oathbound.notary;

import java.util.Objects;
import java.util.UUID;

/** Block coordinates a Notary was installed at, independent of any Bukkit World reference - purely
 * informational (lookup at interact-time goes through the spawned entity's UUID, not this location). */
public record NotaryLocation(UUID worldId, int x, int y, int z) {
    public NotaryLocation {
        Objects.requireNonNull(worldId, "worldId");
    }
}
