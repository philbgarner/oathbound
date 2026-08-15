package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bukkit.ItemStackSerialization;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathState;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Click/drag/close/join glue for the three trade GUIs. Kept as one listener since the GUIs share the
 * same "deposit items, confirm or cancel, return anything left on close" shape. */
public final class TradeGuiListener implements Listener {

    private final OathboundPlugin plugin;

    public TradeGuiListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof TradeBuilderHolder builderHolder) {
            handleBuilderClick(event, builderHolder);
        } else if (holder instanceof TradeFulfillHolder fulfillHolder) {
            handleFulfillClick(event, fulfillHolder);
        } else if (holder instanceof TradeBoardHolder boardHolder) {
            handleBoardClick(event, boardHolder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        if (holder instanceof TradeBuilderHolder) {
            boolean onlyDeposit = event.getRawSlots().stream().allMatch(TradeBuilderHolder::isDepositSlot);
            if (!onlyDeposit) {
                event.setCancelled(true);
            }
        } else if (holder instanceof TradeFulfillHolder) {
            boolean onlyDeposit = event.getRawSlots().stream().allMatch(TradeFulfillHolder::isDepositSlot);
            if (!onlyDeposit) {
                event.setCancelled(true);
            }
        } else if (holder instanceof TradeBoardHolder) {
            event.setCancelled(true);
        }
    }

    // ---- builder ----

    private void handleBuilderClick(InventoryClickEvent event, TradeBuilderHolder holder) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = rawSlot >= 0 && rawSlot < topSize;
        if (!clickedTop) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        if (TradeBuilderHolder.isDepositSlot(rawSlot)) {
            return;
        }
        event.setCancelled(true);
        if (rawSlot == TradeBuilderHolder.POST_SLOT) {
            postContract(event.getWhoClicked(), holder);
        } else if (rawSlot == TradeBuilderHolder.CANCEL_SLOT) {
            event.getWhoClicked().closeInventory();
        }
    }

    private void postContract(HumanEntity human, TradeBuilderHolder holder) {
        if (!(human instanceof Player player)) {
            return;
        }
        List<SerializedItemStack> items = takeItems(holder.getInventory(), 0, TradeBuilderHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);
        if (items.isEmpty()) {
            player.sendMessage("Deposit at least one item before posting a contract.");
            return;
        }

        PlayerRef creatorRef = holder.creator();
        Oath oath = plugin.oathService().createOpenDraft(creatorRef, false);
        plugin.oathService().propose(oath, creatorRef);
        plugin.oathCache().put(oath.id(), oath);
        plugin.persistOathAsync(oath);

        TradeOffer offer = new TradeOffer(oath.id(), creatorRef, items);
        plugin.tradeOfferCache().put(oath.id(), offer);
        plugin.persistTradeOfferAsync(offer);

        holder.markPosted();
        player.sendMessage("Contract posted. Anyone can accept it from /oathbound-trade board.");
        player.closeInventory();
    }

    // ---- fulfill ----

    private void handleFulfillClick(InventoryClickEvent event, TradeFulfillHolder holder) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = rawSlot >= 0 && rawSlot < topSize;
        if (!clickedTop) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        if (TradeFulfillHolder.isDisplaySlot(rawSlot)) {
            event.setCancelled(true);
            return;
        }
        if (TradeFulfillHolder.isDepositSlot(rawSlot)) {
            return;
        }
        event.setCancelled(true);
        if (rawSlot == TradeFulfillHolder.CONFIRM_SLOT) {
            confirmTrade(event.getWhoClicked(), holder);
        } else if (rawSlot == TradeFulfillHolder.CANCEL_SLOT) {
            event.getWhoClicked().closeInventory();
        }
    }

    private void confirmTrade(HumanEntity human, TradeFulfillHolder holder) {
        if (!(human instanceof Player player)) {
            return;
        }
        Oath oath = plugin.oathCache().get(holder.oathId());
        TradeOffer offer = plugin.tradeOfferCache().get(holder.oathId());
        if (oath == null || offer == null || !oath.open() || oath.state() != OathState.PROPOSED) {
            player.sendMessage("This contract is no longer available.");
            returnDepositedItems(player, holder.getInventory(),
                    TradeFulfillHolder.DEPOSIT_SLOTS_START, TradeFulfillHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);
            player.closeInventory();
            return;
        }

        PlayerRef fulfillerRef = new PlayerRef(player.getUniqueId());
        if (offer.creator().equals(fulfillerRef)) {
            player.sendMessage("You can't fulfill your own contract.");
            return;
        }

        List<SerializedItemStack> fulfillerItems = takeItems(holder.getInventory(),
                TradeFulfillHolder.DEPOSIT_SLOTS_START, TradeFulfillHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);
        if (fulfillerItems.isEmpty()) {
            player.sendMessage("Deposit at least one item before confirming.");
            return;
        }

        plugin.oathService().acceptOpen(oath, fulfillerRef);
        offer.fulfill(fulfillerRef, fulfillerItems);
        plugin.oathService().fulfill(oath, fulfillerRef);
        plugin.persistOathAsync(oath);

        deliver(player, offer.creatorItems());
        offer.markCreatorItemsDelivered();

        Player creatorPlayer = Bukkit.getPlayer(offer.creator().playerId());
        if (creatorPlayer != null) {
            deliver(creatorPlayer, offer.fulfillerItems());
            offer.markFulfillerItemsDelivered();
            creatorPlayer.sendMessage("Your open contract was fulfilled by " + player.getName() + "!");
        }
        plugin.persistTradeOfferAsync(offer);

        holder.markConfirmed();
        player.sendMessage("Trade complete!");
        player.closeInventory();
    }

    // ---- board ----

    private void handleBoardClick(InventoryClickEvent event, TradeBoardHolder holder) {
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < 0 || rawSlot >= topSize) {
            return;
        }
        UUID oathId = holder.oathIdAt(rawSlot);
        if (oathId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Oath oath = plugin.oathCache().get(oathId);
        TradeOffer offer = plugin.tradeOfferCache().get(oathId);
        if (oath == null || offer == null || !oath.open() || oath.state() != OathState.PROPOSED) {
            player.sendMessage("That contract is no longer available.");
            player.closeInventory();
            return;
        }

        PlayerRef viewerRef = new PlayerRef(player.getUniqueId());
        if (offer.creator().equals(viewerRef)) {
            cancelOwnContract(player, oath, offer);
            return;
        }

        player.closeInventory();
        TradeFulfillGui.open(plugin, player, oathId);
    }

    private void cancelOwnContract(Player player, Oath oath, TradeOffer offer) {
        plugin.oathService().voidOath(oath, offer.creator());
        plugin.persistOathAsync(oath);

        deliver(player, offer.creatorItems());
        offer.markCreatorItemsDelivered();
        plugin.persistTradeOfferAsync(offer);

        player.sendMessage("Contract cancelled and items returned.");
        player.closeInventory();
    }

    // ---- close / join ----

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (holder instanceof TradeBuilderHolder builderHolder && !builderHolder.isPosted()) {
            returnDepositedItems(player, event.getInventory(), 0, TradeBuilderHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);
        } else if (holder instanceof TradeFulfillHolder fulfillHolder && !fulfillHolder.isConfirmed()) {
            returnDepositedItems(player, event.getInventory(),
                    TradeFulfillHolder.DEPOSIT_SLOTS_START, TradeFulfillHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerRef playerRef = new PlayerRef(player.getUniqueId());
        for (TradeOffer offer : plugin.tradeOfferCache().values()) {
            if (!offer.completed() || offer.fulfillerItemsDelivered()) {
                continue;
            }
            if (offer.creator().equals(playerRef)) {
                deliver(player, offer.fulfillerItems());
                offer.markFulfillerItemsDelivered();
                plugin.persistTradeOfferAsync(offer);
                player.sendMessage("You've received the items from a completed trade contract.");
            }
        }
    }

    // ---- shared helpers ----

    private List<SerializedItemStack> takeItems(Inventory inventory, int fromInclusive, int toExclusive) {
        List<SerializedItemStack> items = new ArrayList<>();
        for (int slot = fromInclusive; slot < toExclusive; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                items.add(ItemStackSerialization.serialize(stack));
                inventory.setItem(slot, null);
            }
        }
        return items;
    }

    private void returnDepositedItems(Player player, Inventory inventory, int fromInclusive, int toExclusive) {
        for (int slot = fromInclusive; slot < toExclusive; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                inventory.setItem(slot, null);
                giveOrDrop(player, stack);
            }
        }
    }

    private void deliver(Player player, List<SerializedItemStack> items) {
        for (SerializedItemStack serialized : items) {
            giveOrDrop(player, ItemStackSerialization.deserialize(serialized));
        }
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(remaining -> player.getWorld().dropItemNaturally(player.getLocation(), remaining));
    }
}
