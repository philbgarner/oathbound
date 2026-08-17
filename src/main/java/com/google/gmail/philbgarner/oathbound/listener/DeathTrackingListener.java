package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Feeds every player death into {@link com.google.gmail.philbgarner.oathbound.oath.DeathTracker} - the
 * live backend behind {@code DeathCount} conditions. */
public final class DeathTrackingListener implements Listener {

    private final OathboundPlugin plugin;

    public DeathTrackingListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        PlayerRef killer = event.getEntity().getKiller() != null
                ? new PlayerRef(event.getEntity().getKiller().getUniqueId())
                : null;
        plugin.deathTracker().recordDeath(new PlayerRef(event.getEntity().getUniqueId()), killer);
    }
}
