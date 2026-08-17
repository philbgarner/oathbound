package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes banishment sentence durations from a paid bounty amount (linear baseline, min/max clamp) and
 * either starts a new sentence or stacks a subsequent qualifying kill onto an already-active one.
 */
public final class BanishmentService {

    private final long minHours;
    private final long maxHours;
    private final long hoursPerCurrencyUnit;
    private final long stackCapHours;

    public BanishmentService(long minHours, long maxHours, long hoursPerCurrencyUnit, long stackCapHours) {
        this.minHours = minHours;
        this.maxHours = maxHours;
        this.hoursPerCurrencyUnit = hoursPerCurrencyUnit;
        this.stackCapHours = stackCapHours;
    }

    public long hoursFor(long bountyAmountPaid) {
        long raw = hoursPerCurrencyUnit <= 0 ? minHours : bountyAmountPaid / hoursPerCurrencyUnit;
        return Math.max(minHours, Math.min(maxHours, raw));
    }

    /** Starts a new sentence, or extends (never resets) an existing still-serving one, capped at
     * {@code stackCapHours} measured from the original {@code servingSince}. */
    public Banishment beginOrExtend(Optional<Banishment> existing, PlayerRef player, UUID triggeringBountyId,
                                     long bountyAmountPaid, Instant now, ReturnLocation returnLocationIfNew) {
        Duration additional = Duration.ofHours(hoursFor(bountyAmountPaid));
        Duration stackCap = Duration.ofHours(stackCapHours);

        if (existing.isPresent() && existing.get().active(now)) {
            Banishment banishment = existing.get();
            banishment.extend(additional, stackCap);
            return banishment;
        }

        Instant releaseAt = now.plus(additional);
        Instant cappedAt = now.plus(stackCap);
        if (releaseAt.isAfter(cappedAt)) {
            releaseAt = cappedAt;
        }
        return new Banishment(UUID.randomUUID(), player, triggeringBountyId, now, releaseAt,
                Objects.requireNonNull(returnLocationIfNew, "returnLocationIfNew"), false);
    }

    /** Forces immediate release ahead of schedule - the admin `/oathbound-debug banishment release`
     * override, and the on-login "already elapsed while offline" path. */
    public void forceRelease(Banishment banishment) {
        Objects.requireNonNull(banishment, "banishment");
        banishment.release();
    }
}
