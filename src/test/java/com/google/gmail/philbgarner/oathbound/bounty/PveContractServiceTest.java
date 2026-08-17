package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PveContractServiceTest {

    private final PlayerRef player = new PlayerRef(UUID.randomUUID());
    private final Currency coin = new Currency("coin");
    private final PveContractDefinition definition =
            new PveContractDefinition("spider-cull", "Spider Cull", "SPIDER", 3, Map.of(coin, 90L));

    private EconomyService economyService;
    private PveContractService service;

    @BeforeEach
    void setUp() {
        economyService = new EconomyService(List.of(coin));
        service = new PveContractService(economyService);
    }

    @Test
    void acceptStartsFreshProgressWhenNoneExists() {
        PveContractProgress progress = service.accept(player, definition, Instant.now(), List.of());

        assertEquals(player, progress.player());
        assertEquals("spider-cull", progress.contractId());
        assertEquals(0, progress.killsSoFar());
    }

    @Test
    void acceptReturnsExistingProgressRatherThanStartingOver() {
        PveContractProgress existing = new PveContractProgress(UUID.randomUUID(), player, "spider-cull", 2, Instant.now(), 0);

        PveContractProgress accepted = service.accept(player, definition, Instant.now(), List.of(existing));

        assertEquals(existing.id(), accepted.id());
        assertEquals(2, accepted.killsSoFar());
    }

    @Test
    void recordKillDoesNotPayOutOrResetBeforeQuantityIsReached() {
        PveContractProgress progress = service.accept(player, definition, Instant.now(), List.of());

        boolean completed = service.recordKill(progress, definition);
        assertFalse(completed);
        assertEquals(1, progress.killsSoFar());
        assertEquals(0L, economyService.balance(player, coin));
    }

    @Test
    void recordKillPaysOutAndResetsOnceQuantityIsReached() {
        PveContractProgress progress = service.accept(player, definition, Instant.now(), List.of());

        service.recordKill(progress, definition);
        service.recordKill(progress, definition);
        boolean completed = service.recordKill(progress, definition);

        assertTrue(completed);
        assertEquals(90L, economyService.balance(player, coin));
        assertEquals(0, progress.killsSoFar(), "progress resets so the contract is repeatable");
        assertEquals(1, progress.timesCompleted());
    }
}
