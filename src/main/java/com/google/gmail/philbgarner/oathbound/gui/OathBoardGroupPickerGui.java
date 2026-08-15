package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.board.BoardLocation;
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

/** Chest GUI listing groups the actor is currently a member of, to bind a board's regional scope to. */
public final class OathBoardGroupPickerGui {
    private OathBoardGroupPickerGui() {
    }

    public static void open(OathboundPlugin plugin, Player actor, BoardLocation location) {
        PlayerRef actorRef = new PlayerRef(actor.getUniqueId());
        List<ProtectionGroup> eligible = plugin.groupCache().values().stream()
                .filter(group -> group.findMember(actorRef).isPresent())
                .sorted(Comparator.comparing(ProtectionGroup::name))
                .toList();
        if (eligible.isEmpty()) {
            actor.sendMessage("You're not a member of any group to bind this board to.");
            OathBoardHubGui.open(plugin, actor, location);
            return;
        }

        Map<Integer, UUID> slotToGroupId = new HashMap<>();
        Map<Integer, ItemStack> slotToIcon = new HashMap<>();
        int slot = 0;
        for (ProtectionGroup group : eligible) {
            if (slot >= OathBoardGroupPickerHolder.LIST_SLOTS_END_EXCLUSIVE) {
                break;
            }
            slotToIcon.put(slot, GuiItems.button(Material.WHITE_BANNER, group.name(),
                    "Tier: " + group.tier(), "Click to bind the board here"));
            slotToGroupId.put(slot, group.id());
            slot++;
        }

        OathBoardGroupPickerHolder holder = new OathBoardGroupPickerHolder(location, slotToGroupId);
        Inventory inventory = Bukkit.createInventory(holder, OathBoardGroupPickerHolder.SIZE,
                Component.text("Bind Board To Which Group?"));
        holder.setInventory(inventory);
        slotToIcon.forEach(inventory::setItem);

        for (int filler = OathBoardGroupPickerHolder.LIST_SLOTS_END_EXCLUSIVE;
                filler < OathBoardGroupPickerHolder.SIZE; filler++) {
            inventory.setItem(filler, GuiItems.filler());
        }
        inventory.setItem(OathBoardGroupPickerHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Back"));

        actor.openInventory(inventory);
    }
}
