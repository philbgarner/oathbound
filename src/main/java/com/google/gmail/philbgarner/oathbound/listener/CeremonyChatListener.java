package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bukkit.ItemStackSerialization;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyClauseSpec;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTemplateDefinition;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyValidationException;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The "reply I do / Yes to accept" half of the Ceremony Designer, mirroring the chat-intercept pattern
 * {@code OathDraftPromptListener}/{@code OathBuilderListener} already use elsewhere - the target's next
 * chat message is checked against the template's confirm/decline phrases rather than treated as a free
 * text field. A confirm materializes and seals the oath in one atomic step (both parties already
 * consented via the physical ceremony), a decline or an expired prompt clears the pending state without
 * creating anything.
 */
public final class CeremonyChatListener implements Listener {

    private record PendingCeremony(CeremonyTemplateDefinition template, UUID initiatorId, UUID liegeGroupId,
                                    Instant expiresAt) {
    }

    private final OathboundPlugin plugin;
    private final Map<UUID, PendingCeremony> pending = new ConcurrentHashMap<>();

    public CeremonyChatListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    public void beginPrompt(Player initiator, Player target, CeremonyTemplateDefinition template, UUID liegeGroupId) {
        if (pending.containsKey(target.getUniqueId())) {
            initiator.sendMessage("They're already considering another ceremony.");
            return;
        }
        Instant expiresAt = Instant.now().plusSeconds(template.promptTimeoutSeconds());
        pending.put(target.getUniqueId(), new PendingCeremony(template, initiator.getUniqueId(), liegeGroupId, expiresAt));
        for (String line : template.dialogueLines()) {
            target.sendMessage(line.replace("{initiator}", initiator.getName()).replace("{target}", target.getName()));
        }
        initiator.sendMessage("You have begun the '" + template.displayName() + "' ceremony with " + target.getName() + ".");
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingCeremony ceremony = pending.get(player.getUniqueId());
        if (ceremony == null) {
            return;
        }
        if (Instant.now().isAfter(ceremony.expiresAt())) {
            pending.remove(player.getUniqueId());
            return;
        }

        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        boolean confirmed = ceremony.template().confirmPhrases().stream().anyMatch(text::equalsIgnoreCase);
        boolean declined = !confirmed && ceremony.template().declinePhrases().stream().anyMatch(text::equalsIgnoreCase);
        if (!confirmed && !declined) {
            return;
        }

        event.setCancelled(true);
        pending.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (declined) {
                handleDecline(player, ceremony);
            } else {
                handleConfirm(player, ceremony);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private void handleDecline(Player target, PendingCeremony ceremony) {
        target.sendMessage("You declined the '" + ceremony.template().displayName() + "' ceremony.");
        Player initiator = Bukkit.getPlayer(ceremony.initiatorId());
        if (initiator != null) {
            initiator.sendMessage(target.getName() + " declined the '" + ceremony.template().displayName() + "' ceremony.");
        }
    }

    private void handleConfirm(Player target, PendingCeremony ceremony) {
        Player initiator = Bukkit.getPlayer(ceremony.initiatorId());
        if (initiator == null) {
            target.sendMessage("The other party is no longer online - the ceremony was not sealed.");
            return;
        }

        CeremonyTemplateDefinition template = ceremony.template();
        ProtectionGroup liege = plugin.groupCache().get(ceremony.liegeGroupId());
        if (liege == null) {
            initiator.sendMessage("That kingdom group no longer exists - the ceremony was not sealed.");
            return;
        }
        ProtectionGroupRef liegeGroupRef = new ProtectionGroupRef(liege.id());

        PlayerRef initiatorRef = new PlayerRef(initiator.getUniqueId());
        PlayerRef targetRef = new PlayerRef(target.getUniqueId());

        ProtectionGroupRef subjectGroupRef = null;
        boolean needsSubjectGroup = template.clauses().stream().anyMatch(spec -> spec instanceof CeremonyClauseSpec.TransferSpec);
        if (needsSubjectGroup) {
            try {
                subjectGroupRef = plugin.ceremonyService().resolveSubjectGroup(targetRef);
            } catch (CeremonyValidationException e) {
                initiator.sendMessage("Ceremony not sealed: " + e.getMessage());
                target.sendMessage("Ceremony not sealed: " + e.getMessage());
                return;
            }
        }

        List<CeremonyClauseSpec.TributeSpec> tributeSpecs = template.clauses().stream()
                .filter(spec -> spec instanceof CeremonyClauseSpec.TributeSpec)
                .map(spec -> (CeremonyClauseSpec.TributeSpec) spec)
                .toList();
        Map<CeremonyClauseSpec.TributeSpec, ItemStack> tributeTemplates = new HashMap<>();
        for (CeremonyClauseSpec.TributeSpec tribute : tributeSpecs) {
            Material material = Material.matchMaterial(tribute.materialName());
            if (material == null) {
                initiator.sendMessage("Ceremony not sealed: unknown tribute material " + tribute.materialName() + ".");
                return;
            }
            ItemStack required = new ItemStack(material, tribute.quantity());
            if (!target.getInventory().containsAtLeast(required, tribute.quantity())) {
                target.sendMessage("Ceremony not sealed: you need " + tribute.quantity() + " " + material
                        + " for the tribute owed to " + tribute.recipientPlayerName() + ".");
                initiator.sendMessage("Ceremony not sealed - " + target.getName() + " doesn't have the required tribute.");
                return;
            }
            tributeTemplates.put(tribute, required);
        }

        Map<String, PlayerRef> recipients = new HashMap<>();
        for (CeremonyClauseSpec.TributeSpec tribute : tributeSpecs) {
            OfflinePlayer recipient = Bukkit.getOfflinePlayer(tribute.recipientPlayerName());
            recipients.put(tribute.recipientPlayerName(), new PlayerRef(recipient.getUniqueId()));
        }

        Map<CeremonyClauseSpec.TributeSpec, SerializedItemStack> tributeItems = new HashMap<>();
        for (Map.Entry<CeremonyClauseSpec.TributeSpec, ItemStack> entry : tributeTemplates.entrySet()) {
            target.getInventory().removeItem(entry.getValue());
            tributeItems.put(entry.getKey(), ItemStackSerialization.serialize(entry.getValue()));
        }

        List<Clause> clauses;
        try {
            clauses = plugin.ceremonyService().materialize(template, targetRef, liegeGroupRef, subjectGroupRef,
                    tributeItems, recipients::get);
        } catch (CeremonyValidationException e) {
            initiator.sendMessage("Ceremony not sealed: " + e.getMessage());
            target.sendMessage("Ceremony not sealed: " + e.getMessage());
            return;
        }

        Oath oath = plugin.oathService().createDraft(List.of(initiatorRef, targetRef), template.bloodOath());
        clauses.forEach(clause -> plugin.oathService().addClause(oath, clause));
        plugin.oathService().propose(oath, initiatorRef);
        plugin.oathService().seal(oath, targetRef);
        plugin.oathService().activate(oath, targetRef);
        plugin.oathCache().put(oath.id(), oath);
        plugin.persistOathAsync(oath);

        initiator.sendMessage(target.getName() + " has sworn the '" + template.displayName() + "' oath. Oath id=" + oath.id());
        target.sendMessage("You have sworn the '" + template.displayName() + "' oath to " + initiator.getName() + ".");
    }
}
