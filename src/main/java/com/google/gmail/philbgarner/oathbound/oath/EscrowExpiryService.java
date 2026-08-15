package com.google.gmail.philbgarner.oathbound.oath;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Sweeps unclaimed {@link EscrowClaim}s past their expiry window back to the depositor. */
public final class EscrowExpiryService {

    public List<EscrowClaim> sweep(Collection<EscrowClaim> claims, Instant now, Duration expiry) {
        List<EscrowClaim> changed = new ArrayList<>();
        for (EscrowClaim claim : claims) {
            if (!claim.claimed() && !claim.expired() && now.isAfter(claim.releasedAt().plus(expiry))) {
                claim.expire();
                changed.add(claim);
            }
        }
        return changed;
    }
}
