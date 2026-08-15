package com.google.gmail.philbgarner.oathbound.economy;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.util.Objects;

public record PlayerBalance(PlayerRef player, Currency currency, long amount) {
    public PlayerBalance {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currency, "currency");
    }
}
