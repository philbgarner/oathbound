package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.Bounty;
import com.google.gmail.philbgarner.oathbound.bounty.BountyStatus;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTargeting;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

/** One-time login notice per bounty per victim - avoids forcing this on every login while active (a
 * one-time notice plus the toggleable {@code /oathbound-bounty list} pull command, per the master plan)
 * by tracking who's already been told via {@code Bounty.notifiedVictims}, and respects the player's
 * opt-out ({@code /oathbound-bounty notify off}). Mirrors {@link AltarWarningListener}'s login-nudge shape. */
public final class BountyLoginNoticeListener implements Listener {

    private final OathboundPlugin plugin;

    public BountyLoginNoticeListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef joined = new PlayerRef(event.getPlayer().getUniqueId());
        if (plugin.bountyNotificationOptOuts().contains(joined.playerId())) {
            return;
        }
        for (Bounty bounty : plugin.bountyCache().values()) {
            if (bounty.status() != BountyStatus.ACTIVE || bounty.isNotified(joined)) {
                continue;
            }
            if (!BountyTargeting.matches(bounty.target(), joined, id -> Optional.ofNullable(plugin.groupCache().get(id)))) {
                continue;
            }
            event.getPlayer().sendMessage("There is a bounty on your head - watch your back. "
                    + "(/oathbound-bounty notify off to silence this.)");
            plugin.bountyService().markNotified(bounty, joined);
            plugin.persistBountyAsync(bounty);
        }
    }
}
