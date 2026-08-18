package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Chest GUI for sacrificing enchanted items into the banishment prayer/intercession ritual on behalf of
 * {@code target}. Same "enchanted items only, consumed on confirm" rules as {@link AltarSacrificeGui} -
 * see {@link SacrificeInputSupport} for the shared valuation logic. */
public final class PrayerAltarGui {
    private PrayerAltarGui() {
    }

    public static void open(OathboundPlugin plugin, Player player, PlayerRef target) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(target.playerId());
        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : target.playerId().toString();

        PlayerRef supplicant = new PlayerRef(player.getUniqueId());
        PrayerAltarHolder holder = new PrayerAltarHolder(target, supplicant);
        Inventory inventory = Bukkit.createInventory(holder, PrayerAltarHolder.SIZE,
                Component.text("Intercession for " + targetName));
        holder.setInventory(inventory);

        for (int slot = PrayerAltarHolder.DEPOSIT_SLOTS_END_EXCLUSIVE; slot < PrayerAltarHolder.SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler());
        }

        inventory.setItem(PrayerAltarHolder.CONFIRM_SLOT, GuiItems.button(Material.NETHER_STAR, "Confirm Prayer",
                "Deposit enchanted items in the rows above,", "then click here.", "",
                "Only enchanted items count as artifacts -", "value comes entirely from their",
                "enchantment profile, not item type.", "",
                "Cuts time from " + targetName + "'s", "banishment sentence - a large enough", "offering forgives it outright.", "",
                "Deposited items are consumed", "permanently - this cannot be undone."));
        inventory.setItem(PrayerAltarHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel",
                "Closes this menu and returns", "anything you've deposited."));

        player.openInventory(inventory);
    }
}
