package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.board.BoardLocation;
import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Click/drag glue for the Oath Board hub, group picker, and read-only feed - a standalone flow
 * unrelated to oath-building, so it gets its own listener. */
public final class OathBoardGuiListener implements Listener {

    private final OathboundPlugin plugin;

    public OathBoardGuiListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof OathBoardHubHolder hubHolder) {
            handleHubClick(event, hubHolder);
        } else if (holder instanceof OathBoardGroupPickerHolder pickerHolder) {
            handlePickerClick(event, pickerHolder);
        } else if (holder instanceof OathBoardFeedHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof OathBoardHubHolder || holder instanceof OathBoardGroupPickerHolder
                || holder instanceof OathBoardFeedHolder) {
            event.setCancelled(true);
        }
    }

    private void handleHubClick(InventoryClickEvent event, OathBoardHubHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == OathBoardHubHolder.BROWSE_SLOT) {
            player.closeInventory();
            OathBoardFeedGui.open(plugin, player, holder.location());
        } else if (rawSlot == OathBoardHubHolder.BIND_GROUP_SLOT) {
            player.closeInventory();
            OathBoardGroupPickerGui.open(plugin, player, holder.location());
        } else if (rawSlot == OathBoardHubHolder.MAKE_CAPITAL_SLOT) {
            bindBoard(player, holder.location(), null);
            player.sendMessage("Board set to Capital - it now shows every witnessed posting.");
            player.closeInventory();
        } else if (rawSlot == OathBoardHubHolder.CANCEL_SLOT) {
            player.closeInventory();
        }
    }

    private void handlePickerClick(InventoryClickEvent event, OathBoardGroupPickerHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == OathBoardGroupPickerHolder.CANCEL_SLOT) {
            OathBoardHubGui.open(plugin, player, holder.location());
            return;
        }
        if (!OathBoardGroupPickerHolder.isListSlot(rawSlot)) {
            return;
        }
        UUID groupId = holder.groupIdAt(rawSlot);
        if (groupId == null) {
            return;
        }
        bindBoard(player, holder.location(), new ProtectionGroupRef(groupId));
        player.sendMessage("Board bound to that group.");
        player.closeInventory();
    }

    private void bindBoard(Player player, BoardLocation location, ProtectionGroupRef regionalGroup) {
        Optional<OathBoard> existing = plugin.oathBoardCache().values().stream()
                .filter(b -> b.location().equals(location))
                .findFirst();
        OathBoard board = existing
                .map(b -> new OathBoard(b.id(), regionalGroup, b.location(), b.installer(), b.installedAt()))
                .orElseGet(() -> new OathBoard(UUID.randomUUID(), regionalGroup, location,
                        new PlayerRef(player.getUniqueId()), Instant.now()));
        plugin.oathBoardCache().put(board.id(), board);
        plugin.persistOathBoardAsync(board);
    }
}
