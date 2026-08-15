package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NegotiationExpiryServiceTest {

    private final OathService oathService = new OathService(new Ledger());
    private final NegotiationExpiryService service = new NegotiationExpiryService();

    private Oath proposedOathCreatedAt(Instant createdAt, boolean open) {
        PlayerRef creator = new PlayerRef(UUID.randomUUID());
        PlayerRef counterparty = new PlayerRef(UUID.randomUUID());
        List<PlayerRef> parties = open ? List.of(creator) : List.of(creator, counterparty);
        return Oath.reconstruct(UUID.randomUUID(), parties, List.of(), List.of(),
                OathState.PROPOSED, false, open, createdAt, null, null, null, null, Set.of());
    }

    @Test
    void voidsNamedPartyOathsProposedPastTheExpiryWindow() {
        Instant now = Instant.now();
        Oath stale = proposedOathCreatedAt(now.minus(Duration.ofDays(8)), false);

        List<Oath> expired = service.sweep(List.of(stale), now, Duration.ofDays(7), oathService);

        assertEquals(List.of(stale), expired);
        assertEquals(OathState.VOIDED, stale.state());
    }

    @Test
    void leavesRecentProposalsAlone() {
        Instant now = Instant.now();
        Oath recent = proposedOathCreatedAt(now.minus(Duration.ofHours(1)), false);

        List<Oath> expired = service.sweep(List.of(recent), now, Duration.ofDays(7), oathService);

        assertTrue(expired.isEmpty());
        assertEquals(OathState.PROPOSED, recent.state());
    }

    @Test
    void leavesOpenTradeContractsAloneEvenWhenStale() {
        Instant now = Instant.now();
        Oath openStale = proposedOathCreatedAt(now.minus(Duration.ofDays(30)), true);

        List<Oath> expired = service.sweep(List.of(openStale), now, Duration.ofDays(7), oathService);

        assertTrue(expired.isEmpty());
        assertEquals(OathState.PROPOSED, openStale.state());
    }
}
