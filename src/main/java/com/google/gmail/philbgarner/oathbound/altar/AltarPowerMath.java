package com.google.gmail.philbgarner.oathbound.altar;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure decay math: a linear ramp from {@code baseline} down to zero over exactly {@code decayDays}
 * days, regardless of the baseline's magnitude - each altar's own last-topped-up total is its own
 * "full", so "days full-to-empty" is one server-wide tunable no matter how big or small an altar's
 * Power is.
 */
public final class AltarPowerMath {

    private AltarPowerMath() {
    }

    public static long currentPower(long baseline, Instant lastEventAt, Instant now, int decayDays) {
        if (baseline <= 0) {
            return 0L;
        }
        if (decayDays <= 0) {
            return baseline;
        }
        double daysElapsed = Duration.between(lastEventAt, now).toMillis() / 86_400_000.0;
        if (daysElapsed <= 0) {
            return baseline;
        }
        if (daysElapsed >= decayDays) {
            return 0L;
        }
        return Math.max(0L, (long) Math.floor(baseline * (1.0 - daysElapsed / decayDays)));
    }
}
