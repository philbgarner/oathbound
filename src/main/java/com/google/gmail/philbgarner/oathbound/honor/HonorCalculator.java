package com.google.gmail.philbgarner.oathbound.honor;

import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Oath;

import java.util.Objects;

/**
 * Pure Honor-delta math for oath resolution - no Bukkit, no persistence. Both gain and loss scale with
 * {@link OathSeverity}, and Blood Oaths amplify whichever direction applies ("Blood Oaths matter more,"
 * both on fulfillment and on breach, per the design doc). Breach loss is deliberately a larger swing
 * than the corresponding gain by giving {@code breachLossBase} a bigger default than
 * {@code fulfillGainBase} in config - this class just applies whatever the two bases are.
 *
 * <p>An oath made entirely of {@link Clause.CustomFlagClause}s (no real mechanical stakes - a pure RP
 * pledge) scores zero Honor movement either way, not just low severity. Without this, a zero-stakes
 * oath like the built-in {@code welcome-pact} ceremony still resolves at severity 1 like any other oath
 * and mints the full {@code fulfillGainBase} for free, repeatably, for both parties.</p>
 */
public final class HonorCalculator {
    private final long fulfillGainBase;
    private final long breachLossBase;
    private final double bloodOathMultiplier;

    public HonorCalculator(long fulfillGainBase, long breachLossBase, double bloodOathMultiplier) {
        this.fulfillGainBase = fulfillGainBase;
        this.breachLossBase = breachLossBase;
        this.bloodOathMultiplier = bloodOathMultiplier;
    }

    /** Positive amount to add to Honor on FULFILLED. */
    public long fulfillGain(Oath oath) {
        Objects.requireNonNull(oath, "oath");
        return scale(fulfillGainBase, oath);
    }

    /** Positive magnitude to subtract from Honor on BROKEN. */
    public long breachLoss(Oath oath) {
        Objects.requireNonNull(oath, "oath");
        return scale(breachLossBase, oath);
    }

    private long scale(long base, Oath oath) {
        if (hasNoRealStakes(oath)) {
            return 0L;
        }
        long scaled = base * OathSeverity.of(oath);
        return oath.bloodOath() ? Math.round(scaled * bloodOathMultiplier) : scaled;
    }

    /** True only for a non-empty clause list made up entirely of {@link Clause.CustomFlagClause}s - an
     * oath with no clauses at all still scores at severity 1 via {@link OathSeverity}, unaffected by
     * this check, since that's the existing baseline for a not-yet-fleshed-out draft, not the free-Honor
     * shape this guards against. */
    private static boolean hasNoRealStakes(Oath oath) {
        return !oath.clauses().isEmpty()
                && oath.clauses().stream().allMatch(clause -> clause instanceof Clause.CustomFlagClause);
    }
}
