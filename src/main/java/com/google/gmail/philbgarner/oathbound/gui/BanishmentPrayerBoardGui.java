package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.Banishment;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chest GUI listing every player currently serving a banishment sentence - "the Altar of Intercession."
 * Reached by shift-right-clicking any consecrated, non-Critical altar barrel (see
 * {@link com.google.gmail.philbgarner.oathbound.listener.AltarInteractListener}) instead of the normal
 * sacrifice screen. Click a target to open {@link PrayerAltarGui} and spend enchanted items shortening
 * their sentence. */
public final class BanishmentPrayerBoardGui {
    private BanishmentPrayerBoardGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer) {
        Instant now = Instant.now();
        List<Banishment> active = plugin.banishmentCache().values().stream()
                .filter(banishment -> banishment.active(now))
                .sorted(Comparator.comparing(Banishment::releaseAt))
                .toList();

        Map<Integer, PlayerRef> slotToTarget = new HashMap<>();
        Map<Integer, ItemStack> slotToIcon = new HashMap<>();

        int slot = 0;
        for (Banishment banishment : active) {
            if (slot >= BanishmentPrayerBoardHolder.LIST_SLOTS_END_EXCLUSIVE) {
                break;
            }
            slotToIcon.put(slot, buildIcon(banishment, now));
            slotToTarget.put(slot, banishment.player());
            slot++;
        }

        BanishmentPrayerBoardHolder holder = new BanishmentPrayerBoardHolder(slotToTarget);
        Inventory inventory = Bukkit.createInventory(holder, BanishmentPrayerBoardHolder.SIZE,
                Component.text("Altar of Intercession"));
        holder.setInventory(inventory);

        for (int i = 0; i < BanishmentPrayerBoardHolder.SIZE; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        slotToIcon.forEach(inventory::setItem);
        if (active.isEmpty()) {
            inventory.setItem(22, GuiItems.button(Material.PAPER, "No one is currently banished"));
        }
        inventory.setItem(BanishmentPrayerBoardHolder.CLOSE_SLOT, GuiItems.button(Material.BARRIER, "Close"));

        viewer.openInventory(inventory);
    }

    private static ItemStack buildIcon(Banishment banishment, Instant now) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(banishment.player().playerId());
        String targetName = target.getName() != null ? target.getName() : banishment.player().playerId().toString();

        List<String> lore = new ArrayList<>();
        lore.add("Remaining: " + formatRemaining(Duration.between(now, banishment.releaseAt())));
        lore.add("");
        lore.add("Click to pray for their release -");
        lore.add("sacrifice enchanted items to cut");
        lore.add("time from their sentence.");

        ItemStack icon = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) icon.getItemMeta();
        meta.setOwningPlayer(target);
        icon.setItemMeta(meta);
        GuiItems.setDisplayName(icon, targetName);
        GuiItems.setLore(icon, lore);
        return icon;
    }

    private static String formatRemaining(Duration remaining) {
        long totalMinutes = Math.max(0, remaining.toMinutes());
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + "h " + minutes + "m";
    }
}
