package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bukkit.ItemStackSerialization;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.EscrowClaim;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chest GUI listing every unclaimed escrow item batch the viewer currently holds - normally as the
 * clause's recipient, or as the original depositor if it expired unclaimed back to them. */
public final class EscrowClaimBoardGui {
    private EscrowClaimBoardGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer) {
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId());
        List<EscrowClaim> claimable = plugin.escrowClaimCache().values().stream()
                .filter(claim -> !claim.claimed() && claim.holder().equals(viewerRef))
                .sorted(Comparator.comparing(EscrowClaim::releasedAt))
                .toList();

        int rows = Math.max(1, Math.min(6, (claimable.size() + 8) / 9));
        int size = rows * 9;

        Map<Integer, UUID> slotToClaimId = new HashMap<>();
        Map<Integer, ItemStack> slotToIcon = new HashMap<>();

        int slot = 0;
        for (EscrowClaim claim : claimable) {
            if (slot >= size) {
                break;
            }
            slotToIcon.put(slot, buildIcon(claim));
            slotToClaimId.put(slot, claim.id());
            slot++;
        }

        EscrowClaimBoardHolder holder = new EscrowClaimBoardHolder(slotToClaimId);
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text("Unclaimed Escrow"));
        holder.setInventory(inventory);
        slotToIcon.forEach(inventory::setItem);

        viewer.openInventory(inventory);
    }

    private static ItemStack buildIcon(EscrowClaim claim) {
        List<ItemStack> items = new ArrayList<>();
        for (SerializedItemStack serialized : claim.items()) {
            items.add(ItemStackSerialization.deserialize(serialized));
        }

        ItemStack icon = items.isEmpty() ? new ItemStack(Material.CHEST) : items.get(0).clone();
        icon.setAmount(1);

        List<String> lore = new ArrayList<>();
        if (claim.expired()) {
            lore.add("Returned to you - unclaimed past its expiry.");
        } else {
            lore.add("Escrow release from your oath.");
        }
        lore.add("Contains:");
        for (ItemStack item : items) {
            lore.add("  " + item.getAmount() + "x " + item.getType());
        }
        lore.add("");
        lore.add("Click to claim");

        GuiItems.setDisplayName(icon, "Unclaimed Escrow");
        GuiItems.setLore(icon, lore);
        return icon;
    }
}
