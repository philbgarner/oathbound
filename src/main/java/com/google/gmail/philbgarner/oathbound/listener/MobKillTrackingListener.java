package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** Feeds every mob kill into {@link com.google.gmail.philbgarner.oathbound.oath.MobKillTracker} - the
 * live backend behind {@code MobKillCount} conditions. Unconditional, unlike
 * {@link PveKillListener}, which only tracks kills against a killer's already-accepted PvE contracts. */
public final class MobKillTrackingListener implements Listener {

    private final OathboundPlugin plugin;

    public MobKillTrackingListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        plugin.mobKillTracker().recordKill(new PlayerRef(killer.getUniqueId()), event.getEntityType().name());
    }
}
