package com.google.gmail.philbgarner.oathbound.villager;

import java.util.Objects;
import java.util.UUID;

/** Block coordinates a villager shop NPC was installed at, independent of any Bukkit World reference -
 * purely informational (lookup at interact-time goes through the spawned entity's UUID, not this
 * location). Mirrors {@link com.google.gmail.philbgarner.oathbound.notary.NotaryLocation}, kept as a
 * separate type so this package doesn't depend on {@code notary}. */
public record VillagerNpcLocation(UUID worldId, int x, int y, int z) {
    public VillagerNpcLocation {
        Objects.requireNonNull(worldId, "worldId");
    }
}
