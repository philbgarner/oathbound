package com.google.gmail.philbgarner.oathbound.persistence.dto;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.EscrowClaim;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Plain, Gson-friendly mirror of {@link EscrowClaim}'s persisted state - kept separate from the domain
 * type so persistence never relies on reflective access into its private fields. */
public record EscrowClaimDto(UUID id, UUID oathId, int clauseIndex, PlayerRef depositor, PlayerRef recipient,
                              List<SerializedItemStack> items, Instant releasedAt, boolean claimed, boolean expired) {

    public static EscrowClaimDto from(EscrowClaim claim) {
        return new EscrowClaimDto(claim.id(), claim.oathId(), claim.clauseIndex(), claim.depositor(),
                claim.recipient(), claim.items(), claim.releasedAt(), claim.claimed(), claim.expired());
    }

    public EscrowClaim toDomain() {
        return EscrowClaim.reconstruct(id, oathId, clauseIndex, depositor, recipient, items, releasedAt,
                claimed, expired);
    }
}
