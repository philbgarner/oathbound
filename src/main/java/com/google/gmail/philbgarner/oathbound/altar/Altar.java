package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Created the moment its physical structure (barrel + capstone + candle) is completed, at zero Power.
 * Power accrual via the sacrifice ritual, decay, and vulnerability tiers are a later phase - for now
 * this just records that the altar exists and who it belongs to.
 */
public record Altar(UUID id, EntityRef owner, AltarLocation location, long power, Instant consecratedAt) {
    public Altar {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(consecratedAt, "consecratedAt");
    }
}
