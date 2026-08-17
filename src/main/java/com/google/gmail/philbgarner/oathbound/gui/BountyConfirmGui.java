package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.BountyService;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTarget;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Final confirmation screen for a bounty placement in progress - shows the heat-scaled fee (and whether
 * a breach discount applied) before the player commits any currency. */
public final class BountyConfirmGui {
    private BountyConfirmGui() {
    }

    public static void open(OathboundPlugin plugin, Player player, BountyTarget target, int quantity,
                             Map<Currency, Long> reward) {
        BountyConfirmHolder holder = new BountyConfirmHolder(target, quantity, reward);
        Inventory inventory = Bukkit.createInventory(holder, BountyConfirmHolder.SIZE, Component.text("Confirm Bounty"));
        holder.setInventory(inventory);

        for (int i = 0; i < BountyConfirmHolder.SIZE; i++) {
            inventory.setItem(i, GuiItems.filler());
        }

        BountyService.PlacementQuote quote = plugin.bountyService().quote(
                new PlayerRef(player.getUniqueId()), target, Instant.now());

        List<String> lore = new ArrayList<>();
        lore.add("Target: " + BountyBoardGui.targetName(plugin, target));
        lore.add(quantity > 1 ? "Quantity: " + quantity : "");
        lore.add("Reward: " + reward);
        lore.add("Notary fee: " + quote.fee() + (quote.discounted() ? " (breach discount applied)" : ""));
        lore.removeIf(String::isEmpty);

        inventory.setItem(4, GuiItems.button(Material.PAPER, "Summary", lore.toArray(String[]::new)));
        inventory.setItem(BountyConfirmHolder.CONFIRM_SLOT, GuiItems.button(Material.EMERALD_BLOCK, "Confirm"));
        inventory.setItem(BountyConfirmHolder.CANCEL_SLOT, GuiItems.button(Material.BARRIER, "Cancel"));

        player.openInventory(inventory);
    }
}
