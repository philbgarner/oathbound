package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticState;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Restricts PvP damage to declared wars - only registered when {@code pvp.restrict-to-declared-wars} is
 * true. Two players can only hurt each other if their group memberships' resolved root relation (see
 * {@code diplomacy.DiplomacyService}) is {@link DiplomaticState#WAR}; a player who isn't a member of any
 * group at all has no chain to resolve, so no relation can ever be WAR for them - always protected. This
 * listener only reads {@code DiplomacyService.currentState}, never writes to it. */
public final class DiplomaticPvpGuardListener implements Listener {

    private final OathboundPlugin plugin;

    public DiplomaticPvpGuardListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player p ? p : null;
        if (victim == null) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Set<UUID> attackerRoots = rootsOf(attacker);
        Set<UUID> victimRoots = rootsOf(victim);
        if (attackerRoots.isEmpty() || victimRoots.isEmpty()) {
            event.setCancelled(true);
            attacker.sendMessage("PvP is restricted to declared wars - joining a kingdom is what unlocks it.");
            return;
        }

        boolean atWar = false;
        for (UUID rootA : attackerRoots) {
            for (UUID rootB : victimRoots) {
                if (rootA.equals(rootB)) {
                    continue;
                }
                if (plugin.diplomacyService().currentState(rootA, rootB) == DiplomaticState.WAR) {
                    atWar = true;
                    break;
                }
            }
            if (atWar) {
                break;
            }
        }

        if (!atWar) {
            event.setCancelled(true);
            attacker.sendMessage("PvP is restricted to declared wars - your groups aren't at war with theirs.");
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private Set<UUID> rootsOf(Player player) {
        PlayerRef playerRef = new PlayerRef(player.getUniqueId());
        Set<UUID> roots = new HashSet<>();
        for (ProtectionGroup group : plugin.groupCache().values()) {
            if (group.findMember(playerRef).isPresent()) {
                roots.add(plugin.diplomacyService().rootOf(group.id()));
            }
        }
        return roots;
    }
}
