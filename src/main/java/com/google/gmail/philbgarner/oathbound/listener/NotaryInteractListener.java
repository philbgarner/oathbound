package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.gui.NotaryMenuGui;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-clicking a Villager that backs a {@link com.google.gmail.philbgarner.oathbound.notary.Notary}
 * opens the Notary menu instead of the vanilla trade GUI. */
public final class NotaryInteractListener implements Listener {

    private final OathboundPlugin plugin;

    public NotaryInteractListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        boolean isNotary = plugin.notaryCache().values().stream()
                .anyMatch(notary -> notary.entityId().equals(villager.getUniqueId()));
        if (!isNotary) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        NotaryMenuGui.open(player);
    }
}
