package com.google.gmail.philbgarner.oathbound.altar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Periodic sweep that edge-detects vulnerability-tier crossings for every altar - Power itself is a
 * live-computed value (see {@link AltarPowerMath}) and is never mutated here. Only the crossing itself
 * is reported, so a one-time warning/broadcast can fire exactly once per crossing rather than every
 * tick. Mirrors the shape of the other periodic sweep services ({@code EscrowExpiryService},
 * {@code NegotiationExpiryService}).
 */
public final class AltarDecaySweepService {

    public record TierCrossing(Altar altar, AltarVulnerabilityTier from, AltarVulnerabilityTier to) {
    }

    public List<TierCrossing> sweep(Collection<Altar> altars, Instant now, int decayDays,
                                     long criticalThreshold, long decayingThreshold) {
        List<TierCrossing> crossings = new ArrayList<>();
        for (Altar altar : altars) {
            long power = altar.currentPower(now, decayDays);
            AltarVulnerabilityTier newTier = AltarVulnerability.classify(power, criticalThreshold, decayingThreshold);
            if (newTier != altar.lastKnownTier()) {
                crossings.add(new TierCrossing(altar, altar.lastKnownTier(), newTier));
                altar.setLastKnownTier(newTier);
            }
        }
        return crossings;
    }
}
