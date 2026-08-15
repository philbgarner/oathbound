package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Sweeps named-party oaths that have sat PROPOSED too long back to VOIDED - an unanswered proposal
 * timing out is nobody's fault, so this reuses the same neutral transition mutual cancellation already
 * uses (no Honor change, per the design doc). Open oaths (the trade board) are deliberately excluded -
 * those are standing listings meant to sit until fulfilled or manually cancelled, not a personal mailbox
 * entry. Uses {@link Oath#createdAt()} as a proxy for "time since proposed" since {@code Oath} doesn't
 * separately track a proposedAt timestamp - in practice DRAFT lasts only as long as the builder GUI
 * session, so the approximation is close enough for an anti-spam sweep.
 */
public final class NegotiationExpiryService {

    public List<Oath> sweep(Collection<Oath> oaths, Instant now, Duration expiry, OathService oathService) {
        List<Oath> expired = new ArrayList<>();
        for (Oath oath : oaths) {
            if (!oath.open() && oath.state() == OathState.PROPOSED && now.isAfter(oath.createdAt().plus(expiry))) {
                oathService.voidOath(oath, PlayerRef.SYSTEM);
                expired.add(oath);
            }
        }
        return expired;
    }
}
