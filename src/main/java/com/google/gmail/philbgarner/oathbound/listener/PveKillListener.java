package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.PveContractDefinition;
import com.google.gmail.philbgarner.oathbound.bounty.PveContractProgress;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;

/** Feeds qualifying mob kills into any standing PvE contract the killer has already accepted - unlike
 * bounty head-return fulfillment, PvE contracts use native Bukkit kill-attribution
 * ({@code EntityDeathEvent.getEntity().getKiller()}) directly, since there's no reason to sidestep it
 * for a server-owned economy tap the way there is for a PvP bounty. */
public final class PveKillListener implements Listener {

    private final OathboundPlugin plugin;

    public PveKillListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        EntityType mobType = event.getEntityType();
        PlayerRef killerRef = new PlayerRef(killer.getUniqueId());

        for (PveContractDefinition definition : plugin.oathboundConfig().pveContracts()) {
            if (!definition.mobTypeName().equals(mobType.name())) {
                continue;
            }
            Optional<PveContractProgress> progress = plugin.pveContractProgressCache().values().stream()
                    .filter(p -> p.player().equals(killerRef) && p.contractId().equals(definition.id()))
                    .findFirst();
            if (progress.isEmpty()) {
                continue;
            }
            boolean completed = plugin.pveContractService().recordKill(progress.get(), definition);
            plugin.persistPveContractProgressAsync(progress.get());
            if (completed) {
                killer.sendMessage("Standing contract '" + definition.displayName() + "' complete - paid "
                        + definition.reward() + ".");
            }
        }
    }
}
