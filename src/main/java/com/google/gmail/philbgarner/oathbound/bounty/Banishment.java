package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A real-time-scaled End-banishment sentence. A subsequent qualifying bounty kill while already serving
 * one extends the remaining time (see {@link #extend}) rather than resetting it, capped at a configured
 * maximum measured from {@code servingSince}. Mutation beyond construction is package-private, only meant
 * to happen through {@link BanishmentService}/{@link BanishmentSweepService}.
 */
public final class Banishment {
    private final UUID id;
    private final PlayerRef player;
    private final UUID triggeringBountyId;
    private final Instant servingSince;
    private Instant releaseAt;
    private final ReturnLocation returnLocation;
    private boolean released;

    public Banishment(UUID id, PlayerRef player, UUID triggeringBountyId, Instant servingSince, Instant releaseAt,
                       ReturnLocation returnLocation, boolean released) {
        this.id = Objects.requireNonNull(id, "id");
        this.player = Objects.requireNonNull(player, "player");
        this.triggeringBountyId = triggeringBountyId;
        this.servingSince = Objects.requireNonNull(servingSince, "servingSince");
        this.releaseAt = Objects.requireNonNull(releaseAt, "releaseAt");
        this.returnLocation = Objects.requireNonNull(returnLocation, "returnLocation");
        this.released = released;
    }

    public UUID id() {
        return id;
    }

    public PlayerRef player() {
        return player;
    }

    public UUID triggeringBountyId() {
        return triggeringBountyId;
    }

    public Instant servingSince() {
        return servingSince;
    }

    public Instant releaseAt() {
        return releaseAt;
    }

    public ReturnLocation returnLocation() {
        return returnLocation;
    }

    public boolean released() {
        return released;
    }

    public boolean active(Instant now) {
        return !released && now.isBefore(releaseAt);
    }

    /** Extends the remaining sentence by {@code additional}, capped so the total duration measured from
     * {@code servingSince} never exceeds {@code stackCap} - further stacking beyond the cap is absorbed. */
    void extend(Duration additional, Duration stackCap) {
        Instant proposed = releaseAt.plus(additional);
        Instant cappedAt = servingSince.plus(stackCap);
        this.releaseAt = proposed.isAfter(cappedAt) ? cappedAt : proposed;
        this.released = false;
    }

    void release() {
        this.released = true;
    }

    /** Seam for a future release-oath hook (an ally negotiating a shortened sentence via the normal Oath
     * system) - nothing calls this yet; deliberately left unused so this phase doesn't have to guess at
     * the Oath-side clause shape. */
    public void reduceSentence(Duration reduction, Instant now) {
        Instant reduced = releaseAt.minus(reduction);
        this.releaseAt = reduced.isBefore(now) ? now : reduced;
    }
}
