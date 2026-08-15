package com.google.gmail.philbgarner.oathbound.group;

import java.util.Objects;

public record Member(PlayerRef player, String roleName) {
    public Member {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(roleName, "roleName");
        if (roleName.isBlank()) {
            throw new IllegalArgumentException("roleName must not be blank");
        }
    }
}
