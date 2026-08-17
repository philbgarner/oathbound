package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.Bounty;
import com.google.gmail.philbgarner.oathbound.bounty.BountyStatus;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTarget;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chest GUI listing every active bounty - one icon per contract, click your own to cancel it (a refund
 * of the unpaid remainder, not the fee). Placing a new one starts the chat-prompt flow via
 * {@link BountyPlacementListener}. */
public final class BountyBoardGui {
    private BountyBoardGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer) {
        List<Bounty> active = plugin.bountyCache().values().stream()
                .filter(bounty -> bounty.status() == BountyStatus.ACTIVE)
                .sorted(Comparator.comparing(Bounty::placedAt))
                .toList();

        Map<Integer, UUID> slotToBountyId = new HashMap<>();
        Map<Integer, ItemStack> slotToIcon = new HashMap<>();

        int slot = 0;
        for (Bounty bounty : active) {
            if (slot >= BountyBoardHolder.LIST_SLOTS_END_EXCLUSIVE) {
                break;
            }
            slotToIcon.put(slot, buildIcon(plugin, bounty, new PlayerRef(viewer.getUniqueId())));
            slotToBountyId.put(slot, bounty.id());
            slot++;
        }

        BountyBoardHolder holder = new BountyBoardHolder(slotToBountyId);
        Inventory inventory = Bukkit.createInventory(holder, BountyBoardHolder.SIZE, Component.text("Bounty Board"));
        holder.setInventory(inventory);

        for (int i = 0; i < BountyBoardHolder.SIZE; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        slotToIcon.forEach(inventory::setItem);
        inventory.setItem(BountyBoardHolder.PLACE_NEW_SLOT, GuiItems.button(Material.EMERALD, "Place New Bounty",
                "Type a target's name in chat", "and set a reward."));
        inventory.setItem(BountyBoardHolder.STANDING_CONTRACTS_SLOT, GuiItems.button(Material.TARGET,
                "Standing Contracts", "Browse admin-authored PvE", "kill contracts for currency."));
        inventory.setItem(BountyBoardHolder.BACK_SLOT, GuiItems.button(Material.ARROW, "Back"));

        viewer.openInventory(inventory);
    }

    private static ItemStack buildIcon(OathboundPlugin plugin, Bounty bounty, PlayerRef viewer) {
        OfflinePlayer placer = Bukkit.getOfflinePlayer(bounty.placer().playerId());
        String placerName = placer.getName() != null ? placer.getName() : bounty.placer().playerId().toString();

        List<String> lore = new ArrayList<>();
        lore.add("Target: " + targetName(plugin, bounty.target()));
        lore.add("Placed by " + placerName);
        lore.add(bounty.remainingQuantity() + "/" + bounty.originalQuantity() + " remaining");
        lore.add("Reward per head: " + bounty.totalReward());
        boolean own = bounty.placer().equals(viewer);
        lore.add("");
        lore.add(own ? "Click to cancel (refunds the unpaid remainder)" : "Return a head to any Notary to claim it");

        ItemStack icon = new ItemStack(Material.PLAYER_HEAD);
        if (bounty.target() instanceof BountyTarget.Solo solo) {
            SkullMeta meta = (SkullMeta) icon.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(solo.player().playerId()));
            icon.setItemMeta(meta);
        }
        GuiItems.setDisplayName(icon, "Bounty on " + targetName(plugin, bounty.target()));
        GuiItems.setLore(icon, lore);
        return icon;
    }

    static String targetName(OathboundPlugin plugin, BountyTarget target) {
        if (target instanceof BountyTarget.Solo solo) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(solo.player().playerId());
            return player.getName() != null ? player.getName() : solo.player().playerId().toString();
        }
        if (target instanceof BountyTarget.Group group) {
            ProtectionGroup protectionGroup = plugin.groupCache().get(group.group().groupId());
            return protectionGroup != null ? protectionGroup.name() : group.group().groupId().toString();
        }
        return "unknown";
    }
}
