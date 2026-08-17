package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.Banishment;
import com.google.gmail.philbgarner.oathbound.bounty.Bounty;
import com.google.gmail.philbgarner.oathbound.bounty.BountyStatus;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTargeting;
import com.google.gmail.philbgarner.oathbound.bounty.ReturnLocation;
import com.google.gmail.philbgarner.oathbound.bukkit.BountyHeadItems;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/** On a qualifying kill (the victim has a matching ACTIVE bounty on their head), forces a tagged player
 * head to drop and starts/extends the victim's banishment sentence. Deliberately separate from {@link
 * DeathTrackingListener} - that one feeds the generic, kill-attribution-free {@code DeathTracker} used by
 * ordinary Oath kill-count clauses; this one is bounty-specific and does need to identify a victim's
 * <em>active bounty</em>, though never who did the killing - reusing the master plan's own reasoning for
 * sidestepping last-hit tracking entirely via head-return fulfillment. */
public final class BountyKillListener implements Listener {

    private final OathboundPlugin plugin;

    public BountyKillListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        PlayerRef victimRef = new PlayerRef(victim.getUniqueId());

        Optional<Bounty> matched = plugin.bountyCache().values().stream()
                .filter(bounty -> bounty.status() == BountyStatus.ACTIVE && bounty.remainingQuantity() > 0)
                .filter(bounty -> BountyTargeting.matches(bounty.target(), victimRef,
                        id -> Optional.ofNullable(plugin.groupCache().get(id))))
                .min(Comparator.comparing(Bounty::placedAt));
        if (matched.isEmpty()) {
            return;
        }
        Bounty bounty = matched.get();

        event.getDrops().add(BountyHeadItems.build(plugin, victim, bounty.id()));

        Currency defaultCurrency = plugin.economyService().defaultCurrency();
        Map<Currency, Long> nextHeadReward = plugin.bountyService().previewNextHeadReward(bounty);
        long bountyAmountPaid = nextHeadReward.getOrDefault(defaultCurrency, 0L);

        Instant now = Instant.now();
        ReturnLocation returnLocation = new ReturnLocation(victim.getWorld().getUID(), victim.getLocation().getX(),
                victim.getLocation().getY(), victim.getLocation().getZ(), victim.getLocation().getYaw(),
                victim.getLocation().getPitch());
        Optional<Banishment> existing = plugin.banishmentCache().values().stream()
                .filter(b -> b.player().equals(victimRef))
                .findFirst();
        Banishment banishment = plugin.banishmentService().beginOrExtend(
                existing, victimRef, bounty.id(), bountyAmountPaid, now, returnLocation);
        plugin.banishmentCache().put(banishment.id(), banishment);
        plugin.persistBanishmentAsync(banishment);
        victim.sendMessage("A bounty was on your head - you've been banished to the End until your sentence is served.");
    }
}
