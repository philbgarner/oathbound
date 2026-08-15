package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;

/** Chest GUI for reviewing one pending named-party oath and signing or declining it. */
public final class PendingOathDetailGui {
    private PendingOathDetailGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer, UUID oathId) {
        Oath oath = plugin.oathCache().get(oathId);
        if (oath == null) {
            viewer.sendMessage("That oath is no longer available.");
            return;
        }

        OfflinePlayer creator = Bukkit.getOfflinePlayer(oath.parties().get(0).playerId());
        String creatorName = creator.getName() != null ? creator.getName() : oath.parties().get(0).playerId().toString();

        PendingOathDetailHolder holder = new PendingOathDetailHolder(oathId);
        Inventory inventory = Bukkit.createInventory(holder, PendingOathDetailHolder.SIZE,
                Component.text("Oath From " + creatorName));
        holder.setInventory(inventory);

        List<Clause> clauses = oath.clauses();
        for (int slot = 0; slot < PendingOathDetailHolder.CLAUSE_LIST_END_EXCLUSIVE && slot < clauses.size(); slot++) {
            inventory.setItem(slot, ClauseIcons.icon(clauses.get(slot), plugin.groupCache()));
        }
        for (int filler = clauses.size(); filler < PendingOathDetailHolder.SIZE; filler++) {
            inventory.setItem(filler, GuiItems.filler());
        }

        inventory.setItem(PendingOathDetailHolder.SIGN_SLOT, GuiItems.button(Material.EMERALD_BLOCK, "Sign Oath",
                "Accepts every clause above", "and activates the oath."));
        inventory.setItem(PendingOathDetailHolder.DECLINE_SLOT, GuiItems.button(Material.BARRIER, "Decline Oath",
                "Voids the oath permanently."));

        viewer.openInventory(inventory);
    }
}
