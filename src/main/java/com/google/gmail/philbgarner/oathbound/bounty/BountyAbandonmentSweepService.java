package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Auto-decrements a group-target {@link Bounty}'s remaining quantity once a targeted member has been
 * inactive past a configured threshold - prevents a long-running group bounty from becoming permanently
 * unfulfillable due to inactive members padding the original count. "Abandoned" is a live inactivity
 * check (no persisted flag): {@code lastPlayedLookup} is injected exactly like {@code ConditionEngine}'s
 * {@code groupLookup} seam, so the real {@code OfflinePlayer.getLastPlayed()} call stays out of this
 * Bukkit-free class and this stays unit-testable with a fake.
 */
public final class BountyAbandonmentSweepService {

    public List<Bounty> sweep(Collection<Bounty> bounties, Function<UUID, Optional<ProtectionGroup>> groupLookup,
                               Function<UUID, Instant> lastPlayedLookup, Duration inactivityThreshold, Instant now,
                               BountyService bountyService) {
        List<Bounty> changed = new ArrayList<>();
        for (Bounty bounty : bounties) {
            if (bounty.status() != BountyStatus.ACTIVE || !(bounty.target() instanceof BountyTarget.Group group)) {
                continue;
            }
            Optional<ProtectionGroup> protectionGroup = groupLookup.apply(group.group().groupId());
            if (protectionGroup.isEmpty()) {
                continue;
            }
            boolean touched = false;
            for (Member member : protectionGroup.get().members()) {
                if (bounty.isAbandonmentCounted(member.player())) {
                    continue;
                }
                Instant lastPlayed = lastPlayedLookup.apply(member.player().playerId());
                if (lastPlayed == null || now.minus(inactivityThreshold).isBefore(lastPlayed)) {
                    continue;
                }
                bountyService.decrementForAbandonment(bounty, member.player());
                touched = true;
                if (bounty.status() != BountyStatus.ACTIVE) {
                    break;
                }
            }
            if (touched) {
                changed.add(bounty);
            }
        }
        return changed;
    }
}
