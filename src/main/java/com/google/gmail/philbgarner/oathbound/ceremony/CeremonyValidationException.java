package com.google.gmail.philbgarner.oathbound.ceremony;

/** Thrown when a ceremony can't be sealed as configured - an ambiguous/missing territory group, an
 * unresolvable tribute recipient, etc. Carries a player-facing message directly, same shape as
 * {@code bounty.BountyPlacementLimitException}. */
public final class CeremonyValidationException extends RuntimeException {
    public CeremonyValidationException(String message) {
        super(message);
    }
}
