package com.google.gmail.philbgarner.oathbound.board;

import java.util.Objects;
import java.util.UUID;

/** Block coordinates of an installed Oath Board, independent of any Bukkit World reference - unlike
 * {@code NotaryLocation}, this doubles as the actual interact-time lookup key since a board is a real
 * block, not a spawned entity. */
public record BoardLocation(UUID worldId, int x, int y, int z) {
    public BoardLocation {
        Objects.requireNonNull(worldId, "worldId");
    }
}
