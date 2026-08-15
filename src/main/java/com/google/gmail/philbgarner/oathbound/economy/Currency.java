package com.google.gmail.philbgarner.oathbound.economy;

import java.util.Objects;

public record Currency(String id) {
    public Currency {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Currency id must not be blank");
        }
    }
}
