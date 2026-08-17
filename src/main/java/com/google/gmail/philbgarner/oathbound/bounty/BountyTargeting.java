package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Whether a {@link BountyTarget} covers a given player - directly for {@code Solo}, via current
 * membership for {@code Group}. Shared by every call site that needs to ask "is this player on this
 * bounty's target list right now" (the kill listener, the player-facing list command, the login notice). */
public final class BountyTargeting {
    private BountyTargeting() {
    }

    public static boolean matches(BountyTarget target, PlayerRef player,
                                   Function<UUID, Optional<ProtectionGroup>> groupLookup) {
        if (target instanceof BountyTarget.Solo solo) {
            return solo.player().equals(player);
        }
        if (target instanceof BountyTarget.Group group) {
            return groupLookup.apply(group.group().groupId())
                    .map(g -> g.members().stream().anyMatch(member -> member.player().equals(player)))
                    .orElse(false);
        }
        return false;
    }
}
