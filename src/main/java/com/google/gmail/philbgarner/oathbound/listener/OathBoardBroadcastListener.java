package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathBoardEligibility;
import com.google.gmail.philbgarner.oathbound.oath.OathState;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Reacts to every {@link LedgerEntry} the Ledger records - server-wide broadcast is reserved for a
 * witnessed Blood Oath breaking specifically ("special/dramatic formatting, possibly a broadcast," per
 * the design doc); every other postable posting (sealed, fulfilled, non-blood breaks) is only visible
 * by browsing a physical board, not broadcast, to avoid chat spam. Registered via
 * {@code ledger.addListener(...)}, like {@link HonorLedgerListener}.
 */
public final class OathBoardBroadcastListener {

    private final OathboundPlugin plugin;

    public OathBoardBroadcastListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    public void onLedgerEntry(LedgerEntry entry) {
        if (entry.toState() != OathState.BROKEN) {
            return;
        }
        Oath oath = plugin.oathCache().get(entry.oathId());
        if (oath == null || !oath.bloodOath() || !OathBoardEligibility.isPostable(oath, entry.toState())) {
            return;
        }
        String message = "A Blood Oath has been BROKEN between " + partyNames(oath) + "!";
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }

    private String partyNames(Oath oath) {
        StringBuilder names = new StringBuilder();
        for (PlayerRef party : oath.parties()) {
            if (!names.isEmpty()) {
                names.append(" & ");
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(party.playerId());
            names.append(player.getName() != null ? player.getName() : party.playerId().toString());
        }
        return names.toString();
    }
}
