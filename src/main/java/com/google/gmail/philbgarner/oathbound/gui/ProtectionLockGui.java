package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.protection.ProtectedLocation;
import com.google.gmail.philbgarner.oathbound.protection.Protection;
import com.google.gmail.philbgarner.oathbound.protection.ProtectionAccessService;
import com.google.gmail.philbgarner.oathbound.protection.ProtectionType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Chest GUI listing the groups the actor is allowed to lock a chest/door to, opened by right-clicking
 * a protectable block while holding the configured lock tool. */
public final class ProtectionLockGui {
    private ProtectionLockGui() {
    }

    public static void open(OathboundPlugin plugin, Player actor, ProtectedLocation location, ProtectionType type) {
        PlayerRef actorRef = new PlayerRef(actor.getUniqueId());
        List<ProtectionGroup> eligible =
                ProtectionAccessService.bindableGroups(actorRef, type, plugin.groupCache().values());

        Optional<Protection> existing = plugin.protectionCache().values().stream()
                .filter(protection -> protection.location().equals(location))
                .findFirst();
        boolean unlockAvailable = existing.isPresent() && ProtectionAccessService.canAccess(
                existing.get(), actorRef, id -> Optional.ofNullable(plugin.groupCache().get(id)));

        if (eligible.isEmpty() && !unlockAvailable) {
            actor.sendMessage("You don't have permission to lock anything here.");
            return;
        }

        Map<Integer, UUID> slotToGroupId = new HashMap<>();
        Map<Integer, ItemStack> slotToIcon = new HashMap<>();
        int slot = 0;
        for (ProtectionGroup group : eligible) {
            if (slot >= ProtectionLockHolder.LIST_SLOTS_END_EXCLUSIVE) {
                break;
            }
            slotToIcon.put(slot, buildIcon(group));
            slotToGroupId.put(slot, group.id());
            slot++;
        }

        ProtectionLockHolder holder = new ProtectionLockHolder(location, type, unlockAvailable, slotToGroupId);
        String title = type == ProtectionType.CONTAINER ? "Lock This Container" : "Lock This Door";
        Inventory inventory = Bukkit.createInventory(holder, ProtectionLockHolder.SIZE, Component.text(title));
        holder.setInventory(inventory);
        slotToIcon.forEach(inventory::setItem);

        for (int filler = ProtectionLockHolder.LIST_SLOTS_END_EXCLUSIVE; filler < ProtectionLockHolder.SIZE; filler++) {
            inventory.setItem(filler, GuiItems.filler());
        }
        if (unlockAvailable) {
            inventory.setItem(ProtectionLockHolder.UNLOCK_SLOT, GuiItems.button(Material.LEVER, "Unlock",
                    "Removes this block's", "current lock."));
        }
        inventory.setItem(ProtectionLockHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel",
                "Leaves this block as-is."));

        actor.openInventory(inventory);
    }

    private static ItemStack buildIcon(ProtectionGroup group) {
        return GuiItems.button(Material.WHITE_BANNER, group.name(),
                "Tier: " + group.tier(), "Click to lock to this group");
    }
}
