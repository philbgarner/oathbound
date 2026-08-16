package com.google.gmail.philbgarner.oathbound.command;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.villager.NpcRole;
import com.google.gmail.philbgarner.oathbound.villager.VillagerNpc;
import com.google.gmail.philbgarner.oathbound.villager.VillagerNpcLocation;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Player-facing entry point for installing a villager shop NPC: spawns a rooted, invulnerable Villager
 * at the player's current location, skinned for the given {@link NpcRole}. One instance of this class is
 * registered per role (see {@code OathboundPlugin.onEnable()}), so each role gets its own
 * {@code /oathbound-&lt;role&gt;} command while sharing this single implementation. Mirrors
 * {@link OathboundNotaryCommand} - same install shape, no structure/ritual required. */
public final class OathboundVillagerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("install");

    private final OathboundPlugin plugin;
    private final NpcRole role;

    public OathboundVillagerCommand(OathboundPlugin plugin, NpcRole role) {
        this.plugin = plugin;
        this.role = role;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("install")) {
            player.sendMessage("Usage: /" + label + " install <name...>");
            return true;
        }
        install(player, String.join(" ", Arrays.asList(args).subList(1, args.length)));
        return true;
    }

    private void install(Player player, String name) {
        Location location = player.getLocation();
        Villager villager = location.getWorld().spawn(location, Villager.class);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setProfession(role.profession());
        villager.customName(Component.text(name));
        villager.setCustomNameVisible(true);

        VillagerNpcLocation npcLocation = new VillagerNpcLocation(
                location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        VillagerNpc npc = new VillagerNpc(UUID.randomUUID(), villager.getUniqueId(),
                new PlayerRef(player.getUniqueId()), role, name, npcLocation, Instant.now());
        plugin.villagerNpcCache().put(npc.id(), npc);
        plugin.persistVillagerNpcAsync(npc);

        player.sendMessage(role.displayName() + " '" + name + "' installed.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
