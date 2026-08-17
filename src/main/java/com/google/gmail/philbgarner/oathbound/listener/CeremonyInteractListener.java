package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bukkit.CeremonyItems;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTemplateDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/** Right-clicking another player while holding a tagged {@link CeremonyItems} item starts a Ceremony
 * Designer exchange instead of whatever vanilla interaction would normally happen - mirrors
 * {@code NotaryInteractListener}'s "check the held item, cancel, branch" shape. */
public final class CeremonyInteractListener implements Listener {

    private final OathboundPlugin plugin;

    public CeremonyInteractListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player initiator = event.getPlayer();
        ItemStack inHand = initiator.getInventory().getItemInMainHand();
        Optional<String> templateId = CeremonyItems.readTemplateId(plugin, inHand);
        if (templateId.isEmpty()) {
            return;
        }
        Optional<UUID> liegeGroupId = CeremonyItems.readLiegeGroupId(plugin, inHand);
        if (liegeGroupId.isEmpty()) {
            return;
        }
        Optional<CeremonyTemplateDefinition> template = plugin.oathboundConfig().ceremonyTemplates().stream()
                .filter(t -> t.id().equals(templateId.get()))
                .findFirst();
        if (template.isEmpty()) {
            initiator.sendMessage("That ceremony template no longer exists.");
            return;
        }
        if (target.getUniqueId().equals(initiator.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        plugin.ceremonyChatListener().beginPrompt(initiator, target, template.get(), liegeGroupId.get());
    }
}
