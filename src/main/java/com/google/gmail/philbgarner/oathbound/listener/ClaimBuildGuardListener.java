package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarLocation;
import com.google.gmail.philbgarner.oathbound.altar.ClaimAccessService;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Protects block break/place inside any Altar's live claim radius from non-permitted players, via
 * {@link ClaimAccessService#protectedAltar}. Uses the cooldown-aware lookup, not the raw covering-altar
 * one, so an altar mid-reconsecration-cooldown is treated as unprotected even though its Power/radius
 * already read as sufficient.
 */
public final class ClaimBuildGuardListener implements Listener {

    private final OathboundPlugin plugin;

    public ClaimBuildGuardListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!canBuild(event.getBlock(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("This land is claimed - you can't break blocks here.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getBlock(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("This land is claimed - you can't place blocks here.");
        }
    }

    private boolean canBuild(Block block, UUID playerId) {
        AltarLocation location =
                new AltarLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        Optional<Altar> altar = ClaimAccessService.protectedAltar(location, plugin.altarCache().values(),
                Instant.now(), plugin.oathboundConfig().altarDecayDays(),
                plugin.altarRadiusCalculator(), id -> Optional.ofNullable(plugin.groupCache().get(id)));
        if (altar.isEmpty()) {
            return true;
        }
        PlayerRef playerRef = new PlayerRef(playerId);
        return ClaimAccessService.canBuild(altar.get(), playerRef,
                id -> Optional.ofNullable(plugin.groupCache().get(id)));
    }
}
