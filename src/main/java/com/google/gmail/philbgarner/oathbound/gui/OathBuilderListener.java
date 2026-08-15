package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bukkit.ItemStackSerialization;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.InsufficientFundsException;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Condition;
import com.google.gmail.philbgarner.oathbound.oath.EscrowClaim;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathState;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Click/drag/chat glue for the named-party oath builder, group picker, pending-oath, escrow-deposit,
 * and escrow-claim GUIs. Text and number input (flag text, kill-count target/quantity, transfer owner
 * name, escrow currency amount) is collected by closing the GUI and intercepting the player's next chat
 * message rather than any in-inventory text field. */
public final class OathBuilderListener implements Listener {

    private enum PromptKind {
        CUSTOM_FLAG_TEXT, KILLCOUNT_TARGET_NAME, KILLCOUNT_QUANTITY, TRANSFER_OWNER_PLAYER_NAME, ESCROW_CURRENCY_AMOUNT
    }

    private record PendingPrompt(PromptKind kind, UUID oathId, Object context) {
    }

    private final OathboundPlugin plugin;
    private final Map<UUID, PendingPrompt> pendingPrompts = new ConcurrentHashMap<>();

    public OathBuilderListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof OathBuilderHolder builderHolder) {
            handleBuilderClick(event, builderHolder);
        } else if (holder instanceof GroupPickerHolder pickerHolder) {
            handleGroupPickerClick(event, pickerHolder);
        } else if (holder instanceof PendingOathBoardHolder boardHolder) {
            handlePendingBoardClick(event, boardHolder);
        } else if (holder instanceof PendingOathDetailHolder detailHolder) {
            handlePendingDetailClick(event, detailHolder);
        } else if (holder instanceof EscrowDepositHolder depositHolder) {
            handleEscrowDepositClick(event, depositHolder);
        } else if (holder instanceof EscrowClaimBoardHolder claimHolder) {
            handleEscrowClaimBoardClick(event, claimHolder);
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
        if (holder instanceof EscrowDepositHolder) {
            boolean onlyDeposit = event.getRawSlots().stream().allMatch(EscrowDepositHolder::isDepositSlot);
            if (!onlyDeposit) {
                event.setCancelled(true);
            }
        } else if (holder instanceof OathBuilderHolder || holder instanceof GroupPickerHolder
                || holder instanceof PendingOathBoardHolder || holder instanceof PendingOathDetailHolder
                || holder instanceof EscrowClaimBoardHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof EscrowDepositHolder depositHolder && !depositHolder.isPosted()) {
            returnDepositedItems(player, event.getInventory(), 0, EscrowDepositHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef playerRef = new PlayerRef(event.getPlayer().getUniqueId());
        boolean hasUnclaimed = plugin.escrowClaimCache().values().stream()
                .anyMatch(claim -> !claim.claimed() && claim.holder().equals(playerRef));
        if (hasUnclaimed) {
            event.getPlayer().sendMessage("You have unclaimed escrow waiting - check /oathbound-oath claim.");
        }
    }

    // ---- oath builder ----

    private void handleBuilderClick(InventoryClickEvent event, OathBuilderHolder holder) {
        if (!isTopClick(event)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == OathBuilderHolder.ADD_TRANSFER_SLOT) {
            player.closeInventory();
            GroupPickerGui.openForSubject(plugin, player, holder.oathId());
        } else if (rawSlot == OathBuilderHolder.ADD_FLAG_SLOT) {
            player.closeInventory();
            beginPrompt(player, PromptKind.CUSTOM_FLAG_TEXT, holder.oathId(), null,
                    "Type the custom flag text in chat (or 'cancel').");
        } else if (rawSlot == OathBuilderHolder.ADD_KILLCOUNT_SLOT) {
            player.closeInventory();
            beginPrompt(player, PromptKind.KILLCOUNT_TARGET_NAME, holder.oathId(), null,
                    "Type the target player's name in chat (or 'cancel').");
        } else if (rawSlot == OathBuilderHolder.ADD_ESCROW_SLOT) {
            player.closeInventory();
            Currency currency = plugin.economyService().defaultCurrency();
            beginPrompt(player, PromptKind.ESCROW_CURRENCY_AMOUNT, holder.oathId(), null,
                    "Type how much " + currency.id() + " to escrow (0 for none), or 'cancel'.");
        } else if (rawSlot == OathBuilderHolder.PROPOSE_SLOT) {
            proposeOath(player, holder);
        } else if (rawSlot == OathBuilderHolder.CANCEL_SLOT) {
            cancelDraft(player, holder);
        }
    }

    private void proposeOath(Player player, OathBuilderHolder holder) {
        Oath oath = plugin.oathCache().get(holder.oathId());
        if (oath == null) {
            player.sendMessage("That oath draft no longer exists.");
            player.closeInventory();
            return;
        }
        if (oath.clauses().isEmpty()) {
            player.sendMessage("Add at least one clause before proposing.");
            return;
        }
        plugin.oathService().propose(oath, holder.creator());
        plugin.persistOathAsync(oath);

        PlayerRef counterparty = oath.parties().get(1);
        OfflinePlayer other = Bukkit.getOfflinePlayer(counterparty.playerId());
        player.sendMessage("Oath proposed to " + (other.getName() != null ? other.getName() : counterparty.playerId()) + ".");
        Player otherPlayer = Bukkit.getPlayer(counterparty.playerId());
        if (otherPlayer != null) {
            otherPlayer.sendMessage(player.getName() + " has proposed an oath. Check /oathbound-oath pending.");
        }
        player.closeInventory();
    }

    private void cancelDraft(Player player, OathBuilderHolder holder) {
        Oath oath = plugin.oathCache().get(holder.oathId());
        if (oath != null) {
            plugin.oathService().voidOath(oath, holder.creator());
            plugin.persistOathAsync(oath);
        }
        player.sendMessage("Draft cancelled.");
        player.closeInventory();
    }

    // ---- group picker ----

    private void handleGroupPickerClick(InventoryClickEvent event, GroupPickerHolder holder) {
        if (!isTopClick(event)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == GroupPickerHolder.CANCEL_SLOT) {
            OathBuilderGui.open(plugin, player, holder.oathId());
            return;
        }
        if (holder.purpose() == GroupPickerHolder.Purpose.OWNER_TARGET_GROUP && rawSlot == GroupPickerHolder.PLAYER_INSTEAD_SLOT) {
            player.closeInventory();
            beginPrompt(player, PromptKind.TRANSFER_OWNER_PLAYER_NAME, holder.oathId(), holder.subjectGroupId(),
                    "Type the new owner's player name in chat (or 'cancel').");
            return;
        }
        if (!GroupPickerHolder.isListSlot(rawSlot)) {
            return;
        }
        UUID groupId = holder.groupIdAt(rawSlot);
        if (groupId == null) {
            return;
        }
        if (holder.purpose() == GroupPickerHolder.Purpose.SUBJECT_GROUP) {
            GroupPickerGui.openForOwnerTarget(plugin, player, holder.oathId(), groupId);
            return;
        }
        Oath oath = plugin.oathCache().get(holder.oathId());
        if (oath == null) {
            player.sendMessage("That oath draft no longer exists.");
            return;
        }
        plugin.oathService().addClause(oath, new Clause.TransferClause(
                new ProtectionGroupRef(groupId), new ProtectionGroupRef(holder.subjectGroupId()), new Condition.Immediate()));
        plugin.persistOathAsync(oath);
        OathBuilderGui.open(plugin, player, holder.oathId());
    }

    // ---- pending board ----

    private void handlePendingBoardClick(InventoryClickEvent event, PendingOathBoardHolder holder) {
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
        player.closeInventory();
        PendingOathDetailGui.open(plugin, player, oathId);
    }

    // ---- pending detail ----

    private void handlePendingDetailClick(InventoryClickEvent event, PendingOathDetailHolder holder) {
        if (!isTopClick(event)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == PendingOathDetailHolder.SIGN_SLOT) {
            signOath(player, holder);
        } else if (rawSlot == PendingOathDetailHolder.DECLINE_SLOT) {
            declineOath(player, holder);
        }
    }

    private void signOath(Player player, PendingOathDetailHolder holder) {
        Oath oath = requireSignableOath(player, holder);
        if (oath == null) {
            return;
        }
        PlayerRef actor = new PlayerRef(player.getUniqueId());
        plugin.oathService().seal(oath, actor);
        plugin.oathService().activate(oath, actor);
        plugin.persistOathAsync(oath);
        player.sendMessage("Oath signed and active!");
        Player creatorPlayer = Bukkit.getPlayer(oath.parties().get(0).playerId());
        if (creatorPlayer != null) {
            creatorPlayer.sendMessage(player.getName() + " signed your oath - it's now active!");
        }
        player.closeInventory();
    }

    private void declineOath(Player player, PendingOathDetailHolder holder) {
        Oath oath = requireSignableOath(player, holder);
        if (oath == null) {
            return;
        }
        PlayerRef actor = new PlayerRef(player.getUniqueId());
        plugin.oathService().voidOath(oath, actor);
        plugin.persistOathAsync(oath);
        player.sendMessage("Oath declined.");
        Player creatorPlayer = Bukkit.getPlayer(oath.parties().get(0).playerId());
        if (creatorPlayer != null) {
            creatorPlayer.sendMessage(player.getName() + " declined your oath.");
        }
        player.closeInventory();
    }

    /** Re-validates the clicker is actually the named counterparty (not the creator, not a bystander)
     * before signing/declining - {@link com.google.gmail.philbgarner.oathbound.oath.OathService} itself
     * doesn't check actor identity, so this belongs at the GUI layer. */
    private Oath requireSignableOath(Player player, PendingOathDetailHolder holder) {
        Oath oath = plugin.oathCache().get(holder.oathId());
        if (oath == null) {
            player.sendMessage("That oath is no longer available.");
            player.closeInventory();
            return null;
        }
        PlayerRef actor = new PlayerRef(player.getUniqueId());
        List<PlayerRef> parties = oath.parties();
        if (parties.size() < 2 || !parties.contains(actor) || parties.get(0).equals(actor)) {
            player.sendMessage("You are not the named counterparty on this oath.");
            player.closeInventory();
            return null;
        }
        if (oath.state() != OathState.PROPOSED) {
            player.sendMessage("This oath is no longer pending.");
            player.closeInventory();
            return null;
        }
        return oath;
    }

    // ---- escrow deposit ----

    private void handleEscrowDepositClick(InventoryClickEvent event, EscrowDepositHolder holder) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = rawSlot >= 0 && rawSlot < topSize;
        if (!clickedTop) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        if (EscrowDepositHolder.isDepositSlot(rawSlot)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (rawSlot == EscrowDepositHolder.CONFIRM_SLOT) {
            confirmEscrowDeposit(player, holder);
        } else if (rawSlot == EscrowDepositHolder.CANCEL_SLOT) {
            player.sendMessage("Escrow deposit cancelled.");
            player.closeInventory();
            OathBuilderGui.open(plugin, player, holder.oathId());
        }
    }

    private void confirmEscrowDeposit(Player player, EscrowDepositHolder holder) {
        Oath oath = plugin.oathCache().get(holder.oathId());
        if (oath == null) {
            player.sendMessage("That oath draft no longer exists.");
            player.closeInventory();
            return;
        }
        if (oath.parties().size() < 2) {
            player.sendMessage("This oath doesn't have a named counterparty yet.");
            return;
        }

        boolean depositingCurrency = holder.currencyAmount() > 0;
        if (depositingCurrency) {
            long balance = plugin.economyService().balance(holder.depositor(), holder.currency());
            if (balance < holder.currencyAmount()) {
                player.sendMessage("You don't have " + holder.currencyAmount() + " " + holder.currency().id()
                        + " - you have " + balance + ".");
                return;
            }
        }

        List<SerializedItemStack> items = takeItems(holder.getInventory(), 0, EscrowDepositHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);
        if (items.isEmpty() && !depositingCurrency) {
            player.sendMessage("Deposit at least one item, or set a currency amount, before confirming.");
            return;
        }

        if (depositingCurrency) {
            try {
                plugin.economyService().withdraw(holder.depositor(), holder.currency(), holder.currencyAmount());
            } catch (InsufficientFundsException e) {
                returnDepositedItems(player, holder.getInventory(), 0, EscrowDepositHolder.DEPOSIT_SLOTS_END_EXCLUSIVE);
                player.sendMessage("You don't have enough " + holder.currency().id() + " anymore.");
                return;
            }
        }

        Map<Currency, Long> currencyMap = depositingCurrency ? Map.of(holder.currency(), holder.currencyAmount()) : Map.of();
        PlayerRef recipient = oath.parties().get(1);
        plugin.oathService().addClause(oath, new Clause.EscrowClause(holder.depositor(), recipient, items, currencyMap,
                List.of(new Clause.ReleaseStep(1.0, new Condition.Immediate()))));
        plugin.persistOathAsync(oath);

        holder.markPosted();
        player.sendMessage("Escrow deposited. It's released to them once this oath is signed.");
        player.closeInventory();
        OathBuilderGui.open(plugin, player, holder.oathId());
    }

    // ---- escrow claim board ----

    private void handleEscrowClaimBoardClick(InventoryClickEvent event, EscrowClaimBoardHolder holder) {
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < 0 || rawSlot >= topSize) {
            return;
        }
        UUID claimId = holder.claimIdAt(rawSlot);
        if (claimId == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        EscrowClaim claim = plugin.escrowClaimCache().get(claimId);
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId());
        if (claim == null || claim.claimed() || !claim.holder().equals(viewerRef)) {
            player.sendMessage("That escrow claim is no longer available.");
            player.closeInventory();
            return;
        }

        deliver(player, claim.items());
        claim.claim();
        plugin.persistEscrowClaimAsync(claim);
        player.sendMessage("Claimed.");
        player.closeInventory();
    }

    // ---- chat-intercept text/number prompts ----

    private void beginPrompt(Player player, PromptKind kind, UUID oathId, Object context, String message) {
        pendingPrompts.put(player.getUniqueId(), new PendingPrompt(kind, oathId, context));
        player.sendMessage(message);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingPrompt prompt = pendingPrompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handlePromptInput(player, prompt, text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingPrompts.remove(event.getPlayer().getUniqueId());
    }

    private void handlePromptInput(Player player, PendingPrompt prompt, String text) {
        if (text.equalsIgnoreCase("cancel")) {
            player.sendMessage("Cancelled.");
            OathBuilderGui.open(plugin, player, prompt.oathId());
            return;
        }
        switch (prompt.kind()) {
            case CUSTOM_FLAG_TEXT -> applyCustomFlag(player, prompt, text);
            case KILLCOUNT_TARGET_NAME -> applyKillCountTarget(player, prompt, text);
            case KILLCOUNT_QUANTITY -> applyKillCountQuantity(player, prompt, text);
            case TRANSFER_OWNER_PLAYER_NAME -> applyTransferOwnerPlayer(player, prompt, text);
            case ESCROW_CURRENCY_AMOUNT -> applyEscrowCurrencyAmount(player, prompt, text);
        }
    }

    private void applyCustomFlag(Player player, PendingPrompt prompt, String text) {
        Oath oath = plugin.oathCache().get(prompt.oathId());
        if (oath == null) {
            player.sendMessage("That oath draft no longer exists.");
            return;
        }
        plugin.oathService().addClause(oath, new Clause.CustomFlagClause(text));
        plugin.persistOathAsync(oath);
        OathBuilderGui.open(plugin, player, prompt.oathId());
    }

    private void applyKillCountTarget(Player player, PendingPrompt prompt, String name) {
        OfflinePlayer target = resolveKnownPlayer(name);
        if (target == null) {
            player.sendMessage("Unknown player: " + name);
            OathBuilderGui.open(plugin, player, prompt.oathId());
            return;
        }
        player.sendMessage("Type the required kill count (a whole number) in chat, or 'cancel'.");
        pendingPrompts.put(player.getUniqueId(),
                new PendingPrompt(PromptKind.KILLCOUNT_QUANTITY, prompt.oathId(), new PlayerRef(target.getUniqueId())));
    }

    private void applyKillCountQuantity(Player player, PendingPrompt prompt, String text) {
        int quantity;
        try {
            quantity = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            player.sendMessage("Not a whole number: " + text);
            OathBuilderGui.open(plugin, player, prompt.oathId());
            return;
        }
        if (quantity <= 0) {
            player.sendMessage("Quantity must be positive.");
            OathBuilderGui.open(plugin, player, prompt.oathId());
            return;
        }
        Oath oath = plugin.oathCache().get(prompt.oathId());
        if (oath == null) {
            player.sendMessage("That oath draft no longer exists.");
            return;
        }
        plugin.oathService().addClause(oath, new Clause.KillCountClause((PlayerRef) prompt.context(), quantity));
        plugin.persistOathAsync(oath);
        OathBuilderGui.open(plugin, player, prompt.oathId());
    }

    private void applyTransferOwnerPlayer(Player player, PendingPrompt prompt, String name) {
        OfflinePlayer target = resolveKnownPlayer(name);
        if (target == null) {
            player.sendMessage("Unknown player: " + name);
            OathBuilderGui.open(plugin, player, prompt.oathId());
            return;
        }
        Oath oath = plugin.oathCache().get(prompt.oathId());
        if (oath == null) {
            player.sendMessage("That oath draft no longer exists.");
            return;
        }
        UUID subjectGroupId = (UUID) prompt.context();
        plugin.oathService().addClause(oath, new Clause.TransferClause(
                new PlayerRef(target.getUniqueId()), new ProtectionGroupRef(subjectGroupId), new Condition.Immediate()));
        plugin.persistOathAsync(oath);
        OathBuilderGui.open(plugin, player, prompt.oathId());
    }

    private void applyEscrowCurrencyAmount(Player player, PendingPrompt prompt, String text) {
        long amount;
        try {
            amount = Long.parseLong(text);
        } catch (NumberFormatException e) {
            player.sendMessage("Not a whole number: " + text);
            OathBuilderGui.open(plugin, player, prompt.oathId());
            return;
        }
        if (amount < 0) {
            player.sendMessage("Amount can't be negative.");
            OathBuilderGui.open(plugin, player, prompt.oathId());
            return;
        }
        Oath oath = plugin.oathCache().get(prompt.oathId());
        if (oath == null) {
            player.sendMessage("That oath draft no longer exists.");
            return;
        }
        EscrowDepositGui.open(plugin, player, prompt.oathId(), plugin.economyService().defaultCurrency(), amount);
    }

    private OfflinePlayer resolveKnownPlayer(String name) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (!player.hasPlayedBefore() && !player.isOnline()) {
            return null;
        }
        return player;
    }

    private boolean isTopClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = rawSlot >= 0 && rawSlot < topSize;
        if (!clickedTop && event.isShiftClick()) {
            event.setCancelled(true);
        }
        return clickedTop;
    }

    // ---- item helpers ----

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
