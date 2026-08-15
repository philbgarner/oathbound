package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bukkit.ItemStackSerialization;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathState;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chest GUI listing every open, unaccepted contract - one icon per contract, click to view/fulfill. */
public final class TradeBoardGui {
    private TradeBoardGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer) {
        List<Oath> openOffers = plugin.oathCache().values().stream()
                .filter(oath -> oath.open() && oath.state() == OathState.PROPOSED && oath.parties().size() == 1)
                .sorted(Comparator.comparing(Oath::createdAt))
                .toList();

        int rows = Math.max(1, Math.min(6, (openOffers.size() + 8) / 9));
        int size = rows * 9;

        Map<Integer, UUID> slotToOathId = new HashMap<>();
        Map<Integer, ItemStack> slotToIcon = new HashMap<>();

        int slot = 0;
        for (Oath oath : openOffers) {
            if (slot >= size) {
                break;
            }
            TradeOffer offer = plugin.tradeOfferCache().get(oath.id());
            if (offer == null) {
                continue;
            }
            slotToIcon.put(slot, buildIcon(offer));
            slotToOathId.put(slot, oath.id());
            slot++;
        }

        TradeBoardHolder holder = new TradeBoardHolder(slotToOathId);
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text("Open Contracts"));
        holder.setInventory(inventory);
        slotToIcon.forEach(inventory::setItem);

        viewer.openInventory(inventory);
    }

    private static ItemStack buildIcon(TradeOffer offer) {
        List<ItemStack> items = new ArrayList<>();
        for (SerializedItemStack serialized : offer.creatorItems()) {
            items.add(ItemStackSerialization.deserialize(serialized));
        }

        ItemStack icon = items.isEmpty() ? new ItemStack(Material.CHEST) : items.get(0).clone();
        icon.setAmount(1);

        OfflinePlayer creator = Bukkit.getOfflinePlayer(offer.creator().playerId());
        String creatorName = creator.getName() != null ? creator.getName() : offer.creator().playerId().toString();

        List<String> lore = new ArrayList<>();
        lore.add("Posted by " + creatorName);
        lore.add("Offering:");
        for (ItemStack item : items) {
            lore.add("  " + item.getAmount() + "x " + item.getType());
        }
        lore.add("");
        lore.add("Click to view / fulfill");

        GuiItems.setDisplayName(icon, "Open Contract");
        GuiItems.setLore(icon, lore);
        return icon;
    }
}
