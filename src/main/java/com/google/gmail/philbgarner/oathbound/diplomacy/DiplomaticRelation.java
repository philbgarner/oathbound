package com.google.gmail.philbgarner.oathbound.diplomacy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** {@code groupA}/{@code groupB} are always the resolved root groups of whichever chains were involved
 * (see {@link DiplomacyService}), stored in canonical sorted order so an unordered pair never produces
 * two records. Never explicitly stores {@link DiplomaticState#NEUTRAL} - see that enum's javadoc. */
public record DiplomaticRelation(UUID groupA, UUID groupB, DiplomaticState state, Instant since) {
    public DiplomaticRelation {
        Objects.requireNonNull(groupA, "groupA");
        Objects.requireNonNull(groupB, "groupB");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(since, "since");
    }
}
