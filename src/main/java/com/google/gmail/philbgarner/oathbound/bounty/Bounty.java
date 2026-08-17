package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A standing kill contract, unilaterally placed by {@code placer} against {@code target} and paid out
 * per head turned in rather than through the two-party Oath lifecycle - see {@link BountyService}'s
 * class javadoc for why this isn't built on {@code Oath}. Mutation beyond construction is package-private,
 * only meant to happen through {@link BountyService}, mirroring {@code Oath}'s own pattern.
 */
public final class Bounty {
    private final UUID id;
    private final PlayerRef placer;
    private final BountyTarget target;
    private final int originalQuantity;
    private int remainingQuantity;
    private final Map<Currency, Long> totalReward;
    private final long feePaid;
    private final Instant placedAt;
    private BountyStatus status;
    private final Set<PlayerRef> notifiedVictims = new HashSet<>();
    private final Set<PlayerRef> abandonmentCounted = new HashSet<>();

    public Bounty(UUID id, PlayerRef placer, BountyTarget target, int originalQuantity, int remainingQuantity,
                  Map<Currency, Long> totalReward, long feePaid, Instant placedAt, BountyStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.placer = Objects.requireNonNull(placer, "placer");
        this.target = Objects.requireNonNull(target, "target");
        if (originalQuantity <= 0) {
            throw new IllegalArgumentException("originalQuantity must be positive");
        }
        this.originalQuantity = originalQuantity;
        this.remainingQuantity = remainingQuantity;
        this.totalReward = new HashMap<>(Objects.requireNonNull(totalReward, "totalReward"));
        this.feePaid = feePaid;
        this.placedAt = Objects.requireNonNull(placedAt, "placedAt");
        this.status = Objects.requireNonNull(status, "status");
    }

    public UUID id() {
        return id;
    }

    public PlayerRef placer() {
        return placer;
    }

    public BountyTarget target() {
        return target;
    }

    public int originalQuantity() {
        return originalQuantity;
    }

    public int remainingQuantity() {
        return remainingQuantity;
    }

    public Map<Currency, Long> totalReward() {
        return Map.copyOf(totalReward);
    }

    public long feePaid() {
        return feePaid;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public BountyStatus status() {
        return status;
    }

    public Set<PlayerRef> notifiedVictims() {
        return Set.copyOf(notifiedVictims);
    }

    public boolean isNotified(PlayerRef victim) {
        return notifiedVictims.contains(victim);
    }

    public boolean isAbandonmentCounted(PlayerRef member) {
        return abandonmentCounted.contains(member);
    }

    /** What paying out the next head owes, in full: floor(total/original) per currency for every head
     * but the last, which instead pays whatever floor division left over so the sum across every head
     * always equals {@link #totalReward()} exactly regardless of per-currency rounding. */
    Map<Currency, Long> rewardForNextHead() {
        Map<Currency, Long> perHead = new HashMap<>();
        boolean isLast = remainingQuantity == 1;
        int headsAlreadyPaid = originalQuantity - remainingQuantity;
        for (Map.Entry<Currency, Long> entry : totalReward.entrySet()) {
            long floor = entry.getValue() / originalQuantity;
            perHead.put(entry.getKey(), isLast ? entry.getValue() - floor * headsAlreadyPaid : floor);
        }
        return perHead;
    }

    /** Unpaid entitlement, for a cancellation refund. Every head paid out so far (if any) was paid at
     * the floored per-head rate - cancellation is only reachable while still {@code ACTIVE}, i.e. before
     * the last head's remainder payment could ever occur. */
    Map<Currency, Long> remainingReward() {
        Map<Currency, Long> remaining = new HashMap<>();
        int headsAlreadyPaid = originalQuantity - remainingQuantity;
        for (Map.Entry<Currency, Long> entry : totalReward.entrySet()) {
            long floor = entry.getValue() / originalQuantity;
            remaining.put(entry.getKey(), entry.getValue() - floor * headsAlreadyPaid);
        }
        return remaining;
    }

    void decrementRemaining() {
        if (remainingQuantity > 0) {
            remainingQuantity--;
        }
    }

    void setStatus(BountyStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    void markNotified(PlayerRef victim) {
        notifiedVictims.add(victim);
    }

    void markAbandonmentCounted(PlayerRef member) {
        abandonmentCounted.add(member);
    }
}
