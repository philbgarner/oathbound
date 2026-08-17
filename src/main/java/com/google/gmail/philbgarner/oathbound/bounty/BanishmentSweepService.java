package com.google.gmail.philbgarner.oathbound.bounty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Periodic sweep that releases any still-serving {@link Banishment} whose sentence has elapsed - mirrors
 * {@code EscrowExpiryService}/{@code AltarDecaySweepService}'s edge-fire-once shape, so a release/teleport
 * only fires exactly once per sentence rather than every tick. */
public final class BanishmentSweepService {

    public List<Banishment> sweep(Collection<Banishment> banishments, Instant now) {
        List<Banishment> released = new ArrayList<>();
        for (Banishment banishment : banishments) {
            if (!banishment.released() && !now.isBefore(banishment.releaseAt())) {
                banishment.release();
                released.add(banishment);
            }
        }
        return released;
    }
}
