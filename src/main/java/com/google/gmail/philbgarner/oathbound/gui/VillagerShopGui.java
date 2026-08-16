package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.villager.NpcRole;
import com.google.gmail.philbgarner.oathbound.villager.VillagerNpc;
import com.google.gmail.philbgarner.oathbound.villager.VillagerRoleTrades;
import com.google.gmail.philbgarner.oathbound.villager.VillagerTradeOffer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chest GUI for a villager shop NPC: top rows list what it sells (player buys), bottom rows list what
 * it buys (player sells). Fixed per-role stock from config.yml - see {@link VillagerShopGuiListener} for
 * the click-to-transact behavior. */
public final class VillagerShopGui {
    private static final int SELL_ROW_START = 0;
    private static final int BUY_ROW_START = 18;
    private static final int ROW_WIDTH = 9;

    private VillagerShopGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer, VillagerNpc npc) {
        NpcRole role = npc.role();
        VillagerRoleTrades trades = plugin.oathboundConfig().villagerTrades().get(role);
        Currency currency = plugin.economyService().defaultCurrency();

        Map<Integer, VillagerTradeOffer> sellSlots = new HashMap<>();
        Map<Integer, VillagerTradeOffer> buySlots = new HashMap<>();
        Map<Integer, ItemStack> sellIcons = buildIcons(sellSlots, SELL_ROW_START, trades.sells(), currency,
                "Price", "Click to buy 1x");
        Map<Integer, ItemStack> buyIcons = buildIcons(buySlots, BUY_ROW_START, trades.buys(), currency,
                "Sell price", "Click to sell 1x");

        VillagerShopHolder holder = new VillagerShopHolder(sellSlots, buySlots);
        Inventory inventory = Bukkit.createInventory(holder, VillagerShopHolder.SIZE,
                Component.text(role.displayName() + " - " + npc.name()));
        holder.setInventory(inventory);

        for (int slot = 0; slot < VillagerShopHolder.SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler());
        }
        inventory.setItem(VillagerShopHolder.LABEL_SLOT,
                GuiItems.button(Material.PAPER, "Sells above / Buys below"));
        inventory.setItem(VillagerShopHolder.CLOSE_SLOT, GuiItems.button(Material.BARRIER, "Close"));
        sellIcons.forEach(inventory::setItem);
        buyIcons.forEach(inventory::setItem);

        viewer.openInventory(inventory);
    }

    private static Map<Integer, ItemStack> buildIcons(Map<Integer, VillagerTradeOffer> slotIndex, int rowStart,
                                                        List<VillagerTradeOffer> offers, Currency currency,
                                                        String priceLabel, String actionLabel) {
        Map<Integer, ItemStack> icons = new HashMap<>();
        int limit = Math.min(offers.size(), ROW_WIDTH * 2);
        for (int i = 0; i < limit; i++) {
            VillagerTradeOffer offer = offers.get(i);
            int slot = rowStart + i;
            ItemStack icon = new ItemStack(offer.material());
            GuiItems.setDisplayName(icon, displayName(offer.material()));
            GuiItems.setLore(icon, List.of(
                    priceLabel + ": " + offer.price() + " " + currency.id(),
                    actionLabel));
            icons.put(slot, icon);
            slotIndex.put(slot, offer);
        }
        return icons;
    }

    private static String displayName(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
