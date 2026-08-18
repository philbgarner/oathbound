package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.SacrificeValuationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;

/** Click/drag/close glue for the altar sacrifice GUI. Enchantment extraction and valuation is shared
 * with the banishment prayer ritual via {@link SacrificeInputSupport}, keeping
 * {@link SacrificeValuationService} itself Bukkit-free in exactly one place. */
public final class AltarSacrificeGuiListener implements Listener {

    private final OathboundPlugin plugin;

    public AltarSacrificeGuiListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof AltarSacrificeHolder sacrificeHolder)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = rawSlot >= 0 && rawSlot < topSize;
        if (!clickedTop) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        if (AltarSacrificeHolder.isDepositSlot(rawSlot)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (rawSlot == AltarSacrificeHolder.CONFIRM_SLOT) {
            confirmSacrifice(player, sacrificeHolder);
        } else if (rawSlot == AltarSacrificeHolder.CANCEL_SLOT) {
            player.sendMessage("Sacrifice cancelled.");
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof AltarSacrificeHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        boolean onlyDeposit = event.getRawSlots().stream().allMatch(AltarSacrificeHolder::isDepositSlot);
        if (!onlyDeposit) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof AltarSacrificeHolder holder && !holder.isPosted()) {
            SacrificeInputSupport.returnDeposits(event.getInventory(), AltarSacrificeHolder.DEPOSIT_SLOTS_END_EXCLUSIVE, player);
        }
    }

    private void confirmSacrifice(Player player, AltarSacrificeHolder holder) {
        Altar altar = plugin.altarCache().get(holder.altarId());
        if (altar == null) {
            player.sendMessage("This altar no longer exists.");
            player.closeInventory();
            return;
        }

        Inventory inventory = holder.getInventory();
        if (SacrificeInputSupport.hasNonEnchantedItem(inventory, AltarSacrificeHolder.DEPOSIT_SLOTS_END_EXCLUSIVE)) {
            player.sendMessage("Remove non-enchanted items first - only enchanted items count as artifacts.");
            return;
        }

        long sacrificeValue = SacrificeInputSupport.valueOfDeposits(
                inventory, AltarSacrificeHolder.DEPOSIT_SLOTS_END_EXCLUSIVE, plugin);
        if (sacrificeValue <= 0) {
            player.sendMessage("Deposit at least one enchanted item.");
            return;
        }

        SacrificeInputSupport.clearDeposits(inventory, AltarSacrificeHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);

        Instant now = Instant.now();
        int decayDays = plugin.oathboundConfig().altarDecayDays();
        long newBaseline = altar.currentPower(now, decayDays) + sacrificeValue;
        altar.applySacrifice(newBaseline, now, plugin.oathboundConfig().altarReconsecrationCooldown(), sacrificeValue);
        plugin.persistAltarAsync(altar);

        holder.markPosted();
        player.sendMessage("Sacrifice accepted - Altar Power is now " + newBaseline + ".");
        player.closeInventory();
    }
}
