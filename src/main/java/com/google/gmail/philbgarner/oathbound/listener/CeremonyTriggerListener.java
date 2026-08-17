package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bukkit.CeremonyItems;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTemplateDefinition;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTrigger;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTriggerLocation;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Right-clicking an already-placed pressure plate or push button while holding a tagged
 * {@link CeremonyItems} item binds the ceremony there instead of consuming the item, mirroring
 * {@code CeremonyInteractListener}'s "check the held item, cancel, branch" shape. Once bound, any player
 * stepping on the plate (or pressing the button) is prompted as if the installer had right-clicked them -
 * the installer stands in as initiator via {@link com.google.gmail.philbgarner.oathbound.listener.CeremonyChatListener}'s
 * offline-tolerant {@code beginPrompt} overload, since they needn't be online for the trigger to fire. */
public final class CeremonyTriggerListener implements Listener {

    private final OathboundPlugin plugin;

    public CeremonyTriggerListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !isPlateOrButton(block)) {
            return;
        }
        CeremonyTriggerLocation location = new CeremonyTriggerLocation(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        Player player = event.getPlayer();
        CeremonyTrigger existing = findByLocation(location);

        if (existing == null && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() == EquipmentSlot.HAND) {
            if (tryBind(player, location)) {
                event.setCancelled(true);
            }
            return;
        }

        if (existing != null && (event.getAction() == Action.PHYSICAL || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            activate(existing, player);
        }
    }

    private boolean tryBind(Player player, CeremonyTriggerLocation location) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        Optional<String> templateId = CeremonyItems.readTemplateId(plugin, inHand);
        if (templateId.isEmpty()) {
            return false;
        }
        Optional<UUID> liegeGroupId = CeremonyItems.readLiegeGroupId(plugin, inHand);
        if (liegeGroupId.isEmpty()) {
            return false;
        }
        Optional<CeremonyTemplateDefinition> template = plugin.oathboundConfig().ceremonyTemplates().stream()
                .filter(t -> t.id().equals(templateId.get()))
                .findFirst();
        if (template.isEmpty()) {
            player.sendMessage("That ceremony template no longer exists.");
            return true;
        }

        CeremonyTrigger trigger = new CeremonyTrigger(UUID.randomUUID(), location, templateId.get(),
                liegeGroupId.get(), new PlayerRef(player.getUniqueId()), Instant.now());
        plugin.ceremonyTriggerCache().put(trigger.id(), trigger);
        plugin.persistCeremonyTriggerAsync(trigger);
        player.sendMessage("Bound the '" + template.get().displayName() + "' ceremony here.");
        return true;
    }

    private void activate(CeremonyTrigger trigger, Player player) {
        if (player.getUniqueId().equals(trigger.installer().playerId())) {
            return;
        }
        if (plugin.ceremonyChatListener().hasPending(player.getUniqueId())) {
            return;
        }
        Optional<CeremonyTemplateDefinition> template = plugin.oathboundConfig().ceremonyTemplates().stream()
                .filter(t -> t.id().equals(trigger.templateId()))
                .findFirst();
        if (template.isEmpty()) {
            player.sendMessage("That ceremony template no longer exists.");
            return;
        }

        OfflinePlayer installer = Bukkit.getOfflinePlayer(trigger.installer().playerId());
        String installerName = installer.getName() != null ? installer.getName() : "Someone";
        plugin.ceremonyChatListener().beginPrompt(trigger.installer().playerId(), installerName, player,
                template.get(), trigger.liegeGroupId());
    }

    private CeremonyTrigger findByLocation(CeremonyTriggerLocation location) {
        for (CeremonyTrigger trigger : plugin.ceremonyTriggerCache().values()) {
            if (trigger.location().equals(location)) {
                return trigger;
            }
        }
        return null;
    }

    private static boolean isPlateOrButton(Block block) {
        return Tag.PRESSURE_PLATES.isTagged(block.getType()) || Tag.BUTTONS.isTagged(block.getType());
    }
}
