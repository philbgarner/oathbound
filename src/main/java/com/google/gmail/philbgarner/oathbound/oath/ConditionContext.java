package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.util.UUID;

/** Seam for the real death-tracking/payment/vote/manual-confirm backends wired up in later phases. */
public interface ConditionContext {
    int deathCount(EntityRef target);

    long amountPaid(Currency currency);

    boolean isBallotDecided(UUID ballotRef);

    boolean isManuallyConfirmed(PlayerRef party);
}
