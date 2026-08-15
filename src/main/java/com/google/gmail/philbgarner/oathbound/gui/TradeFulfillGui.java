package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bukkit.ItemStackSerialization;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Chest GUI for fulfilling someone else's open contract: view their offer, deposit your own items, confirm the swap. */
public final class TradeFulfillGui {
    private TradeFulfillGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer, UUID oathId) {
        TradeOffer offer = plugin.tradeOfferCache().get(oathId);
        if (offer == null) {
            viewer.sendMessage("That contract is no longer available.");
            return;
        }

        TradeFulfillHolder holder = new TradeFulfillHolder(oathId);
        Inventory inventory = Bukkit.createInventory(holder, TradeFulfillHolder.SIZE, Component.text("Fulfill Contract"));
        holder.setInventory(inventory);

        int slot = 0;
        for (var serialized : offer.creatorItems()) {
            if (slot >= TradeFulfillHolder.DISPLAY_SLOTS_END_EXCLUSIVE) {
                break;
            }
            ItemStack stack = ItemStackSerialization.deserialize(serialized);
            inventory.setItem(slot, stack);
            slot++;
        }

        for (int filler = TradeFulfillHolder.DEPOSIT_SLOTS_END_EXCLUSIVE; filler < TradeFulfillHolder.SIZE; filler++) {
            inventory.setItem(filler, GuiItems.filler());
        }
        inventory.setItem(TradeFulfillHolder.CONFIRM_SLOT, GuiItems.button(Material.EMERALD_BLOCK, "Confirm Trade",
                "Deposit your items in the rows", "above, then click here.", "",
                "You receive their items;", "they receive yours."));
        inventory.setItem(TradeFulfillHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel",
                "Closes this menu and returns", "anything you've deposited."));

        viewer.openInventory(inventory);
    }
}
