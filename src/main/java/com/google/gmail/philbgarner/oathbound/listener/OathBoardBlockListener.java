package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.board.BoardLocation;
import com.google.gmail.philbgarner.oathbound.gui.OathBoardHubGui;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-clicking the configured Oath Board block always opens the hub - whether or not this exact
 * location has been bound to a scope yet, mirroring the Notary's always-open-a-menu pattern. */
public final class OathBoardBlockListener implements Listener {

    private final OathboundPlugin plugin;

    public OathBoardBlockListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != plugin.oathboundConfig().oathBoardMaterial()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        BoardLocation location = new BoardLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        OathBoardHubGui.open(plugin, player, location);
    }
}
