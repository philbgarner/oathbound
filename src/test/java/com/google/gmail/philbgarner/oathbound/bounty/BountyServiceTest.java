package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.economy.InsufficientFundsException;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.oath.Ledger;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BountyServiceTest {

    private final PlayerRef placer = new PlayerRef(UUID.randomUUID());
    private final PlayerRef bob = new PlayerRef(UUID.randomUUID());
    private final Currency coin = new Currency("coin");

    private EconomyService economyService;
    private Map<UUID, ProtectionGroup> groups;
    private List<Bounty> bounties;
    private List<Oath> oaths;
    private BountyService service;

    @BeforeEach
    void setUp() {
        economyService = new EconomyService(List.of(coin));
        groups = new HashMap<>();
        bounties = new ArrayList<>();
        oaths = new ArrayList<>();
        service = new BountyService(economyService, id -> Optional.ofNullable(groups.get(id)),
                () -> bounties, () -> oaths,
                100L, 0.5, Duration.ofHours(72), 3, Duration.ofDays(14), 0.5);
    }

    private ProtectionGroup groupOf(PlayerRef... members) {
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "Raiders", placer, GroupTier.INDIVIDUAL);
        for (PlayerRef member : members) {
            group.addMember(new Member(member, "member"));
        }
        groups.put(group.id(), group);
        return group;
    }

    @Test
    void soloTargetForcesQuantityToOneAndWithdrawsFeePlusReward() {
        economyService.deposit(placer, coin, 1000L);
        Bounty bounty = service.place(placer, new BountyTarget.Solo(bob), 5, Map.of(coin, 200L), coin, Instant.now());

        assertEquals(1, bounty.originalQuantity());
        assertEquals(1, bounty.remainingQuantity());
        assertEquals(BountyStatus.ACTIVE, bounty.status());
        // fee = feeBase (100) since no existing heat and no discount
        assertEquals(100L, bounty.feePaid());
        assertEquals(1000L - 200L - 100L, economyService.balance(placer, coin));
    }

    @Test
    void groupTargetQuantityMustNotExceedCurrentMemberCount() {
        PlayerRef carol = new PlayerRef(UUID.randomUUID());
        ProtectionGroup group = groupOf(bob, carol);
        economyService.deposit(placer, coin, 1000L);

        assertThrows(IllegalArgumentException.class, () -> service.place(
                placer, new BountyTarget.Group(new ProtectionGroupRef(group.id())), 3,
                Map.of(coin, 200L), coin, Instant.now()));
    }

    @Test
    void groupTargetWithinMemberCountSucceeds() {
        PlayerRef carol = new PlayerRef(UUID.randomUUID());
        ProtectionGroup group = groupOf(bob, carol);
        economyService.deposit(placer, coin, 1000L);

        Bounty bounty = service.place(placer, new BountyTarget.Group(new ProtectionGroupRef(group.id())), 2,
                Map.of(coin, 200L), coin, Instant.now());

        assertEquals(2, bounty.originalQuantity());
        assertEquals(2, bounty.remainingQuantity());
    }

    @Test
    void insufficientFundsLeavesNoPartialWithdrawal() {
        economyService.deposit(placer, coin, 50L);
        assertThrows(InsufficientFundsException.class, () -> service.place(
                placer, new BountyTarget.Solo(bob), 1, Map.of(coin, 200L), coin, Instant.now()));
        assertEquals(50L, economyService.balance(placer, coin));
        assertTrue(bounties.isEmpty());
    }

    @Test
    void placementLimitIsEnforcedIndependentlyOfFunds() {
        economyService.deposit(placer, coin, 10_000L);
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            PlayerRef target = new PlayerRef(UUID.randomUUID());
            Bounty bounty = service.place(placer, new BountyTarget.Solo(target), 1, Map.of(coin, 50L), coin, now);
            bounties.add(bounty);
        }
        assertThrows(BountyPlacementLimitException.class, () -> service.place(
                placer, new BountyTarget.Solo(new PlayerRef(UUID.randomUUID())), 1, Map.of(coin, 50L), coin, now));
    }

    @Test
    void turnInHeadPaysPerHeadInstallmentAndFulfillsOnLastHead() {
        PlayerRef carol = new PlayerRef(UUID.randomUUID());
        PlayerRef dave = new PlayerRef(UUID.randomUUID());
        ProtectionGroup group = groupOf(bob, carol, dave);
        economyService.deposit(placer, coin, 1000L);
        Bounty bounty = service.place(placer, new BountyTarget.Group(new ProtectionGroupRef(group.id())), 3,
                Map.of(coin, 100L), coin, Instant.now());

        PlayerRef finder1 = new PlayerRef(UUID.randomUUID());
        Map<Currency, Long> firstPay = service.turnInHead(bounty, finder1, Instant.now());
        assertEquals(33L, firstPay.get(coin));
        assertEquals(2, bounty.remainingQuantity());
        assertEquals(BountyStatus.ACTIVE, bounty.status());
        assertEquals(33L, economyService.balance(finder1, coin));

        PlayerRef finder2 = new PlayerRef(UUID.randomUUID());
        service.turnInHead(bounty, finder2, Instant.now());
        assertEquals(1, bounty.remainingQuantity());

        PlayerRef finder3 = new PlayerRef(UUID.randomUUID());
        Map<Currency, Long> lastPay = service.turnInHead(bounty, finder3, Instant.now());
        // 100 - 33*2 = 34, so the sum across all three payouts is exactly 100.
        assertEquals(34L, lastPay.get(coin));
        assertEquals(0, bounty.remainingQuantity());
        assertEquals(BountyStatus.FULFILLED, bounty.status());
        assertEquals(100L, economyService.balance(finder1, coin) + economyService.balance(finder2, coin)
                + economyService.balance(finder3, coin));
    }

    @Test
    void cancelRefundsUnpaidRemainderNotTheFee() {
        economyService.deposit(placer, coin, 1000L);
        Bounty bounty = service.place(placer, new BountyTarget.Solo(bob), 1, Map.of(coin, 200L), coin, Instant.now());
        long afterPlacement = economyService.balance(placer, coin);

        service.cancel(bounty);

        assertEquals(BountyStatus.CANCELLED, bounty.status());
        assertEquals(afterPlacement + 200L, economyService.balance(placer, coin));
    }

    @Test
    void cannotTurnInOrCancelANonActiveBounty() {
        economyService.deposit(placer, coin, 1000L);
        Bounty bounty = service.place(placer, new BountyTarget.Solo(bob), 1, Map.of(coin, 200L), coin, Instant.now());
        service.cancel(bounty);

        assertThrows(IllegalStateException.class, () -> service.turnInHead(bounty, bob, Instant.now()));
        assertThrows(IllegalStateException.class, () -> service.cancel(bounty));
    }

    @Test
    void abandonmentDecrementsRemainingQuantityOncePerMemberAndFulfillsAtZero() {
        ProtectionGroup group = groupOf(bob);
        economyService.deposit(placer, coin, 1000L);
        Bounty bounty = service.place(placer, new BountyTarget.Group(new ProtectionGroupRef(group.id())), 1,
                Map.of(coin, 200L), coin, Instant.now());

        service.decrementForAbandonment(bounty, bob);
        assertEquals(0, bounty.remainingQuantity());
        assertEquals(BountyStatus.FULFILLED, bounty.status());

        // Idempotent / no-op once no longer active.
        service.decrementForAbandonment(bounty, bob);
        assertEquals(0, bounty.remainingQuantity());
    }

    @Test
    void heatFromAnExistingActiveBountyRaisesTheFeeForAFollowUpPlacement() {
        economyService.deposit(placer, coin, 10_000L);
        Instant now = Instant.now();
        Bounty first = service.place(placer, new BountyTarget.Solo(bob), 1, Map.of(coin, 50L), coin, now);
        bounties.add(first);

        PlayerRef secondPlacer = new PlayerRef(UUID.randomUUID());
        economyService.deposit(secondPlacer, coin, 10_000L);
        Bounty second = service.place(secondPlacer, new BountyTarget.Solo(bob), 1, Map.of(coin, 50L), coin, now);

        assertTrue(second.feePaid() > 100L, "fee should scale up from existing heat on the same target");
    }

    @Test
    void breachDiscountReducesFeeWhenTargetRecentlyBrokeAnOathWithThePlacer() {
        Ledger ledger = new Ledger();
        OathService oathService = new OathService(ledger);
        Oath oath = oathService.createDraft(List.of(placer, bob), false);
        oathService.propose(oath, placer);
        oathService.seal(oath, bob);
        oathService.activate(oath, bob);
        oathService.breach(oath, bob);
        oaths.add(oath);

        economyService.deposit(placer, coin, 10_000L);
        Bounty bounty = service.place(placer, new BountyTarget.Solo(bob), 1, Map.of(coin, 50L), coin, Instant.now());

        assertEquals(50L, bounty.feePaid());
    }
}
