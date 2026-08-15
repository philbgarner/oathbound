package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathState;
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

/** Chest GUI listing named-party oaths proposed to the viewer - one icon per oath, click to review/sign it. */
public final class PendingOathBoardGui {
    private PendingOathBoardGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer) {
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId());
        List<Oath> pending = plugin.oathCache().values().stream()
                .filter(oath -> !oath.open() && oath.state() == OathState.PROPOSED
                        && oath.parties().size() >= 2
                        && oath.parties().contains(viewerRef)
                        && !oath.parties().get(0).equals(viewerRef))
                .sorted(Comparator.comparing(Oath::createdAt))
                .toList();

        int rows = Math.max(1, Math.min(6, (pending.size() + 8) / 9));
        int size = rows * 9;

        Map<Integer, UUID> slotToOathId = new HashMap<>();
        Map<Integer, ItemStack> slotToIcon = new HashMap<>();

        int slot = 0;
        for (Oath oath : pending) {
            if (slot >= size) {
                break;
            }
            slotToIcon.put(slot, buildIcon(oath));
            slotToOathId.put(slot, oath.id());
            slot++;
        }

        PendingOathBoardHolder holder = new PendingOathBoardHolder(slotToOathId);
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text("Oaths Proposed To You"));
        holder.setInventory(inventory);
        slotToIcon.forEach(inventory::setItem);

        viewer.openInventory(inventory);
    }

    private static ItemStack buildIcon(Oath oath) {
        OfflinePlayer creator = Bukkit.getOfflinePlayer(oath.parties().get(0).playerId());
        String creatorName = creator.getName() != null ? creator.getName() : oath.parties().get(0).playerId().toString();

        List<String> lore = new ArrayList<>();
        lore.add("Proposed by " + creatorName);
        lore.add("Clauses: " + oath.clauses().size());
        if (oath.bloodOath()) {
            lore.add("Blood Oath");
        }
        lore.add("");
        lore.add("Click to review");

        return GuiItems.button(Material.WRITABLE_BOOK, "Pending Oath", lore.toArray(String[]::new));
    }
}
