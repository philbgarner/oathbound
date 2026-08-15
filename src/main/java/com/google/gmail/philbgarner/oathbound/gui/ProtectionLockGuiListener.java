package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.protection.Protection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.time.Instant;
import java.util.UUID;

/** Click/drag glue for {@link ProtectionLockHolder} - a standalone flow unrelated to oath-building or
 * trade, so it gets its own listener rather than folding into {@link OathBuilderListener} or
 * {@link TradeGuiListener}. */
public final class ProtectionLockGuiListener implements Listener {

    private final OathboundPlugin plugin;

    public ProtectionLockGuiListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ProtectionLockHolder lockHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (ProtectionLockHolder.isListSlot(rawSlot)) {
            UUID groupId = lockHolder.groupIdAt(rawSlot);
            if (groupId != null) {
                lockTo(player, lockHolder, groupId);
                player.closeInventory();
            }
        } else if (rawSlot == ProtectionLockHolder.UNLOCK_SLOT && lockHolder.unlockAvailable()) {
            unlock(player, lockHolder);
            player.closeInventory();
        } else if (rawSlot == ProtectionLockHolder.CANCEL_SLOT) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ProtectionLockHolder) {
            event.setCancelled(true);
        }
    }

    private void lockTo(Player player, ProtectionLockHolder holder, UUID groupId) {
        UUID id = plugin.protectionCache().values().stream()
                .filter(protection -> protection.location().equals(holder.location()))
                .map(Protection::id)
                .findFirst()
                .orElseGet(UUID::randomUUID);
        Protection protection = new Protection(id, holder.location(), holder.type(),
                new ProtectionGroupRef(groupId), Instant.now());
        plugin.protectionCache().put(protection.id(), protection);
        plugin.persistProtectionAsync(protection);

        ProtectionGroup group = plugin.groupCache().get(groupId);
        player.sendMessage("Locked to " + (group != null ? group.name() : "that group") + ".");
    }

    private void unlock(Player player, ProtectionLockHolder holder) {
        plugin.protectionCache().values().stream()
                .filter(protection -> protection.location().equals(holder.location()))
                .findFirst()
                .ifPresent(protection -> {
                    plugin.protectionCache().remove(protection.id());
                    plugin.deleteProtectionAsync(protection.id());
                });
        player.sendMessage("Unlocked.");
    }
}
