package com.google.gmail.philbgarner.oathbound.persistence.dto;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathState;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Plain, Gson-friendly mirror of {@link Oath}'s persisted state - kept separate from the domain type
 * so persistence never relies on reflective access into Oath's private fields. */
public record OathDto(UUID id, List<PlayerRef> parties, List<PlayerRef> witnesses, List<Clause> clauses,
                       OathState state, boolean bloodOath, boolean open, Instant createdAt, Instant sealedAt,
                       Instant activatedAt, Instant resolvedAt, UUID negotiationRef, Set<Integer> fulfilledClauseIndices) {

    public static OathDto from(Oath oath) {
        return new OathDto(oath.id(), oath.parties(), oath.witnesses(), oath.clauses(), oath.state(),
                oath.bloodOath(), oath.open(), oath.createdAt(), oath.sealedAt(), oath.activatedAt(),
                oath.resolvedAt(), oath.negotiationRef(), oath.fulfilledClauseIndices());
    }

    public Oath toDomain() {
        return Oath.reconstruct(id, parties, witnesses, clauses, state, bloodOath, open, createdAt, sealedAt,
                activatedAt, resolvedAt, negotiationRef, fulfilledClauseIndices);
    }
}
