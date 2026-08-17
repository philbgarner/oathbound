package com.google.gmail.philbgarner.oathbound.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Small hub screen opened by right-clicking a Notary NPC: start a new named-party oath draft, or
 * review oaths proposed to you. The mailbox is scoped to the player, not the physical Notary - you can
 * review pending offers at any Notary you interact with, per the design doc. */
public final class NotaryMenuGui {
    private NotaryMenuGui() {
    }

    public static void open(Player player) {
        NotaryMenuHolder holder = new NotaryMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, NotaryMenuHolder.SIZE, Component.text("Notary"));
        holder.setInventory(inventory);

        for (int slot = 0; slot < NotaryMenuHolder.SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler());
        }
        inventory.setItem(NotaryMenuHolder.NEW_OATH_SLOT, GuiItems.button(Material.WRITABLE_BOOK, "New Oath",
                "Draft a named-party oath", "with someone you name in chat."));
        inventory.setItem(NotaryMenuHolder.BOUNTY_BOARD_SLOT, GuiItems.button(Material.PLAYER_HEAD, "Bounty Board",
                "Browse active bounties,", "place a new one, or view", "standing kill contracts."));
        inventory.setItem(NotaryMenuHolder.PENDING_OATHS_SLOT, GuiItems.button(Material.BOOK, "Pending Oaths",
                "Review oaths proposed to you,", "wherever they were sent from."));
        inventory.setItem(NotaryMenuHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel"));

        player.openInventory(inventory);
    }
}
