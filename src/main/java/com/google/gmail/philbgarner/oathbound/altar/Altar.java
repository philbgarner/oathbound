package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Created the moment its physical structure (barrel + capstone + candle) is completed, at zero Power.
 * Power is never stored as a raw counter - like radius, it's a live function of {@code powerBaseline}
 * (the Power as of the most recent sacrifice) and how many days have passed since {@code lastSacrificeAt},
 * decaying linearly to zero over a configured number of days. See {@link AltarPowerMath}.
 */
public final class Altar {
    private final UUID id;
    private final EntityRef owner;
    private final AltarLocation location;
    private final Instant consecratedAt;

    private long powerBaseline;
    private Instant lastSacrificeAt;
    private long lastSacrificeValue;
    private Instant cooldownUntil;
    private AltarVulnerabilityTier lastKnownTier;

    public Altar(UUID id, EntityRef owner, AltarLocation location, Instant consecratedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.location = Objects.requireNonNull(location, "location");
        this.consecratedAt = Objects.requireNonNull(consecratedAt, "consecratedAt");
        this.powerBaseline = 0L;
        this.lastSacrificeAt = consecratedAt;
        this.lastSacrificeValue = 0L;
        this.cooldownUntil = consecratedAt;
        this.lastKnownTier = AltarVulnerabilityTier.NORMAL;
    }

    /** Live-computed current Power - a linear decay of {@code powerBaseline} over the configured
     * number of decay days since {@code lastSacrificeAt}. Never stored. */
    public long currentPower(Instant now, int decayDays) {
        return AltarPowerMath.currentPower(powerBaseline, lastSacrificeAt, now, decayDays);
    }

    /** Records a completed sacrifice: the new Power baseline (existing live Power plus the sacrifice's
     * value), resets the decay clock, starts a reconsecration cooldown, and remembers this sacrifice's
     * raw value for a possible future Loot outcome. */
    public void applySacrifice(long newBaseline, Instant now, Duration cooldown, long sacrificeValue) {
        this.powerBaseline = newBaseline;
        this.lastSacrificeAt = now;
        this.cooldownUntil = now.plus(cooldown);
        this.lastSacrificeValue = sacrificeValue;
    }

    /** Hard-resets Power to zero - used right before a Desecrated altar's record is deleted outright. */
    public void desecrate() {
        this.powerBaseline = 0L;
    }

    /** Clears the last-sacrifice-value marker after it's been paid out via a Loot outcome. */
    public void clearLastSacrificeValue() {
        this.lastSacrificeValue = 0L;
    }

    public boolean cooledDown(Instant now) {
        return !now.isBefore(cooldownUntil);
    }

    public void setLastKnownTier(AltarVulnerabilityTier tier) {
        this.lastKnownTier = Objects.requireNonNull(tier, "tier");
    }

    public AltarVulnerabilityTier lastKnownTier() {
        return lastKnownTier;
    }

    public UUID id() {
        return id;
    }

    public EntityRef owner() {
        return owner;
    }

    public AltarLocation location() {
        return location;
    }

    public Instant consecratedAt() {
        return consecratedAt;
    }

    public long powerBaseline() {
        return powerBaseline;
    }

    public Instant lastSacrificeAt() {
        return lastSacrificeAt;
    }

    public long lastSacrificeValue() {
        return lastSacrificeValue;
    }

    public Instant cooldownUntil() {
        return cooldownUntil;
    }
}
