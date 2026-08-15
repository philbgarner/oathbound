package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public sealed interface Condition {

    enum CompoundOperator { AND, OR, NOT }

    record Immediate() implements Condition {
    }

    record TimeElapsed(Duration duration) implements Condition {
    }

    record DeathCount(EntityRef target, int count) implements Condition {
    }

    record PaymentReceived(long amount, Currency currency) implements Condition {
    }

    record VoteTally(UUID ballotRef) implements Condition {
    }

    record ManualConfirm(PlayerRef requiredParty) implements Condition {
    }

    record Compound(CompoundOperator operator, List<Condition> subconditions) implements Condition {
        public Compound {
            subconditions = List.copyOf(subconditions);
        }
    }
}
