package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarLocation;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerability;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerabilityTier;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.gui.AltarSacrificeGui;
import com.google.gmail.philbgarner.oathbound.gui.BanishmentPrayerBoardGui;
import com.google.gmail.philbgarner.oathbound.honor.PlayerHonor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.time.Instant;
import java.util.Optional;

/**
 * Right-clicking a consecrated altar's barrel opens the sacrifice interface, unless the altar is
 * Critical - in which case the barrel is a Loot target instead (Destroy is handled separately, on
 * break, by {@link AltarDesecrationListener}).
 */
public final class AltarInteractListener implements Listener {

    private final OathboundPlugin plugin;

    public AltarInteractListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BARREL) {
            return;
        }
        AltarLocation location = new AltarLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        Altar altar = findByLocation(location);
        if (altar == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);

        OwnershipResolver.Resolution resolution = plugin.ownershipResolver().resolveTerminalOwner(altar.owner());
        if (resolution instanceof OwnershipResolver.Unresolved) {
            event.getPlayer().sendMessage("This altar's owner no longer exists.");
            return;
        }

        Instant now = Instant.now();
        int decayDays = plugin.oathboundConfig().altarDecayDays();
        long power = altar.currentPower(now, decayDays);
        AltarVulnerabilityTier tier = AltarVulnerability.classify(power,
                plugin.oathboundConfig().altarCriticalThreshold(), plugin.oathboundConfig().altarDecayingThreshold());

        if (tier == AltarVulnerabilityTier.CRITICAL) {
            handleLoot(event.getPlayer(), altar);
        } else if (event.getPlayer().isSneaking()) {
            // Shift-right-click on a legitimately protected altar opens the intercession board instead of
            // the normal sacrifice screen - praying for a banished player's release is a different ritual
            // spending the same currency (see BanishmentPrayerGuiListener), not a Power deposit.
            BanishmentPrayerBoardGui.open(plugin, event.getPlayer());
        } else {
            AltarSacrificeGui.open(plugin, event.getPlayer(), altar.id());
        }
    }

    private void handleLoot(Player player, Altar altar) {
        long xpAmount = Math.round(altar.lastSacrificeValue() * plugin.oathboundConfig().altarXpLootConversionRate());
        if (xpAmount > 0) {
            player.getWorld().spawn(player.getLocation(), org.bukkit.entity.ExperienceOrb.class,
                    orb -> orb.setExperience((int) Math.min(Integer.MAX_VALUE, xpAmount)));
        }
        long lootPenalty = plugin.oathboundConfig().altarLootHonorPenalty();
        if (lootPenalty != 0) {
            PlayerRef looter = new PlayerRef(player.getUniqueId());
            long newHonor = plugin.honorService().adjust(looter, -lootPenalty);
            plugin.persistHonorAsync(new PlayerHonor(looter, newHonor));
        }
        altar.clearLastSacrificeValue();
        plugin.persistAltarAsync(altar);
        player.sendMessage("You loot the cold altar. It yields " + xpAmount + " experience.");
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
