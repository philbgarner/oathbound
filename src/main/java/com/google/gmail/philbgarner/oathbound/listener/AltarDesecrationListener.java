package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarLocation;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerability;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerabilityTier;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.honor.PlayerHonor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.time.Instant;

/**
 * Only at Critical vulnerability does an altar's barrel become breakable at all - breaking it there is
 * a Desecration: Honor penalty for the destroyer, a dramatic server-wide broadcast (mirroring the
 * existing Blood-Oath-break broadcast), and the altar record is deleted outright, requiring the
 * barrel/capstone/candle structure to be physically rebuilt from scratch to reclaim the territory.
 */
public final class AltarDesecrationListener implements Listener {

    private final OathboundPlugin plugin;

    public AltarDesecrationListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARREL) {
            return;
        }
        AltarLocation location = new AltarLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        Altar altar = findByLocation(location);
        if (altar == null) {
            return;
        }

        Instant now = Instant.now();
        int decayDays = plugin.oathboundConfig().altarDecayDays();
        long power = altar.currentPower(now, decayDays);
        AltarVulnerabilityTier tier = AltarVulnerability.classify(power,
                plugin.oathboundConfig().altarCriticalThreshold(), plugin.oathboundConfig().altarDecayingThreshold());

        if (tier != AltarVulnerabilityTier.CRITICAL) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("This altar is still consecrated - it can't be destroyed while active.");
            return;
        }

        PlayerRef breaker = new PlayerRef(event.getPlayer().getUniqueId());
        long penalty = plugin.oathboundConfig().altarDesecrationHonorPenalty();
        if (penalty != 0) {
            long newHonor = plugin.honorService().adjust(breaker, -penalty);
            plugin.persistHonorAsync(new PlayerHonor(breaker, newHonor));
        }

        String message = "The altar of " + ownerLabel(altar.owner()) + " has been DESECRATED by "
                + event.getPlayer().getName() + "!";
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }

        altar.desecrate();
        plugin.altarCache().remove(altar.id());
        plugin.deleteAltarAsync(altar.id());
    }

    private String ownerLabel(EntityRef owner) {
        if (owner instanceof PlayerRef playerRef) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerRef.playerId());
            return player.getName() != null ? player.getName() : playerRef.playerId().toString();
        }
        if (owner instanceof ProtectionGroupRef groupRef) {
            ProtectionGroup group = plugin.groupCache().get(groupRef.groupId());
            return group != null ? group.name() : groupRef.groupId().toString();
        }
        return owner.toString();
    }

    private Altar findByLocation(AltarLocation location) {
        for (Altar altar : plugin.altarCache().values()) {
            if (altar.location().equals(location)) {
                return altar;
            }
        }
        return null;
    }
}
