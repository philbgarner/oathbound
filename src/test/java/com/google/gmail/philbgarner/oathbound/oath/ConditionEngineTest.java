package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConditionEngineTest {

    private final PlayerRef alice = new PlayerRef(UUID.randomUUID());
    private final PlayerRef bob = new PlayerRef(UUID.randomUUID());
    private final Currency coin = new Currency("coin");

    private Ledger ledger;
    private OathService oathService;
    private Map<UUID, ProtectionGroup> groups;
    private OwnershipResolver ownershipResolver;
    private EconomyService economyService;
    private DeathTracker deathTracker;
    private MobKillTracker mobKillTracker;
    private ManualConfirmStore manualConfirms;
    private ConditionEngine engine;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        oathService = new OathService(ledger);
        groups = new HashMap<>();
        ownershipResolver = new OwnershipResolver(id -> Optional.ofNullable(groups.get(id)), 10);
        economyService = new EconomyService(List.of(coin));
        deathTracker = new DeathTracker();
        mobKillTracker = new MobKillTracker();
        manualConfirms = new ManualConfirmStore();
        engine = new ConditionEngine(oathService, ownershipResolver, economyService,
                id -> Optional.ofNullable(groups.get(id)), deathTracker, mobKillTracker, manualConfirms);
    }

    private ProtectionGroup group(PlayerRef owner) {
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "Farm", owner, GroupTier.INDIVIDUAL);
        groups.put(group.id(), group);
        return group;
    }

    private Oath activeOathWithClauses(Clause... clauses) {
        Oath oath = oathService.createDraft(List.of(alice, bob), false);
        for (Clause clause : clauses) {
            oathService.addClause(oath, clause);
        }
        oathService.propose(oath, alice);
        oathService.seal(oath, bob);
        oathService.activate(oath, bob);
        return oath;
    }

    private SerializedItemStack fakeItem() {
        return new SerializedItemStack(new byte[] {1, 2, 3});
    }

    @Test
    void immediateTransferClauseExecutesAndFulfillsOnFirstTick() {
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(
                new Clause.TransferClause(bob, new ProtectionGroupRef(farm.id()), new Condition.Immediate()));

        ConditionEngine.TickResult result = engine.tick(List.of(oath), Instant.now());

        assertEquals(List.of(oath), result.changedOaths());
        assertTrue(result.newClaims().isEmpty());
        assertEquals(bob, farm.owner());
        assertTrue(oath.isClauseFulfilled(0));
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void timeElapsedTransferWaitsThenExecutes() {
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(new Clause.TransferClause(
                bob, new ProtectionGroupRef(farm.id()), new Condition.TimeElapsed(Duration.ofMinutes(10))));

        engine.tick(List.of(oath), oath.activatedAt().plus(Duration.ofMinutes(5)));
        assertEquals(alice, farm.owner());
        assertFalse(oath.isClauseFulfilled(0));
        assertEquals(OathState.ACTIVE, oath.state());

        engine.tick(List.of(oath), oath.activatedAt().plus(Duration.ofMinutes(10)));
        assertEquals(bob, farm.owner());
        assertTrue(oath.isClauseFulfilled(0));
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void deathCountTransferWaitsForTrackedDeaths() {
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(new Clause.TransferClause(
                bob, new ProtectionGroupRef(farm.id()), new Condition.DeathCount(bob, 2)));

        engine.tick(List.of(oath), Instant.now());
        assertEquals(alice, farm.owner());

        deathTracker.recordDeath(bob);
        deathTracker.recordDeath(bob);
        engine.tick(List.of(oath), Instant.now());

        assertEquals(bob, farm.owner());
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void deathsBeforeActivationDoNotCount() {
        ProtectionGroup farm = group(alice);
        deathTracker.recordDeath(bob);
        deathTracker.recordDeath(bob);

        Oath oath = activeOathWithClauses(new Clause.TransferClause(
                bob, new ProtectionGroupRef(farm.id()), new Condition.DeathCount(bob, 2)));

        engine.tick(List.of(oath), Instant.now());

        assertEquals(alice, farm.owner());
        assertFalse(oath.isClauseFulfilled(0));
    }

    @Test
    void manualConfirmTransferWaitsForConfirmation() {
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(new Clause.TransferClause(
                bob, new ProtectionGroupRef(farm.id()), new Condition.ManualConfirm(bob)));

        engine.tick(List.of(oath), Instant.now());
        assertEquals(alice, farm.owner());

        manualConfirms.confirm(oath.id(), bob);
        engine.tick(List.of(oath), Instant.now());

        assertEquals(bob, farm.owner());
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void killCountClauseStaysActiveUntilRequiredDeathsAreTracked() {
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(
                new Clause.TransferClause(bob, new ProtectionGroupRef(farm.id()), new Condition.Immediate()),
                new Clause.KillCountClause(bob, 3));

        deathTracker.recordDeath(bob);
        deathTracker.recordDeath(bob);
        engine.tick(List.of(oath), Instant.now());

        assertEquals(bob, farm.owner());
        assertTrue(oath.isClauseFulfilled(0));
        assertFalse(oath.isClauseFulfilled(1));
        assertEquals(OathState.ACTIVE, oath.state());
    }

    @Test
    void killCountClauseAutoFulfillsOathOnceRequiredDeathsAreTracked() {
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(
                new Clause.TransferClause(bob, new ProtectionGroupRef(farm.id()), new Condition.Immediate()),
                new Clause.KillCountClause(bob, 3));

        deathTracker.recordDeath(bob);
        deathTracker.recordDeath(bob);
        deathTracker.recordDeath(bob);
        engine.tick(List.of(oath), Instant.now());

        assertEquals(bob, farm.owner());
        assertTrue(oath.isClauseFulfilled(0));
        assertTrue(oath.isClauseFulfilled(1));
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void killCountClauseOnGroupTargetSumsDeathsAcrossMembers() {
        PlayerRef carol = new PlayerRef(UUID.randomUUID());
        ProtectionGroup targetGroup = new ProtectionGroup(UUID.randomUUID(), "Raiders", alice, GroupTier.INDIVIDUAL);
        targetGroup.addMember(new Member(bob, "member"));
        targetGroup.addMember(new Member(carol, "member"));
        groups.put(targetGroup.id(), targetGroup);

        Oath oath = activeOathWithClauses(
                new Clause.KillCountClause(new ProtectionGroupRef(targetGroup.id()), 2));

        deathTracker.recordDeath(bob);
        engine.tick(List.of(oath), Instant.now());
        assertFalse(oath.isClauseFulfilled(0));
        assertEquals(OathState.ACTIVE, oath.state());

        deathTracker.recordDeath(carol);
        engine.tick(List.of(oath), Instant.now());
        assertTrue(oath.isClauseFulfilled(0));
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void mobKillClauseStaysActiveUntilRequiredKillsAreTracked() {
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(
                new Clause.TransferClause(bob, new ProtectionGroupRef(farm.id()), new Condition.Immediate()),
                new Clause.MobKillClause(bob, "RAVAGER", 2));

        mobKillTracker.recordKill(bob, "RAVAGER");
        engine.tick(List.of(oath), Instant.now());

        assertEquals(bob, farm.owner());
        assertTrue(oath.isClauseFulfilled(0));
        assertFalse(oath.isClauseFulfilled(1));
        assertEquals(OathState.ACTIVE, oath.state());
    }

    @Test
    void mobKillClauseAutoFulfillsOathOnceRequiredKillsAreTracked() {
        Oath oath = activeOathWithClauses(new Clause.MobKillClause(bob, "RAVAGER", 2));

        mobKillTracker.recordKill(bob, "RAVAGER");
        mobKillTracker.recordKill(bob, "RAVAGER");
        engine.tick(List.of(oath), Instant.now());

        assertTrue(oath.isClauseFulfilled(0));
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void mobKillClauseDoesNotCountADifferentMobType() {
        Oath oath = activeOathWithClauses(new Clause.MobKillClause(bob, "RAVAGER", 1));

        mobKillTracker.recordKill(bob, "ZOMBIE");
        engine.tick(List.of(oath), Instant.now());

        assertFalse(oath.isClauseFulfilled(0));
        assertEquals(OathState.ACTIVE, oath.state());
    }

    @Test
    void pvpDeathCountTransferOnlyCountsDeathsWithAKiller() {
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(new Clause.TransferClause(
                bob, new ProtectionGroupRef(farm.id()), new Condition.PvpDeathCount(bob, 2)));

        deathTracker.recordDeath(bob);
        deathTracker.recordDeath(bob);
        engine.tick(List.of(oath), Instant.now());
        assertEquals(alice, farm.owner());
        assertFalse(oath.isClauseFulfilled(0));

        deathTracker.recordDeath(bob, alice);
        deathTracker.recordDeath(bob, alice);
        engine.tick(List.of(oath), Instant.now());

        assertEquals(bob, farm.owner());
        assertTrue(oath.isClauseFulfilled(0));
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void customFlagClauseNeverBlocksAutoFulfillment() {
        Oath oath = activeOathWithClauses(new Clause.CustomFlagClause("be nice"));

        engine.tick(List.of(oath), Instant.now());

        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void missingSubjectGroupLeavesClauseUnresolvedWithoutThrowing() {
        Oath oath = activeOathWithClauses(new Clause.TransferClause(
                bob, new ProtectionGroupRef(UUID.randomUUID()), new Condition.Immediate()));

        assertDoesNotThrow(() -> engine.tick(List.of(oath), Instant.now()));
        assertFalse(oath.isClauseFulfilled(0));
        assertEquals(OathState.ACTIVE, oath.state());
    }

    @Test
    void alreadyFulfilledTransferIsNotReExecuted() {
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(
                new Clause.TransferClause(bob, new ProtectionGroupRef(farm.id()), new Condition.Immediate()));

        engine.tick(List.of(oath), Instant.now());
        assertEquals(OathState.FULFILLED, oath.state());

        // A second tick over an already-terminal oath must be a no-op, not re-fire the transfer or re-fulfill.
        ConditionEngine.TickResult result = engine.tick(List.of(oath), Instant.now());
        assertTrue(result.changedOaths().isEmpty());
    }

    @Test
    void nonActiveOathsAreIgnored() {
        Oath draft = oathService.createDraft(List.of(alice, bob), false);
        ConditionEngine.TickResult result = engine.tick(List.of(draft), Instant.now());
        assertTrue(result.changedOaths().isEmpty());
    }

    @Test
    void immediateEscrowClauseReleasesCurrencyDirectlyToRecipient() {
        economyService.deposit(alice, coin, 100L);
        Oath oath = activeOathWithClauses(new Clause.EscrowClause(alice, bob, List.of(), Map.of(coin, 40L),
                List.of(new Clause.ReleaseStep(1.0, new Condition.Immediate()))));

        ConditionEngine.TickResult result = engine.tick(List.of(oath), Instant.now());

        assertEquals(40L, economyService.balance(bob, coin));
        assertTrue(result.newClaims().isEmpty());
        assertTrue(oath.isClauseFulfilled(0));
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void immediateEscrowClauseWithItemsProducesAClaimableBatch() {
        List<SerializedItemStack> items = List.of(fakeItem());
        Oath oath = activeOathWithClauses(new Clause.EscrowClause(alice, bob, items, Map.of(),
                List.of(new Clause.ReleaseStep(1.0, new Condition.Immediate()))));

        ConditionEngine.TickResult result = engine.tick(List.of(oath), Instant.now());

        assertEquals(1, result.newClaims().size());
        EscrowClaim claim = result.newClaims().get(0);
        assertEquals(oath.id(), claim.oathId());
        assertEquals(0, claim.clauseIndex());
        assertEquals(alice, claim.depositor());
        assertEquals(bob, claim.recipient());
        assertEquals(bob, claim.holder());
        assertFalse(claim.claimed());
        assertEquals(items, claim.items());
        assertTrue(oath.isClauseFulfilled(0));
    }

    @Test
    void multiStepReleaseScheduleFiresAtomicallyOnlyOnceEveryStepConditionHolds() {
        economyService.deposit(alice, coin, 100L);
        Oath oath = activeOathWithClauses(new Clause.EscrowClause(alice, bob, List.of(fakeItem()), Map.of(coin, 100L),
                List.of(new Clause.ReleaseStep(0.3, new Condition.Immediate()),
                        new Clause.ReleaseStep(1.0, new Condition.TimeElapsed(Duration.ofMinutes(10))))));

        ConditionEngine.TickResult first = engine.tick(List.of(oath), oath.activatedAt().plus(Duration.ofMinutes(5)));
        assertEquals(0L, economyService.balance(bob, coin));
        assertTrue(first.newClaims().isEmpty());
        assertFalse(oath.isClauseFulfilled(0));
        assertEquals(OathState.ACTIVE, oath.state());

        ConditionEngine.TickResult second = engine.tick(List.of(oath), oath.activatedAt().plus(Duration.ofMinutes(10)));
        assertEquals(100L, economyService.balance(bob, coin));
        assertEquals(1, second.newClaims().size());
        assertTrue(oath.isClauseFulfilled(0));
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void escrowedCurrencyIsReportedAsPaymentReceivedForOtherClausesInTheSameOath() {
        economyService.deposit(alice, coin, 100L);
        ProtectionGroup farm = group(alice);
        Oath oath = activeOathWithClauses(
                new Clause.EscrowClause(alice, bob, List.of(), Map.of(coin, 50L),
                        List.of(new Clause.ReleaseStep(1.0, new Condition.Immediate()))),
                new Clause.TransferClause(bob, new ProtectionGroupRef(farm.id()),
                        new Condition.PaymentReceived(50L, coin)));

        engine.tick(List.of(oath), Instant.now());

        assertEquals(bob, farm.owner());
        assertTrue(oath.isClauseFulfilled(1));
        assertEquals(OathState.FULFILLED, oath.state());
    }

    @Test
    void unknownCurrencyLeavesEscrowClauseUnresolvedWithoutThrowing() {
        Currency gem = new Currency("gem");
        Oath oath = activeOathWithClauses(new Clause.EscrowClause(alice, bob, List.of(), Map.of(gem, 10L),
                List.of(new Clause.ReleaseStep(1.0, new Condition.Immediate()))));

        assertDoesNotThrow(() -> engine.tick(List.of(oath), Instant.now()));
        assertFalse(oath.isClauseFulfilled(0));
        assertEquals(OathState.ACTIVE, oath.state());
    }
}
