package com.google.gmail.philbgarner.oathbound.command;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.gui.EscrowClaimBoardGui;
import com.google.gmail.philbgarner.oathbound.gui.OathBuilderGui;
import com.google.gmail.philbgarner.oathbound.gui.PendingOathBoardGui;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Player-facing entry point for the general-purpose named-party oath builder: 'create' starts a new
 * draft against a named counterparty, 'pending' reviews oaths proposed to you. */
public final class OathboundOathCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("create", "pending", "claim");

    private final OathboundPlugin plugin;

    public OathboundOathCommand(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <create <playerName> [blood]|pending|claim>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> create(player, args);
            case "pending" -> PendingOathBoardGui.open(plugin, player);
            case "claim" -> EscrowClaimBoardGui.open(plugin, player);
            default -> player.sendMessage("Usage: /" + label + " <create <playerName> [blood]|pending|claim>");
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /oathbound-oath create <playerName> [blood]");
            return;
        }
        OfflinePlayer other = Bukkit.getOfflinePlayer(args[1]);
        if (other.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("You can't propose an oath to yourself.");
            return;
        }
        if (!other.hasPlayedBefore() && !other.isOnline()) {
            player.sendMessage("Unknown player: " + args[1]);
            return;
        }
        boolean blood = args.length >= 3 && Boolean.parseBoolean(args[2]);

        PlayerRef selfRef = new PlayerRef(player.getUniqueId());
        PlayerRef otherRef = new PlayerRef(other.getUniqueId());
        Oath oath = plugin.oathService().createDraft(List.of(selfRef, otherRef), blood);
        plugin.oathCache().put(oath.id(), oath);
        plugin.persistOathAsync(oath);

        OathBuilderGui.open(plugin, player, oath.id());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
