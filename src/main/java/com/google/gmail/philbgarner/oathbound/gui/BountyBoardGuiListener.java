package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.Bounty;
import com.google.gmail.philbgarner.oathbound.bounty.BountyPlacementLimitException;
import com.google.gmail.philbgarner.oathbound.bounty.BountyStatus;
import com.google.gmail.philbgarner.oathbound.bounty.PveContractDefinition;
import com.google.gmail.philbgarner.oathbound.bounty.PveContractProgress;
import com.google.gmail.philbgarner.oathbound.economy.InsufficientFundsException;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Click/drag glue for {@link BountyBoardHolder} (browse/cancel) and {@link BountyConfirmHolder} (the
 * final placement confirm step) - kept together since both are steps of the same overall bounty-board
 * flow, separate from {@link BountyPlacementListener}'s chat-prompt state machine. */
public final class BountyBoardGuiListener implements Listener {

    private final OathboundPlugin plugin;

    public BountyBoardGuiListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof BountyBoardHolder boardHolder) {
            handleBoardClick(event, boardHolder);
        } else if (holder instanceof BountyConfirmHolder confirmHolder) {
            handleConfirmClick(event, confirmHolder);
        } else if (holder instanceof PveContractBoardHolder pveHolder) {
            handlePveBoardClick(event, pveHolder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof BountyBoardHolder || holder instanceof BountyConfirmHolder
                || holder instanceof PveContractBoardHolder) {
            event.setCancelled(true);
        }
    }

    private void handleBoardClick(InventoryClickEvent event, BountyBoardHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == BountyBoardHolder.PLACE_NEW_SLOT) {
            player.closeInventory();
            plugin.bountyPlacementListener().beginPrompt(player);
            return;
        }
        if (rawSlot == BountyBoardHolder.STANDING_CONTRACTS_SLOT) {
            player.closeInventory();
            PveContractBoardGui.open(plugin, player);
            return;
        }
        if (rawSlot == BountyBoardHolder.BACK_SLOT) {
            player.closeInventory();
            NotaryMenuGui.open(player);
            return;
        }
        UUID bountyId = holder.bountyIdAt(rawSlot);
        if (bountyId == null) {
            return;
        }
        Bounty bounty = plugin.bountyCache().get(bountyId);
        if (bounty == null || bounty.status() != BountyStatus.ACTIVE) {
            player.sendMessage("That bounty is no longer active.");
            return;
        }
        PlayerRef viewer = new PlayerRef(player.getUniqueId());
        if (!bounty.placer().equals(viewer)) {
            player.sendMessage("Only the placer can cancel a bounty.");
            return;
        }
        plugin.bountyService().cancel(bounty);
        plugin.persistBountyAsync(bounty);
        player.sendMessage("Bounty cancelled - the unpaid reward has been refunded.");
        player.closeInventory();
        BountyBoardGui.open(plugin, player);
    }

    private void handleConfirmClick(InventoryClickEvent event, BountyConfirmHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == BountyConfirmHolder.CANCEL_SLOT) {
            player.sendMessage("Bounty placement cancelled.");
            player.closeInventory();
            BountyBoardGui.open(plugin, player);
            return;
        }
        if (rawSlot != BountyConfirmHolder.CONFIRM_SLOT) {
            return;
        }
        PlayerRef placer = new PlayerRef(player.getUniqueId());
        try {
            Bounty bounty = plugin.bountyService().place(placer, holder.target(), holder.quantity(),
                    holder.reward(), plugin.economyService().defaultCurrency(), Instant.now());
            plugin.bountyCache().put(bounty.id(), bounty);
            plugin.persistBountyAsync(bounty);
            player.sendMessage("Bounty placed - fee " + bounty.feePaid() + ", reward " + bounty.totalReward() + ".");
            player.closeInventory();
        } catch (InsufficientFundsException e) {
            player.sendMessage("You can't afford the fee plus reward anymore.");
        } catch (BountyPlacementLimitException e) {
            player.sendMessage(e.getMessage());
        } catch (IllegalArgumentException e) {
            player.sendMessage("Couldn't place that bounty: " + e.getMessage());
        }
    }

    private void handlePveBoardClick(InventoryClickEvent event, PveContractBoardHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == PveContractBoardHolder.BACK_SLOT) {
            player.closeInventory();
            BountyBoardGui.open(plugin, player);
            return;
        }
        String contractId = holder.contractIdAt(rawSlot);
        if (contractId == null) {
            return;
        }
        Optional<PveContractDefinition> definition = plugin.oathboundConfig().pveContracts().stream()
                .filter(d -> d.id().equals(contractId))
                .findFirst();
        if (definition.isEmpty()) {
            player.sendMessage("That standing contract no longer exists.");
            return;
        }
        PlayerRef playerRef = new PlayerRef(player.getUniqueId());
        PveContractProgress progress = plugin.pveContractService().accept(playerRef, definition.get(), Instant.now(),
                plugin.pveContractProgressCache().values());
        plugin.pveContractProgressCache().put(progress.id(), progress);
        plugin.persistPveContractProgressAsync(progress);
        player.sendMessage("Accepted '" + definition.get().displayName() + "' - kill " + definition.get().quantity()
                + "x " + definition.get().mobTypeName() + " for " + definition.get().reward() + ".");
        PveContractBoardGui.open(plugin, player);
    }
}
