package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.GroupPermission;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chest GUI listing eligible groups - either "which of my groups can I transfer" (subject) or
 * "which group becomes the new owner" (target), the latter also offering a "pick a player instead" button. */
public final class GroupPickerGui {
    private GroupPickerGui() {
    }

    public static void openForSubject(OathboundPlugin plugin, Player actor, UUID oathId) {
        PlayerRef actorRef = new PlayerRef(actor.getUniqueId());
        List<ProtectionGroup> eligible = plugin.groupCache().values().stream()
                .filter(group -> group.hasPermission(actorRef, GroupPermission.TRANSFER_OWNERSHIP))
                .sorted(Comparator.comparing(ProtectionGroup::name))
                .toList();
        if (eligible.isEmpty()) {
            actor.sendMessage("You have no groups you can transfer ownership of.");
            OathBuilderGui.open(plugin, actor, oathId);
            return;
        }
        open(actor, oathId, GroupPickerHolder.Purpose.SUBJECT_GROUP, null, eligible);
    }

    public static void openForOwnerTarget(OathboundPlugin plugin, Player actor, UUID oathId, UUID subjectGroupId) {
        List<ProtectionGroup> eligible = plugin.groupCache().values().stream()
                .filter(group -> !group.id().equals(subjectGroupId))
                .sorted(Comparator.comparing(ProtectionGroup::name))
                .toList();
        open(actor, oathId, GroupPickerHolder.Purpose.OWNER_TARGET_GROUP, subjectGroupId, eligible);
    }

    private static void open(Player actor, UUID oathId, GroupPickerHolder.Purpose purpose, UUID subjectGroupId,
                              List<ProtectionGroup> groups) {
        Map<Integer, UUID> slotToGroupId = new HashMap<>();
        Map<Integer, ItemStack> slotToIcon = new HashMap<>();
        int slot = 0;
        for (ProtectionGroup group : groups) {
            if (slot >= GroupPickerHolder.LIST_SLOTS_END_EXCLUSIVE) {
                break;
            }
            slotToIcon.put(slot, buildIcon(group));
            slotToGroupId.put(slot, group.id());
            slot++;
        }

        GroupPickerHolder holder = new GroupPickerHolder(oathId, purpose, subjectGroupId, slotToGroupId);
        String title = purpose == GroupPickerHolder.Purpose.SUBJECT_GROUP ? "Transfer Which Group?" : "New Owner Group";
        Inventory inventory = Bukkit.createInventory(holder, GroupPickerHolder.SIZE, Component.text(title));
        holder.setInventory(inventory);
        slotToIcon.forEach(inventory::setItem);

        for (int filler = GroupPickerHolder.LIST_SLOTS_END_EXCLUSIVE; filler < GroupPickerHolder.SIZE; filler++) {
            inventory.setItem(filler, GuiItems.filler());
        }
        if (purpose == GroupPickerHolder.Purpose.OWNER_TARGET_GROUP) {
            inventory.setItem(GroupPickerHolder.PLAYER_INSTEAD_SLOT, GuiItems.button(Material.PLAYER_HEAD,
                    "Set Owner To A Player", "Click, then type a player's", "name in chat."));
        }
        inventory.setItem(GroupPickerHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Back",
                "Returns to the oath builder", "without adding a clause."));

        actor.openInventory(inventory);
    }

    private static ItemStack buildIcon(ProtectionGroup group) {
        return GuiItems.button(Material.WHITE_BANNER, group.name(),
                "Tier: " + group.tier(), "Click to select");
    }
}
