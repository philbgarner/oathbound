package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();
    private final Instant activatedAt = Instant.parse("2026-01-01T00:00:00Z");

    private static final class FakeContext implements ConditionContext {
        final Map<EntityRef, Integer> deaths = new HashMap<>();
        final Map<EntityRef, Integer> pvpDeaths = new HashMap<>();
        final Map<String, Integer> mobKills = new HashMap<>();
        final Map<Currency, Long> paid = new HashMap<>();
        boolean ballotDecided;
        boolean manuallyConfirmed;

        @Override
        public int deathCount(EntityRef target) {
            return deaths.getOrDefault(target, 0);
        }

        @Override
        public int pvpDeathCount(EntityRef target) {
            return pvpDeaths.getOrDefault(target, 0);
        }

        @Override
        public int mobKillCount(PlayerRef killer, String mobTypeName) {
            return mobKills.getOrDefault(killer.playerId() + ":" + mobTypeName, 0);
        }

        @Override
        public long amountPaid(Currency currency) {
            return paid.getOrDefault(currency, 0L);
        }

        @Override
        public boolean isBallotDecided(UUID ballotRef) {
            return ballotDecided;
        }

        @Override
        public boolean isManuallyConfirmed(PlayerRef party) {
            return manuallyConfirmed;
        }
    }

    @Test
    void immediateIsAlwaysTrue() {
        FakeContext context = new FakeContext();
        assertTrue(evaluator.evaluate(new Condition.Immediate(), activatedAt, activatedAt, context));
    }

    @Test
    void timeElapsedIsFalseBeforeAndTrueAtOrAfterDuration() {
        FakeContext context = new FakeContext();
        Condition condition = new Condition.TimeElapsed(Duration.ofMinutes(5));

        assertFalse(evaluator.evaluate(condition, activatedAt, activatedAt.plus(Duration.ofMinutes(4)), context));
        assertTrue(evaluator.evaluate(condition, activatedAt, activatedAt.plus(Duration.ofMinutes(5)), context));
        assertTrue(evaluator.evaluate(condition, activatedAt, activatedAt.plus(Duration.ofMinutes(6)), context));
    }

    @Test
    void deathCountComparesAgainstContext() {
        FakeContext context = new FakeContext();
        PlayerRef target = new PlayerRef(UUID.randomUUID());
        context.deaths.put(target, 2);
        Condition condition = new Condition.DeathCount(target, 3);

        assertFalse(evaluator.evaluate(condition, activatedAt, activatedAt, context));
        context.deaths.put(target, 3);
        assertTrue(evaluator.evaluate(condition, activatedAt, activatedAt, context));
    }

    @Test
    void pvpDeathCountComparesAgainstContext() {
        FakeContext context = new FakeContext();
        PlayerRef target = new PlayerRef(UUID.randomUUID());
        context.pvpDeaths.put(target, 4);
        Condition condition = new Condition.PvpDeathCount(target, 5);

        assertFalse(evaluator.evaluate(condition, activatedAt, activatedAt, context));
        context.pvpDeaths.put(target, 5);
        assertTrue(evaluator.evaluate(condition, activatedAt, activatedAt, context));
    }

    @Test
    void mobKillCountComparesAgainstContext() {
        FakeContext context = new FakeContext();
        PlayerRef killer = new PlayerRef(UUID.randomUUID());
        context.mobKills.put(killer.playerId() + ":RAVAGER", 0);
        Condition condition = new Condition.MobKillCount(killer, "RAVAGER", 1);

        assertFalse(evaluator.evaluate(condition, activatedAt, activatedAt, context));
        context.mobKills.put(killer.playerId() + ":RAVAGER", 1);
        assertTrue(evaluator.evaluate(condition, activatedAt, activatedAt, context));
    }

    @Test
    void paymentReceivedComparesAgainstContext() {
        FakeContext context = new FakeContext();
        Currency coin = new Currency("coin");
        context.paid.put(coin, 5L);
        Condition condition = new Condition.PaymentReceived(10L, coin);

        assertFalse(evaluator.evaluate(condition, activatedAt, activatedAt, context));
        context.paid.put(coin, 10L);
        assertTrue(evaluator.evaluate(condition, activatedAt, activatedAt, context));
    }

    @Test
    void voteTallyAndManualConfirmDelegateToContext() {
        FakeContext context = new FakeContext();
        Condition ballot = new Condition.VoteTally(UUID.randomUUID());
        Condition confirm = new Condition.ManualConfirm(new PlayerRef(UUID.randomUUID()));

        assertFalse(evaluator.evaluate(ballot, activatedAt, activatedAt, context));
        assertFalse(evaluator.evaluate(confirm, activatedAt, activatedAt, context));

        context.ballotDecided = true;
        context.manuallyConfirmed = true;
        assertTrue(evaluator.evaluate(ballot, activatedAt, activatedAt, context));
        assertTrue(evaluator.evaluate(confirm, activatedAt, activatedAt, context));
    }

    @Test
    void compoundAndRequiresAllSubconditions() {
        FakeContext context = new FakeContext();
        context.ballotDecided = true;
        context.manuallyConfirmed = false;
        Condition and = new Condition.Compound(Condition.CompoundOperator.AND, List.of(
                new Condition.VoteTally(UUID.randomUUID()),
                new Condition.ManualConfirm(new PlayerRef(UUID.randomUUID()))
        ));
        assertFalse(evaluator.evaluate(and, activatedAt, activatedAt, context));

        context.manuallyConfirmed = true;
        assertTrue(evaluator.evaluate(and, activatedAt, activatedAt, context));
    }

    @Test
    void compoundOrRequiresAnySubcondition() {
        FakeContext context = new FakeContext();
        context.ballotDecided = false;
        context.manuallyConfirmed = true;
        Condition or = new Condition.Compound(Condition.CompoundOperator.OR, List.of(
                new Condition.VoteTally(UUID.randomUUID()),
                new Condition.ManualConfirm(new PlayerRef(UUID.randomUUID()))
        ));
        assertTrue(evaluator.evaluate(or, activatedAt, activatedAt, context));

        context.manuallyConfirmed = false;
        assertFalse(evaluator.evaluate(or, activatedAt, activatedAt, context));
    }

    @Test
    void compoundNotIsTrueOnlyWhenNoneOfTheSubconditionsHold() {
        FakeContext context = new FakeContext();
        context.ballotDecided = false;
        context.manuallyConfirmed = false;
        Condition not = new Condition.Compound(Condition.CompoundOperator.NOT, List.of(
                new Condition.VoteTally(UUID.randomUUID()),
                new Condition.ManualConfirm(new PlayerRef(UUID.randomUUID()))
        ));
        assertTrue(evaluator.evaluate(not, activatedAt, activatedAt, context));

        context.ballotDecided = true;
        assertFalse(evaluator.evaluate(not, activatedAt, activatedAt, context));
    }
}
