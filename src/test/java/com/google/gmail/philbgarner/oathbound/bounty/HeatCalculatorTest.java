package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.oath.Ledger;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HeatCalculatorTest {

    private final PlayerRef alice = new PlayerRef(UUID.randomUUID());
    private final PlayerRef bob = new PlayerRef(UUID.randomUUID());
    private final Map<UUID, ProtectionGroup> groups = new HashMap<>();
    private final java.util.function.Function<UUID, Optional<ProtectionGroup>> groupLookup =
            id -> Optional.ofNullable(groups.get(id));

    private Bounty soloBounty(PlayerRef target, Instant placedAt) {
        return new Bounty(UUID.randomUUID(), alice, new BountyTarget.Solo(target), 1, 1,
                Map.of(), 0L, placedAt, BountyStatus.ACTIVE);
    }

    @Test
    void heatAccumulatesAcrossMultipleOverlappingActiveBounties() {
        Instant now = Instant.now();
        List<Bounty> bounties = List.of(
                soloBounty(bob, now.minus(Duration.ofHours(1))),
                soloBounty(bob, now.minus(Duration.ofHours(2))));

        double heat = HeatCalculator.heatOf(new BountyTarget.Solo(bob), bounties, groupLookup, now, Duration.ofHours(72));

        assertTrue(heat > 0);
    }

    @Test
    void heatDecaysToZeroPastTheWindow() {
        Instant now = Instant.now();
        List<Bounty> bounties = List.of(soloBounty(bob, now.minus(Duration.ofHours(100))));

        double heat = HeatCalculator.heatOf(new BountyTarget.Solo(bob), bounties, groupLookup, now, Duration.ofHours(72));

        assertEquals(0.0, heat);
    }

    @Test
    void soloTargetPicksUpHeatFromAnOverlappingGroupBountyContainingThem() {
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "Raiders", alice, GroupTier.INDIVIDUAL);
        group.addMember(new Member(bob, "member"));
        groups.put(group.id(), group);

        Instant now = Instant.now();
        Bounty groupBounty = new Bounty(UUID.randomUUID(), alice, new BountyTarget.Group(new ProtectionGroupRef(group.id())),
                1, 1, Map.of(), 0L, now.minus(Duration.ofHours(1)), BountyStatus.ACTIVE);

        double heat = HeatCalculator.heatOf(new BountyTarget.Solo(bob), List.of(groupBounty), groupLookup, now,
                Duration.ofHours(72));

        assertTrue(heat > 0);
    }

    @Test
    void feeForScalesWithHeat() {
        long feeAtZeroHeat = HeatCalculator.feeFor(0.0, 100L, 0.5);
        long feeAtOneHeat = HeatCalculator.feeFor(1.0, 100L, 0.5);

        assertEquals(100L, feeAtZeroHeat);
        assertEquals(150L, feeAtOneHeat);
    }

    @Test
    void placementsInLast24hExcludesOlderPlacements() {
        Instant now = Instant.now();
        List<Bounty> bounties = List.of(
                soloBounty(bob, now.minus(Duration.ofHours(1))),
                soloBounty(bob, now.minus(Duration.ofHours(25))));
        // both placed by alice
        int count = HeatCalculator.placementsInLast24h(alice, bounties, now);
        assertEquals(1, count);
    }

    @Test
    void recentBreachBetweenIsTrueOnlyForABreachInsideTheWindowBetweenThePair() {
        Ledger ledger = new Ledger();
        OathService oathService = new OathService(ledger);
        Oath oath = oathService.createDraft(List.of(alice, bob), false);
        oathService.propose(oath, alice);
        oathService.seal(oath, bob);
        oathService.activate(oath, bob);
        oathService.breach(oath, bob);

        boolean discounted = HeatCalculator.recentBreachBetween(
                alice, new BountyTarget.Solo(bob), List.of(oath), groupLookup, Instant.now(), Duration.ofDays(14));
        assertTrue(discounted);
    }

    @Test
    void recentBreachBetweenIsFalseJustOutsideTheWindow() {
        Ledger ledger = new Ledger();
        OathService oathService = new OathService(ledger);
        Oath oath = oathService.createDraft(List.of(alice, bob), false);
        oathService.propose(oath, alice);
        oathService.seal(oath, bob);
        oathService.activate(oath, bob);
        oathService.breach(oath, bob);

        boolean discounted = HeatCalculator.recentBreachBetween(alice, new BountyTarget.Solo(bob), List.of(oath),
                groupLookup, oath.resolvedAt().plus(Duration.ofDays(15)), Duration.ofDays(14));
        assertFalse(discounted);
    }

    @Test
    void recentBreachBetweenIsFalseWhenTheBreachIsBetweenUnrelatedPlayers() {
        PlayerRef carol = new PlayerRef(UUID.randomUUID());
        PlayerRef dave = new PlayerRef(UUID.randomUUID());
        Ledger ledger = new Ledger();
        OathService oathService = new OathService(ledger);
        Oath oath = oathService.createDraft(List.of(carol, dave), false);
        oathService.propose(oath, carol);
        oathService.seal(oath, dave);
        oathService.activate(oath, dave);
        oathService.breach(oath, dave);

        boolean discounted = HeatCalculator.recentBreachBetween(
                alice, new BountyTarget.Solo(bob), List.of(oath), groupLookup, Instant.now(), Duration.ofDays(14));
        assertFalse(discounted);
    }
}
