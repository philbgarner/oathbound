package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Chest GUI for posting a new open trade contract: deposit items, then click Post. */
public final class TradeBuilderGui {
    private TradeBuilderGui() {
    }

    public static void open(Player player) {
        TradeBuilderHolder holder = new TradeBuilderHolder(new PlayerRef(player.getUniqueId()));
        Inventory inventory = Bukkit.createInventory(holder, TradeBuilderHolder.SIZE,
                Component.text("Post an Open Contract"));
        holder.setInventory(inventory);

        for (int slot = TradeBuilderHolder.DEPOSIT_SLOTS_END_EXCLUSIVE; slot < TradeBuilderHolder.SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler());
        }
        inventory.setItem(TradeBuilderHolder.POST_SLOT, GuiItems.button(Material.EMERALD_BLOCK, "Post Open Contract",
                "Deposit items in the top two rows,", "then click here.", "",
                "Anyone can accept it - whoever", "does becomes the other party."));
        inventory.setItem(TradeBuilderHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel",
                "Closes this menu and returns", "anything you've deposited."));

        player.openInventory(inventory);
    }
}
