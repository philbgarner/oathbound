package com.google.gmail.philbgarner.oathbound.command;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.GroupPermission;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.Role;
import com.google.gmail.philbgarner.oathbound.worldguard.GroupRegionLink;
import com.google.gmail.philbgarner.oathbound.worldguard.WorldGuardIntegration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Player-facing entry point for forming a claim - a Company, Town, Region, or Kingdom - and, if
 * WorldGuard is installed, linking one to a WorldGuard region. Before this command existed,
 * {@code /oathbound-debug group create} was the only code path anywhere that ever constructed a
 * {@link ProtectionGroup}; gating the whole debug command surface behind {@code oathbound.debug} (an
 * admin-only permission, for good reason - see the other subcommands there) meant no non-op player could
 * form a group, or even lock a single chest, at all. This command is that onboarding path made permanent
 * and permission-free, mirroring {@link OathboundOathCommand}'s relationship to
 * {@code /oathbound-debug oath}: the debug version still exists for admin/testing use, this one is the
 * real player-facing entry point. */
public final class OathboundGroupCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("create", "link-region");

    private final OathboundPlugin plugin;

    public OathboundGroupCommand(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <create <name> [tier]|link-region <groupId> <regionId>>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> create(player, args);
            case "link-region" -> linkRegion(player, args);
            default -> sender.sendMessage("Usage: /" + label + " <create <name> [tier]|link-region <groupId> <regionId>>");
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /oathbound-group create <name> [tier]");
            return;
        }
        String name = args[1];
        GroupTier tier = GroupTier.INDIVIDUAL;
        if (args.length >= 3) {
            try {
                tier = GroupTier.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("Unknown tier: " + args[2] + " (expected one of " + List.of(GroupTier.values()) + ")");
                return;
            }
        }
        ProtectionGroup group = createGroup(plugin, new PlayerRef(player.getUniqueId()), name, tier);
        player.sendMessage("Founded '" + name + "' (" + tier + ") - you are its Owner. id=" + group.id());
    }

    /** Records a link from {@code groupId} to a same-named WorldGuard region in the player's current
     * world, and immediately pushes the group's current {@link GroupPermission#BUILD}-holding members
     * into that region's WorldGuard owners (if WorldGuard is installed) - see
     * {@code OathboundPlugin.syncLinkedRegions}, which keeps this in sync automatically afterward on
     * every {@code TransferClause}-driven ownership change. Requires {@link GroupPermission#TRANSFER_OWNERSHIP}
     * on the group (the "Owner" role always holds it, so this doubles as an owner-only check without a
     * separate special case). */
    private void linkRegion(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("Usage: /oathbound-group link-region <groupId> <regionId>");
            return;
        }
        Optional<ProtectionGroup> group = findGroup(args[1]);
        if (group.isEmpty()) {
            player.sendMessage("No such group: " + args[1]);
            return;
        }
        PlayerRef actorRef = new PlayerRef(player.getUniqueId());
        if (!group.get().hasPermission(actorRef, GroupPermission.TRANSFER_OWNERSHIP)) {
            player.sendMessage("You need TRANSFER_OWNERSHIP on '" + group.get().name() + "' to link a region to it.");
            return;
        }
        String regionId = args[2];
        GroupRegionLink link = new GroupRegionLink(UUID.randomUUID(), group.get().id(),
                player.getWorld().getUID(), regionId);
        plugin.groupRegionLinkCache().put(link.id(), link);
        plugin.persistGroupRegionLinkAsync(link);

        if (!WorldGuardIntegration.isAvailable()) {
            player.sendMessage("Linked '" + group.get().name() + "' to region '" + regionId
                    + "' - WorldGuard isn't installed, so nothing was synced yet.");
            return;
        }
        List<PlayerRef> buildableMembers = group.get().members().stream()
                .map(Member::player)
                .filter(member -> group.get().hasPermission(member, GroupPermission.BUILD))
                .toList();
        WorldGuardIntegration.syncOwners(player.getWorld(), regionId, buildableMembers);
        player.sendMessage("Linked '" + group.get().name() + "' to region '" + regionId
                + "' and pushed its current members as owners.");
    }

    private Optional<ProtectionGroup> findGroup(String idString) {
        try {
            return Optional.ofNullable(plugin.groupCache().get(UUID.fromString(idString)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Shared with {@code OathboundDebugCommand.groupCreate} - the actual construction logic (new group,
     * an all-permissions "Owner" role, the creator as its sole member, cached and persisted) needs to stay
     * identical between the player-facing and debug entry points rather than drift apart. */
    public static ProtectionGroup createGroup(OathboundPlugin plugin, PlayerRef owner, String name, GroupTier tier) {
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), name, owner, tier);
        Role ownerRole = new Role("Owner", 100, EnumSet.allOf(GroupPermission.class));
        group.addRole(ownerRole);
        group.addMember(new Member(owner, "Owner"));
        plugin.groupCache().put(group.id(), group);
        plugin.persistGroupAsync(group);
        return group;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            String prefix = args[2].toUpperCase();
            return List.of(GroupTier.values()).stream()
                    .map(Enum::name)
                    .filter(t -> t.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
