package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarLocation;
import com.google.gmail.philbgarner.oathbound.altar.ClaimAccessService;
import org.bukkit.Location;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.time.Instant;
import java.util.Optional;

/**
 * Suppresses natural surface monster spawns inside a live, protected (non-cooldown) altar claim radius
 * - a tangible safety payoff for maintained territory, per the design doc. "Surface" is pragmatically
 * defined as at-or-above the highest solid block at that column (skylight-exposed); only
 * {@link CreatureSpawnEvent.SpawnReason#NATURAL} is filtered, so player-placed spawn eggs/spawners
 * inside a claim aren't silently voided.
 */
public final class AltarMonsterSpawnGuardListener implements Listener {

    private final OathboundPlugin plugin;

    public AltarMonsterSpawnGuardListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        Location location = event.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        int highestBlockY = location.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
        if (location.getBlockY() < highestBlockY) {
            return;
        }

        AltarLocation altarLocation = new AltarLocation(
                location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Optional<Altar> protecting = ClaimAccessService.protectedAltar(
                altarLocation, plugin.altarCache().values(), Instant.now(), plugin.oathboundConfig().altarDecayDays(),
                plugin.altarRadiusCalculator(), id -> Optional.ofNullable(plugin.groupCache().get(id)));
        if (protecting.isPresent()) {
            event.setCancelled(true);
        }
    }
}
