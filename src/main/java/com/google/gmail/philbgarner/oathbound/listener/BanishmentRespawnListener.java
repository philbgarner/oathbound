package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.time.Instant;

/** A dead player can't be relocated by {@code PlayerDeathEvent} itself - this catches the respawn that
 * follows and, if {@link BountyKillListener} just started or extended a still-active banishment for
 * them, redirects the respawn to the End pen instead of their bed/anchor/world spawn. */
public final class BanishmentRespawnListener implements Listener {

    private final OathboundPlugin plugin;

    public BanishmentRespawnListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerRef playerRef = new PlayerRef(event.getPlayer().getUniqueId());
        Instant now = Instant.now();
        boolean stillServing = plugin.banishmentCache().values().stream()
                .anyMatch(banishment -> banishment.player().equals(playerRef) && banishment.active(now));
        if (stillServing) {
            event.setRespawnLocation(plugin.banishmentPenLocation());
        }
    }
}
