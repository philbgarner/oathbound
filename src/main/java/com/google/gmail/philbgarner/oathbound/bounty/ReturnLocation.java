package com.google.gmail.philbgarner.oathbound.bounty;

import java.util.Objects;
import java.util.UUID;

/**
 * Full-fidelity teleport target for restoring a player once their banishment ends. Deliberately not
 * reusing {@code NotaryLocation}/{@code AltarLocation} (both block-integer-only, purely informational) -
 * restoring a player to exactly where they were needs sub-block position and facing, which those don't
 * carry.
 */
public record ReturnLocation(UUID worldId, double x, double y, double z, float yaw, float pitch) {
    public ReturnLocation {
        Objects.requireNonNull(worldId, "worldId");
    }
}
