package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.board.BoardLocation;
import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Optional;

/** Hub screen for an Oath Board block - always opens on right-click, whether or not this location has
 * been bound to a scope yet. */
public final class OathBoardHubGui {
    private OathBoardHubGui() {
    }

    public static void open(OathboundPlugin plugin, Player player, BoardLocation location) {
        Optional<OathBoard> board = plugin.oathBoardCache().values().stream()
                .filter(b -> b.location().equals(location))
                .findFirst();

        OathBoardHubHolder holder = new OathBoardHubHolder(location);
        Inventory inventory = Bukkit.createInventory(holder, OathBoardHubHolder.SIZE, Component.text("Oath Board"));
        holder.setInventory(inventory);

        for (int slot = 0; slot < OathBoardHubHolder.SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler());
        }
        inventory.setItem(OathBoardHubHolder.BROWSE_SLOT, GuiItems.button(Material.BOOK, "Browse Postings",
                "Scope: " + scopeLabel(plugin, board.orElse(null))));
        inventory.setItem(OathBoardHubHolder.BIND_GROUP_SLOT, GuiItems.button(Material.WHITE_BANNER, "Bind To A Group",
                "Shows only postings where a", "party is a member of that group."));
        inventory.setItem(OathBoardHubHolder.MAKE_CAPITAL_SLOT, GuiItems.button(Material.NETHER_STAR, "Make Capital",
                "Shows every witnessed", "posting, unfiltered."));
        inventory.setItem(OathBoardHubHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel"));

        player.openInventory(inventory);
    }

    private static String scopeLabel(OathboundPlugin plugin, OathBoard board) {
        if (board == null || board.isCapital()) {
            return "Capital";
        }
        ProtectionGroup group = plugin.groupCache().get(board.regionalGroup().groupId());
        return group != null ? group.name() : "an unknown group";
    }
}
