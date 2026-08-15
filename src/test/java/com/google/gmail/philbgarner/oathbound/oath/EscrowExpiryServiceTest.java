package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EscrowExpiryServiceTest {

    private final PlayerRef depositor = new PlayerRef(UUID.randomUUID());
    private final PlayerRef recipient = new PlayerRef(UUID.randomUUID());
    private final EscrowExpiryService service = new EscrowExpiryService();

    private EscrowClaim claimReleasedAt(Instant releasedAt) {
        return new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(), 0, depositor, recipient,
                List.of(new SerializedItemStack(new byte[] {1})), releasedAt);
    }

    @Test
    void expiresClaimsPastTheWindowBackToTheDepositor() {
        Instant now = Instant.now();
        EscrowClaim old = claimReleasedAt(now.minus(Duration.ofDays(31)));

        List<EscrowClaim> changed = service.sweep(List.of(old), now, Duration.ofDays(30));

        assertEquals(List.of(old), changed);
        assertTrue(old.expired());
        assertEquals(depositor, old.holder());
    }

    @Test
    void leavesRecentClaimsAlone() {
        Instant now = Instant.now();
        EscrowClaim recent = claimReleasedAt(now.minus(Duration.ofDays(1)));

        List<EscrowClaim> changed = service.sweep(List.of(recent), now, Duration.ofDays(30));

        assertTrue(changed.isEmpty());
        assertFalse(recent.expired());
    }

    @Test
    void skipsAlreadyClaimedOrAlreadyExpiredBatches() {
        Instant now = Instant.now();
        EscrowClaim claimed = claimReleasedAt(now.minus(Duration.ofDays(60)));
        claimed.claim();
        EscrowClaim alreadyExpired = claimReleasedAt(now.minus(Duration.ofDays(60)));
        alreadyExpired.expire();

        List<EscrowClaim> changed = service.sweep(List.of(claimed, alreadyExpired), now, Duration.ofDays(30));

        assertTrue(changed.isEmpty());
    }
}
