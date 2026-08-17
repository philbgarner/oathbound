package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTarget;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Chat-prompt state machine for placing a bounty from the Bounty Board: target name (player or group)
 * -&gt; quantity (group targets only) -&gt; total reward amount -&gt; {@link BountyConfirmGui}. Mirrors the
 * {@code PromptKind}/{@code PendingPrompt}/{@code AsyncChatEvent} idiom in {@link OathBuilderListener},
 * kept separate since bounty placement is a different domain from oath drafting. */
public final class BountyPlacementListener implements Listener {

    private enum PromptKind { TARGET_NAME, QUANTITY, REWARD_AMOUNT }

    private record PendingPrompt(PromptKind kind, BountyTarget target, Integer quantity) {
    }

    private final OathboundPlugin plugin;
    private final Map<UUID, PendingPrompt> pendingPrompts = new ConcurrentHashMap<>();

    public BountyPlacementListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    public void beginPrompt(Player player) {
        pendingPrompts.put(player.getUniqueId(), new PendingPrompt(PromptKind.TARGET_NAME, null, null));
        player.sendMessage("Type the target player's name, or a group's name, in chat (or 'cancel').");
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
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, prompt, text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingPrompts.remove(event.getPlayer().getUniqueId());
    }

    private void handleInput(Player player, PendingPrompt prompt, String text) {
        if (text.equalsIgnoreCase("cancel")) {
            player.sendMessage("Cancelled.");
            BountyBoardGui.open(plugin, player);
            return;
        }
        switch (prompt.kind()) {
            case TARGET_NAME -> applyTargetName(player, text);
            case QUANTITY -> applyQuantity(player, prompt, text);
            case REWARD_AMOUNT -> applyRewardAmount(player, prompt, text);
        }
    }

    private void applyTargetName(Player player, String name) {
        OfflinePlayer target = resolveKnownPlayer(name);
        if (target != null) {
            BountyTarget solo = new BountyTarget.Solo(new PlayerRef(target.getUniqueId()));
            pendingPrompts.put(player.getUniqueId(), new PendingPrompt(PromptKind.REWARD_AMOUNT, solo, 1));
            player.sendMessage("Type the total " + plugin.economyService().defaultCurrency().id()
                    + " reward in chat, or 'cancel'.");
            return;
        }
        Optional<ProtectionGroup> group = plugin.groupCache().values().stream()
                .filter(g -> g.name().equalsIgnoreCase(name))
                .findFirst();
        if (group.isPresent()) {
            BountyTarget groupTarget = new BountyTarget.Group(new ProtectionGroupRef(group.get().id()));
            pendingPrompts.put(player.getUniqueId(), new PendingPrompt(PromptKind.QUANTITY, groupTarget, null));
            player.sendMessage("Type how many heads are required (up to " + group.get().members().size()
                    + ") in chat, or 'cancel'.");
            return;
        }
        player.sendMessage("Unknown player or group: " + name);
        BountyBoardGui.open(plugin, player);
    }

    private void applyQuantity(Player player, PendingPrompt prompt, String text) {
        int quantity;
        try {
            quantity = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            player.sendMessage("Not a whole number: " + text);
            BountyBoardGui.open(plugin, player);
            return;
        }
        if (!(prompt.target() instanceof BountyTarget.Group group)) {
            return;
        }
        ProtectionGroup protectionGroup = plugin.groupCache().get(group.group().groupId());
        int memberCount = protectionGroup == null ? 0 : protectionGroup.members().size();
        if (quantity < 1 || quantity > memberCount) {
            player.sendMessage("Quantity must be between 1 and " + memberCount + ".");
            BountyBoardGui.open(plugin, player);
            return;
        }
        pendingPrompts.put(player.getUniqueId(), new PendingPrompt(PromptKind.REWARD_AMOUNT, prompt.target(), quantity));
        player.sendMessage("Type the total " + plugin.economyService().defaultCurrency().id()
                + " reward (split across all heads) in chat, or 'cancel'.");
    }

    private void applyRewardAmount(Player player, PendingPrompt prompt, String text) {
        long amount;
        try {
            amount = Long.parseLong(text);
        } catch (NumberFormatException e) {
            player.sendMessage("Not a whole number: " + text);
            BountyBoardGui.open(plugin, player);
            return;
        }
        if (amount <= 0) {
            player.sendMessage("Reward must be positive.");
            BountyBoardGui.open(plugin, player);
            return;
        }
        int quantity = prompt.quantity() != null ? prompt.quantity() : 1;
        Map<Currency, Long> reward = Map.of(plugin.economyService().defaultCurrency(), amount);
        BountyConfirmGui.open(plugin, player, prompt.target(), quantity, reward);
    }

    private OfflinePlayer resolveKnownPlayer(String name) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (!player.hasPlayedBefore() && !player.isOnline()) {
            return null;
        }
        return player;
    }
}
