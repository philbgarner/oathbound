package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerability;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerabilityTier;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;

/** Login nudge for altars owned (directly, or via group membership) by the joining player that are
 * Decaying or Critical - mirrors the existing unclaimed-escrow login-nudge shape in {@code OathBuilderListener}. */
public final class AltarWarningListener implements Listener {

    private final OathboundPlugin plugin;

    public AltarWarningListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef joined = new PlayerRef(event.getPlayer().getUniqueId());
        Instant now = Instant.now();
        int decayDays = plugin.oathboundConfig().altarDecayDays();

        for (Altar altar : plugin.altarCache().values()) {
            if (!ownedByOrMemberOf(altar.owner(), joined)) {
                continue;
            }
            long power = altar.currentPower(now, decayDays);
            AltarVulnerabilityTier tier = AltarVulnerability.classify(power,
                    plugin.oathboundConfig().altarCriticalThreshold(), plugin.oathboundConfig().altarDecayingThreshold());
            if (tier == AltarVulnerabilityTier.DECAYING) {
                event.getPlayer().sendMessage("...the sacrifice grows cold at one of your altars...");
            } else if (tier == AltarVulnerabilityTier.CRITICAL) {
                event.getPlayer().sendMessage("One of your altars has gone CRITICAL - it is fully raidable until topped up again!");
            }
        }
    }

    private boolean ownedByOrMemberOf(EntityRef owner, PlayerRef player) {
        if (owner instanceof PlayerRef ownerPlayer) {
            return ownerPlayer.equals(player);
        }
        if (owner instanceof ProtectionGroupRef groupRef) {
            ProtectionGroup group = plugin.groupCache().get(groupRef.groupId());
            return group != null && group.members().stream().anyMatch(member -> member.player().equals(player));
        }
        return false;
    }
}
