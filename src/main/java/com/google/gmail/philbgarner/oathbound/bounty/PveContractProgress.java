package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One player's progress against a standing {@link PveContractDefinition}, identified by
 * {@code contractId} (matching {@code PveContractDefinition.id()} in config) rather than a foreign key
 * to a persisted row, since definitions themselves aren't persisted. */
public final class PveContractProgress {
    private final UUID id;
    private final PlayerRef player;
    private final String contractId;
    private int killsSoFar;
    private final Instant acceptedAt;
    private int timesCompleted;

    public PveContractProgress(UUID id, PlayerRef player, String contractId, int killsSoFar, Instant acceptedAt,
                                int timesCompleted) {
        this.id = Objects.requireNonNull(id, "id");
        this.player = Objects.requireNonNull(player, "player");
        this.contractId = Objects.requireNonNull(contractId, "contractId");
        this.killsSoFar = killsSoFar;
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
        this.timesCompleted = timesCompleted;
    }

    public UUID id() {
        return id;
    }

    public PlayerRef player() {
        return player;
    }

    public String contractId() {
        return contractId;
    }

    public int killsSoFar() {
        return killsSoFar;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public int timesCompleted() {
        return timesCompleted;
    }

    public void recordKill() {
        killsSoFar++;
    }

    public boolean isComplete(int requiredQuantity) {
        return killsSoFar >= requiredQuantity;
    }

    /** Standing contracts are repeatable - resets progress and bumps the completion counter once a
     * completed batch has been paid out. */
    public void resetForRepeat() {
        killsSoFar = 0;
        timesCompleted++;
    }
}
