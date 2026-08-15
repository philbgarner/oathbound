package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.honor.HonorCalculator;
import com.google.gmail.philbgarner.oathbound.honor.PlayerHonor;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/**
 * Reacts to every {@link LedgerEntry} the {@link com.google.gmail.philbgarner.oathbound.oath.Ledger}
 * records, moving Honor on FULFILLED/BROKEN and applying the Blood Oath breach debuff - registered via
 * {@code ledger.addListener(...)}, not a Bukkit {@code Listener}, since it reacts to a domain-layer
 * event rather than a Bukkit one. VOIDED is deliberately a no-op (mutually agreed cancellation is
 * neutral, per the design doc).
 *
 * <p>Honor moves apply to every party of the oath, not just an at-fault party - the domain model has
 * no fault-attribution concept yet (the {@code actor} on a BROKEN transition just means "whoever called
 * {@code breach()}", which today is only ever an admin via the debug command). This should be narrowed
 * once a real breach-detection/reporting flow exists.
 */
public final class HonorLedgerListener {

    private final OathboundPlugin plugin;

    public HonorLedgerListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    public void onLedgerEntry(LedgerEntry entry) {
        if (entry.toState() != OathState.FULFILLED && entry.toState() != OathState.BROKEN) {
            return;
        }
        Oath oath = plugin.oathCache().get(entry.oathId());
        if (oath == null) {
            return;
        }

        boolean fulfilled = entry.toState() == OathState.FULFILLED;
        HonorCalculator calculator = plugin.honorCalculator();
        long delta = fulfilled ? calculator.fulfillGain(oath) : -calculator.breachLoss(oath);

        for (PlayerRef party : oath.parties()) {
            long newHonor = plugin.honorService().adjust(party, delta);
            plugin.persistHonorAsync(new PlayerHonor(party, newHonor));
        }

        if (!fulfilled && oath.bloodOath()) {
            applyBreachDebuff(oath);
        }
    }

    private void applyBreachDebuff(Oath oath) {
        int durationTicks = (int) (plugin.oathboundConfig().honorBloodOathBreachDebuffDuration().toSeconds() * 20L);
        int amplifier = plugin.oathboundConfig().honorBloodOathBreachDebuffAmplifier();
        PotionEffect effect = new PotionEffect(
                plugin.oathboundConfig().honorBloodOathBreachDebuffEffect(), durationTicks, amplifier);

        for (PlayerRef party : oath.parties()) {
            Player online = Bukkit.getPlayer(party.playerId());
            if (online != null) {
                online.addPotionEffect(effect);
                online.sendMessage("Your Blood Oath was broken - you feel the curse take hold.");
            }
        }
    }
}
