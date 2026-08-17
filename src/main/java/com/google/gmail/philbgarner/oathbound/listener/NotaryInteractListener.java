package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.Bounty;
import com.google.gmail.philbgarner.oathbound.bounty.BountyStatus;
import com.google.gmail.philbgarner.oathbound.bukkit.BountyHeadItems;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.gui.NotaryMenuGui;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Right-clicking a Villager that backs a {@link com.google.gmail.philbgarner.oathbound.notary.Notary}
 * opens the Notary menu instead of the vanilla trade GUI - unless the interacting player is holding a
 * tagged bounty head, in which case that click is the fulfillment action instead (turning a head in at
 * any Notary, not a dedicated screen). */
public final class NotaryInteractListener implements Listener {

    private final OathboundPlugin plugin;

    public NotaryInteractListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        boolean isNotary = plugin.notaryCache().values().stream()
                .anyMatch(notary -> notary.entityId().equals(villager.getUniqueId()));
        if (!isNotary) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        ItemStack inHand = player.getInventory().getItemInMainHand();
        Optional<UUID> bountyId = BountyHeadItems.readBountyId(plugin, inHand);
        if (bountyId.isPresent()) {
            handleHeadTurnIn(player, bountyId.get());
            return;
        }

        NotaryMenuGui.open(player);
    }

    private void handleHeadTurnIn(Player player, UUID bountyId) {
        Bounty bounty = plugin.bountyCache().get(bountyId);
        if (bounty == null || bounty.status() != BountyStatus.ACTIVE || bounty.remainingQuantity() <= 0) {
            player.sendMessage("That bounty is no longer active - nothing to claim.");
            return;
        }

        PlayerRef finder = new PlayerRef(player.getUniqueId());
        Map<Currency, Long> paid = plugin.bountyService().turnInHead(bounty, finder, Instant.now());
        plugin.persistBountyAsync(bounty);

        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
        player.sendMessage("Head turned in - you were paid " + paid + ". " + bounty.remainingQuantity() + "/"
                + bounty.originalQuantity() + " remaining on this contract.");
    }
}
