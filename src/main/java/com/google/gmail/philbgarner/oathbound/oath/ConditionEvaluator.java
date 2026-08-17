package com.google.gmail.philbgarner.oathbound.oath;

import java.time.Instant;
import java.util.List;

public final class ConditionEvaluator {

    public boolean evaluate(Condition condition, Instant activatedAt, Instant now, ConditionContext context) {
        return switch (condition) {
            case Condition.Immediate ignored -> true;
            case Condition.TimeElapsed(var duration) -> !now.isBefore(activatedAt.plus(duration));
            case Condition.DeathCount(var target, var count) -> context.deathCount(target) >= count;
            case Condition.PvpDeathCount(var target, var count) -> context.pvpDeathCount(target) >= count;
            case Condition.MobKillCount(var killer, var mobTypeName, var count) ->
                    context.mobKillCount(killer, mobTypeName) >= count;
            case Condition.PaymentReceived(var amount, var currency) -> context.amountPaid(currency) >= amount;
            case Condition.VoteTally(var ballotRef) -> context.isBallotDecided(ballotRef);
            case Condition.ManualConfirm(var party) -> context.isManuallyConfirmed(party);
            case Condition.Compound(var operator, var subconditions) ->
                    evaluateCompound(operator, subconditions, activatedAt, now, context);
        };
    }

    private boolean evaluateCompound(Condition.CompoundOperator operator, List<Condition> subconditions,
                                      Instant activatedAt, Instant now, ConditionContext context) {
        return switch (operator) {
            case AND -> subconditions.stream().allMatch(c -> evaluate(c, activatedAt, now, context));
            case OR -> subconditions.stream().anyMatch(c -> evaluate(c, activatedAt, now, context));
            case NOT -> subconditions.stream().noneMatch(c -> evaluate(c, activatedAt, now, context));
        };
    }
}
