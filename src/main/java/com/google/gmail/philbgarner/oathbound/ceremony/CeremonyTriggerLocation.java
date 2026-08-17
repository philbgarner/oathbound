package com.google.gmail.philbgarner.oathbound.ceremony;

import java.util.Objects;
import java.util.UUID;

/** Block coordinates of a plate/button bound to a ceremony, independent of any Bukkit World reference -
 * like {@code board.BoardLocation}, this doubles as the actual interact-time lookup key since the trigger
 * is a real block, not a spawned entity. */
public record CeremonyTriggerLocation(UUID worldId, int x, int y, int z) {
    public CeremonyTriggerLocation {
        Objects.requireNonNull(worldId, "worldId");
    }
}
