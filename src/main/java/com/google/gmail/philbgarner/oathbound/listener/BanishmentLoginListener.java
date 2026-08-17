package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.Banishment;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** On login: if a sentence already elapsed while the player was offline, release and teleport them back
 * now (the periodic sweep in {@code OathboundPlugin.runConditionEngineTick} only catches this for
 * players already online at the moment it elapses). If still actively serving, force them back to the
 * pen - closes the evade-via-relogin loophole a normal login/respawn point would otherwise open. Mirrors
 * {@link AltarWarningListener}'s login-nudge shape. */
public final class BanishmentLoginListener implements Listener {

    private final OathboundPlugin plugin;

    public BanishmentLoginListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef playerRef = new PlayerRef(event.getPlayer().getUniqueId());
        Instant now = Instant.now();
        Optional<Banishment> maybeBanishment = plugin.banishmentCache().values().stream()
                .filter(banishment -> banishment.player().equals(playerRef) && !banishment.released())
                .findFirst();
        if (maybeBanishment.isEmpty()) {
            return;
        }
        Banishment banishment = maybeBanishment.get();
        if (now.isBefore(banishment.releaseAt())) {
            event.getPlayer().teleport(plugin.banishmentPenLocation());
            long hoursRemaining = Duration.between(now, banishment.releaseAt()).toHours() + 1;
            event.getPlayer().sendMessage("You are still banished - about " + hoursRemaining + " hour(s) remain.");
            return;
        }
        plugin.banishmentService().forceRelease(banishment);
        plugin.persistBanishmentAsync(banishment);
        event.getPlayer().teleport(plugin.toBukkitLocation(banishment.returnLocation()));
        event.getPlayer().sendMessage("Your banishment ended while you were away - welcome back.");
    }
}
