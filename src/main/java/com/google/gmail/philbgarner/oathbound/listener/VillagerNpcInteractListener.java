package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.gui.VillagerShopGui;
import com.google.gmail.philbgarner.oathbound.villager.VillagerNpc;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/** Right-clicking a Villager that backs a {@link VillagerNpc} opens its shop menu instead of the vanilla
 * trade GUI, mirroring {@link NotaryInteractListener}. */
public final class VillagerNpcInteractListener implements Listener {

    private final OathboundPlugin plugin;

    public VillagerNpcInteractListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        Optional<VillagerNpc> npc = plugin.villagerNpcCache().values().stream()
                .filter(n -> n.entityId().equals(villager.getUniqueId()))
                .findFirst();
        if (npc.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        VillagerShopGui.open(plugin, player, npc.get());
    }
}
