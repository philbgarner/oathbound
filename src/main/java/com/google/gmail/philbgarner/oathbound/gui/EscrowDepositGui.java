package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/** Chest GUI for depositing items into a new escrow clause. The currency amount (if any) is decided via
 * chat prompt before this opens - see {@link OathBuilderListener} - since it can't change once items are
 * being placed without risking losing them across a GUI close/reopen. */
public final class EscrowDepositGui {
    private EscrowDepositGui() {
    }

    public static void open(OathboundPlugin plugin, Player player, UUID oathId, Currency currency, long currencyAmount) {
        PlayerRef depositor = new PlayerRef(player.getUniqueId());
        EscrowDepositHolder holder = new EscrowDepositHolder(oathId, depositor, currency, currencyAmount);
        Inventory inventory = Bukkit.createInventory(holder, EscrowDepositHolder.SIZE, Component.text("Deposit Into Escrow"));
        holder.setInventory(inventory);

        for (int slot = EscrowDepositHolder.DEPOSIT_SLOTS_END_EXCLUSIVE; slot < EscrowDepositHolder.SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler());
        }

        String currencyLine = currencyAmount > 0
                ? currencyAmount + " " + currency.id() + " will also be withdrawn from your balance."
                : "No currency will be escrowed.";
        inventory.setItem(EscrowDepositHolder.CONFIRM_SLOT, GuiItems.button(Material.EMERALD_BLOCK, "Confirm Escrow",
                "Deposit items in the rows above,", "then click here.", "", currencyLine, "",
                "Released to the other party once", "this oath is signed."));
        inventory.setItem(EscrowDepositHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel",
                "Closes this menu and returns", "anything you've deposited.", "No currency is withdrawn until confirmed."));

        player.openInventory(inventory);
    }
}
