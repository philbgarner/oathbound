package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarLocation;
import com.google.gmail.philbgarner.oathbound.altar.AltarNestingService;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Altar placement is barrel -> configured capstone block -> candle, stacked directly on top of each
 * other. Placing the candle is the trigger: Minecraft only lets a candle be placed on solid support,
 * so by the time this event fires the two blocks below are guaranteed to already be in place.
 */
public final class AltarConsecrationListener implements Listener {

    private final OathboundPlugin plugin;

    public AltarConsecrationListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCandlePlace(BlockPlaceEvent event) {
        Block candleBlock = event.getBlock();
        if (!Tag.CANDLES.isTagged(candleBlock.getType())) {
            return;
        }

        Block capstoneBlock = candleBlock.getRelative(BlockFace.DOWN);
        Material requiredCapstone = plugin.oathboundConfig().altarCapstoneMaterial();
        if (capstoneBlock.getType() != requiredCapstone) {
            return;
        }

        Block barrelBlock = capstoneBlock.getRelative(BlockFace.DOWN);
        if (barrelBlock.getType() != Material.BARREL) {
            return;
        }

        AltarLocation location = new AltarLocation(
                barrelBlock.getWorld().getUID(), barrelBlock.getX(), barrelBlock.getY(), barrelBlock.getZ());

        boolean alreadyConsecrated = plugin.altarCache().values().stream()
                .anyMatch(altar -> altar.location().equals(location));
        if (alreadyConsecrated) {
            event.getPlayer().sendMessage("An altar is already consecrated here.");
            return;
        }

        AltarNestingService.NestingResult nesting = AltarNestingService.checkPlacement(
                location, GroupTier.INDIVIDUAL, plugin.altarCache().values(), Instant.now(),
                plugin.oathboundConfig().altarDecayDays(), plugin.altarRadiusCalculator(),
                id -> Optional.ofNullable(plugin.groupCache().get(id)));
        if (!nesting.legal()) {
            event.getPlayer().sendMessage("This location falls within another altar's claim - consecration failed.");
            return;
        }

        PlayerRef owner = new PlayerRef(event.getPlayer().getUniqueId());
        Altar altar = new Altar(UUID.randomUUID(), owner, location, Instant.now(),
                plugin.oathboundConfig().altarStartingPower());
        plugin.altarCache().put(altar.id(), altar);
        plugin.persistAltarAsync(altar);

        event.getPlayer().sendMessage("Altar consecrated at " + location.x() + ", " + location.y() + ", "
                + location.z() + ". Charge it with a sacrifice ritual to claim territory.");
    }
}
