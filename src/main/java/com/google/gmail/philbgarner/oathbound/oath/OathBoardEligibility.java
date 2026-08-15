package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;

import java.util.Objects;

/**
 * Pure Oath Board posting rules - no Bukkit, no persistence. Per the design doc, only *witnessed*
 * oaths post (respects the privacy of unwitnessed deals), and only on sealing, fulfillment, or breach -
 * proposing, activating, and voiding are deliberately silent.
 */
public final class OathBoardEligibility {

    private OathBoardEligibility() {
    }

    public static boolean isPostable(Oath oath, OathState toState) {
        Objects.requireNonNull(oath, "oath");
        Objects.requireNonNull(toState, "toState");
        if (oath.witnesses().isEmpty()) {
            return false;
        }
        return toState == OathState.SEALED || toState == OathState.FULFILLED || toState == OathState.BROKEN;
    }

    /** Whether a witnessed, postable oath is "this group's business" for a regional board - true if
     * either party is currently a member of the group. */
    public static boolean isRegionallyRelevant(Oath oath, ProtectionGroup group) {
        Objects.requireNonNull(oath, "oath");
        Objects.requireNonNull(group, "group");
        for (PlayerRef party : oath.parties()) {
            if (group.findMember(party).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
