package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Click/drag glue for {@link NotaryMenuHolder} - a standalone hub screen unrelated to oath-building,
 * so it gets its own listener rather than folding into {@link OathBuilderListener}. */
public final class NotaryMenuGuiListener implements Listener {

    private final OathboundPlugin plugin;

    public NotaryMenuGuiListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof NotaryMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == NotaryMenuHolder.NEW_OATH_SLOT) {
            player.closeInventory();
            plugin.oathDraftPromptListener().beginPrompt(player);
        } else if (rawSlot == NotaryMenuHolder.PENDING_OATHS_SLOT) {
            player.closeInventory();
            PendingOathBoardGui.open(plugin, player);
        } else if (rawSlot == NotaryMenuHolder.CANCEL_SLOT) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NotaryMenuHolder) {
            event.setCancelled(true);
        }
    }
}
