package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.economy.InsufficientFundsException;
import com.google.gmail.philbgarner.oathbound.economy.PlayerBalance;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.villager.VillagerTradeOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Click glue for {@link VillagerShopHolder}: buy from the NPC (withdraw currency, grant the item) or
 * sell to it (take the item, deposit currency). Every click is a fixed 1-unit transaction. */
public final class VillagerShopGuiListener implements Listener {

    private final OathboundPlugin plugin;

    public VillagerShopGuiListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof VillagerShopHolder shopHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == VillagerShopHolder.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        VillagerTradeOffer sellOffer = shopHolder.sellOfferAt(rawSlot);
        if (sellOffer != null) {
            handleBuy(player, sellOffer);
            return;
        }
        VillagerTradeOffer buyOffer = shopHolder.buyOfferAt(rawSlot);
        if (buyOffer != null) {
            handleSell(player, buyOffer);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof VillagerShopHolder) {
            event.setCancelled(true);
        }
    }

    private void handleBuy(Player player, VillagerTradeOffer offer) {
        EconomyService economy = plugin.economyService();
        PlayerRef ref = new PlayerRef(player.getUniqueId());
        Currency currency = economy.defaultCurrency();
        try {
            economy.withdraw(ref, currency, offer.price());
        } catch (InsufficientFundsException e) {
            player.sendMessage("You can't afford that - it costs " + offer.price() + " " + currency.id() + ".");
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(offer.material(), 1));
        leftover.values().forEach(remaining -> player.getWorld().dropItemNaturally(player.getLocation(), remaining));
        plugin.persistBalanceAsync(new PlayerBalance(ref, currency, economy.balance(ref, currency)));
        player.sendMessage("Bought 1x " + offer.material() + " for " + offer.price() + " " + currency.id() + ".");
    }

    private void handleSell(Player player, VillagerTradeOffer offer) {
        ItemStack template = new ItemStack(offer.material(), 1);
        if (!player.getInventory().containsAtLeast(template, 1)) {
            player.sendMessage("You don't have any " + offer.material() + " to sell.");
            return;
        }
        player.getInventory().removeItem(template);
        EconomyService economy = plugin.economyService();
        PlayerRef ref = new PlayerRef(player.getUniqueId());
        Currency currency = economy.defaultCurrency();
        economy.deposit(ref, currency, offer.price());
        plugin.persistBalanceAsync(new PlayerBalance(ref, currency, economy.balance(ref, currency)));
        player.sendMessage("Sold 1x " + offer.material() + " for " + offer.price() + " " + currency.id() + ".");
    }
}
