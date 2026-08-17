package com.google.gmail.philbgarner.oathbound.bounty;

/** Thrown when a player has already placed {@code bounty.max-placements-per-24h} contracts in the last
 * 24 hours - a hard cap independent of heat-scaling fees. */
public final class BountyPlacementLimitException extends RuntimeException {
    public BountyPlacementLimitException(String message) {
        super(message);
    }
}
