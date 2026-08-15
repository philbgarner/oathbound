package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The Sealing Table: right-clicking the configured block opens the same named-party oath draft flow a
 * Notary's "New Oath" button does - a face-to-face alternate entry point, per the design doc, rather
 * than an async mailbox (no "Pending Oaths" option here, just the draft prompt).
 */
public final class SealingTableListener implements Listener {

    private final OathboundPlugin plugin;

    public SealingTableListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != plugin.oathboundConfig().sealingTableMaterial()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        plugin.oathDraftPromptListener().beginPrompt(player);
    }
}
