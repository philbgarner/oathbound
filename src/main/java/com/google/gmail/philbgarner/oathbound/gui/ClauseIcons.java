package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/** Renders a {@link Clause} into a display icon + lore, shared by the builder's editable list and the
 * pending-oath detail screen's read-only list. */
final class ClauseIcons {
    private ClauseIcons() {
    }

    static ItemStack icon(Clause clause, Map<UUID, ProtectionGroup> groupCache) {
        return switch (clause) {
            case Clause.TransferClause transfer -> GuiItems.button(Material.NAME_TAG, "Transfer Ownership",
                    "Group: " + groupName(transfer.subjectGroup(), groupCache),
                    "New owner: " + entityName(transfer.target(), groupCache));
            case Clause.CustomFlagClause flag -> GuiItems.button(Material.PAPER, "Custom Flag", flag.text());
            case Clause.KillCountClause killCount -> GuiItems.button(Material.IRON_SWORD, "Kill Count",
                    "Target: " + entityName(killCount.target(), groupCache),
                    "Required kills: " + killCount.quantity());
            case Clause.MobKillClause mobKill -> GuiItems.button(Material.SPAWNER, "Mob Kill",
                    "Obligor: " + playerName(mobKill.obligor()),
                    "Mob: " + mobKill.mobTypeName(),
                    "Required kills: " + mobKill.quantity());
            case Clause.DiplomacyClause diplomacy -> GuiItems.button(Material.WHITE_BANNER, "Treaty",
                    "Groups: " + groupName(diplomacy.groupA(), groupCache) + " <-> " + groupName(diplomacy.groupB(), groupCache),
                    "Becomes: " + diplomacy.newState());
            case Clause.EscrowClause escrow -> GuiItems.button(Material.CHEST, "Escrow", "(not supported here)");
            case Clause.BanishmentReleaseClause release -> GuiItems.button(Material.ENDER_PEARL, "Banishment Release",
                    "Target: " + playerName(release.target()),
                    release.fullRelease() ? "Forgives the entire remaining sentence"
                            : "Reduces sentence by " + release.reduction().toHours() + "h");
        };
    }

    private static String entityName(EntityRef ref, Map<UUID, ProtectionGroup> groupCache) {
        return switch (ref) {
            case PlayerRef playerRef -> playerName(playerRef);
            case ProtectionGroupRef groupRef -> groupName(groupRef, groupCache);
        };
    }

    private static String groupName(ProtectionGroupRef ref, Map<UUID, ProtectionGroup> groupCache) {
        ProtectionGroup group = groupCache.get(ref.groupId());
        return group != null ? group.name() : ref.groupId().toString();
    }

    private static String playerName(PlayerRef ref) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(ref.playerId());
        return player.getName() != null ? player.getName() : ref.playerId().toString();
    }
}
