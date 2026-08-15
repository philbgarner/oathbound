package com.google.gmail.philbgarner.oathbound.economy;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

public final class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(PlayerRef player, Currency currency, long requested, long available) {
        super("Insufficient funds for " + player.playerId() + " in " + currency.id()
                + ": requested " + requested + ", available " + available);
    }
}
