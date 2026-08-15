package com.google.gmail.philbgarner.oathbound.command;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.notary.Notary;
import com.google.gmail.philbgarner.oathbound.notary.NotaryLocation;
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

/** Player-facing entry point for installing a Notary NPC: spawns a rooted, invulnerable Villager at
 * the player's current location. No structure/ritual is defined for this in the design doc (unlike the
 * Altar's barrel+capstone+candle), so a command is the most honest MVP installation trigger. */
public final class OathboundNotaryCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("install");

    private final OathboundPlugin plugin;

    public OathboundNotaryCommand(OathboundPlugin plugin) {
        this.plugin = plugin;
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
        villager.customName(Component.text(name));
        villager.setCustomNameVisible(true);

        NotaryLocation notaryLocation = new NotaryLocation(
                location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Notary notary = new Notary(UUID.randomUUID(), villager.getUniqueId(),
                new PlayerRef(player.getUniqueId()), name, notaryLocation, Instant.now());
        plugin.notaryCache().put(notary.id(), notary);
        plugin.persistNotaryAsync(notary);

        player.sendMessage("Notary '" + name + "' installed.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
