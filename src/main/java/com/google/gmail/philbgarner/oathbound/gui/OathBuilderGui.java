package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
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

/** Chest GUI for building a named-party oath's clauses one at a time before proposing it. */
public final class OathBuilderGui {
    private OathBuilderGui() {
    }

    public static void open(OathboundPlugin plugin, Player player, UUID oathId) {
        Oath oath = plugin.oathCache().get(oathId);
        if (oath == null) {
            player.sendMessage("That oath draft no longer exists.");
            return;
        }

        PlayerRef creator = oath.parties().get(0);
        OathBuilderHolder holder = new OathBuilderHolder(oathId, creator);
        String otherName = otherPartyName(oath);
        Inventory inventory = Bukkit.createInventory(holder, OathBuilderHolder.SIZE,
                Component.text("Draft Oath with " + otherName));
        holder.setInventory(inventory);

        List<Clause> clauses = oath.clauses();
        for (int slot = 0; slot < OathBuilderHolder.CLAUSE_LIST_END_EXCLUSIVE && slot < clauses.size(); slot++) {
            inventory.setItem(slot, ClauseIcons.icon(clauses.get(slot), plugin.groupCache()));
        }

        for (int filler = clauses.size(); filler < OathBuilderHolder.CLAUSE_LIST_END_EXCLUSIVE; filler++) {
            inventory.setItem(filler, GuiItems.filler());
        }
        for (int filler = OathBuilderHolder.CLAUSE_LIST_END_EXCLUSIVE; filler < OathBuilderHolder.SIZE; filler++) {
            inventory.setItem(filler, GuiItems.filler());
        }

        inventory.setItem(OathBuilderHolder.ADD_TRANSFER_SLOT, GuiItems.button(Material.NAME_TAG, "Add Transfer Clause",
                "Reassigns one of your groups'", "ownership once the oath fulfills."));
        inventory.setItem(OathBuilderHolder.ADD_FLAG_SLOT, GuiItems.button(Material.PAPER, "Add Custom Flag",
                "A free-text roleplay clause", "with no mechanical effect."));
        inventory.setItem(OathBuilderHolder.ADD_KILLCOUNT_SLOT, GuiItems.button(Material.IRON_SWORD, "Add Kill Count",
                "Requires a target player to be", "killed a number of times."));
        inventory.setItem(OathBuilderHolder.PROPOSE_SLOT, GuiItems.button(Material.EMERALD_BLOCK, "Propose Oath",
                "Sends this draft to " + otherName + ".", "Requires at least one clause."));
        inventory.setItem(OathBuilderHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel Draft",
                "Voids this draft permanently."));

        player.openInventory(inventory);
    }

    private static String otherPartyName(Oath oath) {
        List<PlayerRef> parties = oath.parties();
        if (parties.size() < 2) {
            return "no one yet";
        }
        OfflinePlayer other = Bukkit.getOfflinePlayer(parties.get(1).playerId());
        return other.getName() != null ? other.getName() : parties.get(1).playerId().toString();
    }
}
