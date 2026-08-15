package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.board.BoardLocation;
import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathBoardEligibility;
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
import java.util.List;
import java.util.Optional;

/** Read-only feed of recent witnessed oath activity - live-queries the Ledger each time it's opened,
 * exactly like the other board GUIs in this plugin, rather than persisting its own copy. */
public final class OathBoardFeedGui {
    private OathBoardFeedGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer, BoardLocation location) {
        Optional<OathBoard> board = plugin.oathBoardCache().values().stream()
                .filter(b -> b.location().equals(location))
                .findFirst();
        ProtectionGroup regionalGroup = board.filter(b -> !b.isCapital())
                .map(b -> plugin.groupCache().get(b.regionalGroup().groupId()))
                .orElse(null);

        List<LedgerEntry> postings = plugin.ledger().recent(plugin.oathboundConfig().oathBoardFeedSize()).stream()
                .filter(entry -> isPosted(plugin, entry, regionalGroup))
                .sorted(Comparator.comparing(LedgerEntry::timestamp).reversed())
                .toList();

        int rows = Math.max(1, Math.min(6, (postings.size() + 8) / 9));
        int size = rows * 9;

        OathBoardFeedHolder holder = new OathBoardFeedHolder();
        String title = regionalGroup != null ? regionalGroup.name() + " Oath Board" : "Capital Oath Board";
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(title));
        holder.setInventory(inventory);

        int slot = 0;
        for (LedgerEntry entry : postings) {
            if (slot >= size) {
                break;
            }
            inventory.setItem(slot, buildIcon(plugin, entry));
            slot++;
        }
        for (; slot < size; slot++) {
            inventory.setItem(slot, GuiItems.filler());
        }

        viewer.openInventory(inventory);
    }

    private static boolean isPosted(OathboundPlugin plugin, LedgerEntry entry, ProtectionGroup regionalGroup) {
        Oath oath = plugin.oathCache().get(entry.oathId());
        if (oath == null || !OathBoardEligibility.isPostable(oath, entry.toState())) {
            return false;
        }
        return regionalGroup == null || OathBoardEligibility.isRegionallyRelevant(oath, regionalGroup);
    }

    private static ItemStack buildIcon(OathboundPlugin plugin, LedgerEntry entry) {
        Oath oath = plugin.oathCache().get(entry.oathId());
        boolean blood = oath.bloodOath();
        Material material;
        String name;
        if (entry.toState() == OathState.SEALED) {
            material = Material.PAPER;
            name = "Oath Sealed";
        } else if (entry.toState() == OathState.FULFILLED) {
            material = Material.EMERALD;
            name = "Oath Fulfilled";
        } else if (blood) {
            material = Material.NETHERRACK;
            name = "BLOOD OATH BROKEN";
        } else {
            material = Material.GUNPOWDER;
            name = "Oath Broken";
        }

        List<String> lore = new ArrayList<>();
        lore.add("Between " + partyNames(oath));
        if (blood) {
            lore.add("A Blood Oath.");
        }
        lore.add(entry.timestamp().toString());

        return GuiItems.button(material, name, lore.toArray(String[]::new));
    }

    private static String partyNames(Oath oath) {
        List<String> names = new ArrayList<>();
        for (PlayerRef party : oath.parties()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(party.playerId());
            names.add(player.getName() != null ? player.getName() : party.playerId().toString());
        }
        return String.join(" & ", names);
    }
}
