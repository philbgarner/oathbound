package com.google.gmail.philbgarner.oathbound.economy;

import java.util.Objects;

public record Currency(String id) {
    public Currency {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Currency id must not be blank");
        }
    }

    /** Gson's default Map serialization always calls {@code String.valueOf(key)} for the JSON property
     * name, regardless of any registered key TypeAdapter (that's only consulted on read) - overriding
     * this to the bare id, rather than the record-default {@code "Currency[id=coin]"}, is what makes any
     * {@code Map<Currency, ...>} (escrow amounts, bounty rewards) round-trip correctly at all. */
    @Override
    public String toString() {
        return id;
    }
}
