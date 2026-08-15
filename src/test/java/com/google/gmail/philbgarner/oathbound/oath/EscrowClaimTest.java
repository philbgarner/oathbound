package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EscrowClaimTest {

    private final PlayerRef depositor = new PlayerRef(UUID.randomUUID());
    private final PlayerRef recipient = new PlayerRef(UUID.randomUUID());

    private EscrowClaim claim() {
        return new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(), 0, depositor, recipient,
                List.of(new SerializedItemStack(new byte[] {1})), Instant.now());
    }

    @Test
    void holderIsRecipientUntilExpired() {
        EscrowClaim claim = claim();
        assertEquals(recipient, claim.holder());

        claim.expire();
        assertEquals(depositor, claim.holder());
        assertTrue(claim.expired());
    }

    @Test
    void claimingTwiceIsRejected() {
        EscrowClaim claim = claim();
        claim.claim();
        assertTrue(claim.claimed());
        assertThrows(IllegalStateException.class, claim::claim);
    }

    @Test
    void cannotExpireAnAlreadyClaimedBatch() {
        EscrowClaim claim = claim();
        claim.claim();
        assertThrows(IllegalStateException.class, claim::expire);
    }

    @Test
    void reconstructRoundTripsFlags() {
        EscrowClaim original = claim();
        original.expire();

        EscrowClaim rehydrated = EscrowClaim.reconstruct(original.id(), original.oathId(), original.clauseIndex(),
                original.depositor(), original.recipient(), original.items(), original.releasedAt(),
                original.claimed(), original.expired());

        assertFalse(rehydrated.claimed());
        assertTrue(rehydrated.expired());
        assertEquals(depositor, rehydrated.holder());
    }
}
