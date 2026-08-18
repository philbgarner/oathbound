package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.Banishment;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Click/drag/close glue for {@link BanishmentPrayerBoardHolder} (browse active sentences) and
 * {@link PrayerAltarHolder} (the enchanted-item deposit step) - the banishment side of the "release
 * oath, or prayer at an altar" intercession pair the master plan describes; see
 * {@link com.google.gmail.philbgarner.oathbound.oath.Clause.BanishmentReleaseClause} for the Oath-based
 * path. */
public final class BanishmentPrayerGuiListener implements Listener {

    private final OathboundPlugin plugin;

    public BanishmentPrayerGuiListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof BanishmentPrayerBoardHolder boardHolder) {
            handleBoardClick(event, boardHolder);
        } else if (holder instanceof PrayerAltarHolder prayerHolder) {
            handlePrayerClick(event, prayerHolder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof BanishmentPrayerBoardHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(holder instanceof PrayerAltarHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        boolean onlyDeposit = event.getRawSlots().stream().allMatch(PrayerAltarHolder::isDepositSlot);
        if (!onlyDeposit) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof PrayerAltarHolder holder && !holder.isPosted()) {
            SacrificeInputSupport.returnDeposits(event.getInventory(), PrayerAltarHolder.DEPOSIT_SLOTS_END_EXCLUSIVE, player);
        }
    }

    private void handleBoardClick(InventoryClickEvent event, BanishmentPrayerBoardHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == BanishmentPrayerBoardHolder.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        PlayerRef target = holder.targetAt(rawSlot);
        if (target == null) {
            return;
        }
        player.closeInventory();
        PrayerAltarGui.open(plugin, player, target);
    }

    private void handlePrayerClick(InventoryClickEvent event, PrayerAltarHolder holder) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = rawSlot >= 0 && rawSlot < topSize;
        if (!clickedTop) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        if (PrayerAltarHolder.isDepositSlot(rawSlot)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (rawSlot == PrayerAltarHolder.CONFIRM_SLOT) {
            confirmPrayer(player, holder);
        } else if (rawSlot == PrayerAltarHolder.CANCEL_SLOT) {
            player.sendMessage("Prayer cancelled.");
            player.closeInventory();
        }
    }

    private void confirmPrayer(Player player, PrayerAltarHolder holder) {
        Instant now = Instant.now();
        Optional<Banishment> maybeBanishment = plugin.banishmentCache().values().stream()
                .filter(banishment -> banishment.player().equals(holder.target()) && banishment.active(now))
                .findFirst();
        if (maybeBanishment.isEmpty()) {
            player.sendMessage("They are no longer banished.");
            player.closeInventory();
            return;
        }

        Inventory inventory = holder.getInventory();
        if (SacrificeInputSupport.hasNonEnchantedItem(inventory, PrayerAltarHolder.DEPOSIT_SLOTS_END_EXCLUSIVE)) {
            player.sendMessage("Remove non-enchanted items first - only enchanted items count as artifacts.");
            return;
        }

        long sacrificeValue = SacrificeInputSupport.valueOfDeposits(
                inventory, PrayerAltarHolder.DEPOSIT_SLOTS_END_EXCLUSIVE, plugin);
        if (sacrificeValue <= 0) {
            player.sendMessage("Deposit at least one enchanted item.");
            return;
        }

        SacrificeInputSupport.clearDeposits(inventory, PrayerAltarHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);

        Banishment banishment = maybeBanishment.get();
        double hoursPerPower = plugin.oathboundConfig().banishmentPrayerHoursPerPower();
        Duration reduction = Duration.ofSeconds(Math.round(sacrificeValue * hoursPerPower * 3600));
        banishment.reduceSentence(reduction, now);
        plugin.persistBanishmentAsync(banishment);
        holder.markPosted();

        boolean released = !banishment.active(now);
        String targetName = displayName(holder.target());
        player.sendMessage(released
                ? "Your prayer is answered - " + targetName + "'s sentence is forgiven."
                : "Your prayer cuts " + reduction.toHours() + "h from " + targetName + "'s sentence.");

        Player targetPlayer = Bukkit.getPlayer(holder.target().playerId());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(released
                    ? "Someone has prayed for you - your banishment is forgiven."
                    : "Someone has prayed for you - your sentence is cut by " + reduction.toHours() + "h.");
        }
        player.closeInventory();
    }

    private String displayName(PlayerRef ref) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(ref.playerId());
        return player.getName() != null ? player.getName() : ref.playerId().toString();
    }
}
