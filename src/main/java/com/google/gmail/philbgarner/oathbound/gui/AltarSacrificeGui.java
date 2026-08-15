package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/** Chest GUI for sacrificing enchanted items into an altar's Power. Only enchanted items count -
 * item type is irrelevant, value is derived entirely from enchantment profile. Deposits are consumed
 * permanently on confirm, never returned. */
public final class AltarSacrificeGui {
    private AltarSacrificeGui() {
    }

    public static void open(OathboundPlugin plugin, Player player, UUID altarId) {
        PlayerRef depositor = new PlayerRef(player.getUniqueId());
        AltarSacrificeHolder holder = new AltarSacrificeHolder(altarId, depositor);
        Inventory inventory = Bukkit.createInventory(holder, AltarSacrificeHolder.SIZE, Component.text("Sacrifice Ritual"));
        holder.setInventory(inventory);

        for (int slot = AltarSacrificeHolder.DEPOSIT_SLOTS_END_EXCLUSIVE; slot < AltarSacrificeHolder.SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler());
        }

        inventory.setItem(AltarSacrificeHolder.CONFIRM_SLOT, GuiItems.button(Material.NETHER_STAR, "Confirm Sacrifice",
                "Deposit enchanted items in the rows above,", "then click here.", "",
                "Only enchanted items count as artifacts -", "value comes entirely from their",
                "enchantment profile, not item type.", "", "Deposited items are consumed", "permanently - this cannot be undone."));
        inventory.setItem(AltarSacrificeHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel",
                "Closes this menu and returns", "anything you've deposited."));

        player.openInventory(inventory);
    }
}
