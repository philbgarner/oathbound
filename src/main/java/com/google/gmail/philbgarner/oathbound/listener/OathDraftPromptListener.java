package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.command.OathboundOathCommand;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared "type the counterparty's name in chat" prompt used by both the Notary's New Oath button and
 * the Sealing Table - the same chat-intercept pattern {@link com.google.gmail.philbgarner.oathbound.gui.OathBuilderListener}
 * uses for clause input, kept separate since neither physical entry point is a chest GUI itself.
 */
public final class OathDraftPromptListener implements Listener {

    private final OathboundPlugin plugin;
    private final Set<UUID> awaitingCounterpartyName = ConcurrentHashMap.newKeySet();

    public OathDraftPromptListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    public void beginPrompt(Player player) {
        awaitingCounterpartyName.add(player.getUniqueId());
        player.sendMessage("Type the name of who you want to draft an oath with in chat (or 'cancel').");
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingCounterpartyName.remove(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) {
                player.sendMessage("Cancelled.");
                return;
            }
            OathboundOathCommand.startNamedDraft(plugin, player, text, false);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingCounterpartyName.remove(event.getPlayer().getUniqueId());
    }
}
