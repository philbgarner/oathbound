package com.google.gmail.philbgarner.oathbound.honor;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.util.Objects;

public record PlayerHonor(PlayerRef player, long value) {
    public PlayerHonor {
        Objects.requireNonNull(player, "player");
    }
}
