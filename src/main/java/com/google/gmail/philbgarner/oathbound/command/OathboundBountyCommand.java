package com.google.gmail.philbgarner.oathbound.command;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.Bounty;
import com.google.gmail.philbgarner.oathbound.bounty.BountyStatus;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTargeting;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The always-available "show active bounties" pull command from the master plan, plus a toggle to opt
 * out of the one-time login notice - separate from placement itself, which stays GUI/chat-prompt-only
 * through the Notary, matching how Oaths are never created via a command either. */
public final class OathboundBountyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("list", "notify");

    private final OathboundPlugin plugin;

    public OathboundBountyCommand(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(player, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("notify")) {
            notify(player, args);
            return true;
        }
        player.sendMessage("Usage: /" + label + " [list [player]|notify <on|off>]");
        return true;
    }

    private void list(Player sender, String[] args) {
        OfflinePlayer target = args.length >= 2 ? Bukkit.getOfflinePlayer(args[1]) : sender;
        PlayerRef targetRef = new PlayerRef(target.getUniqueId());

        List<Bounty> active = plugin.bountyCache().values().stream()
                .filter(bounty -> bounty.status() == BountyStatus.ACTIVE)
                .filter(bounty -> BountyTargeting.matches(bounty.target(), targetRef,
                        id -> Optional.ofNullable(plugin.groupCache().get(id))))
                .toList();

        if (active.isEmpty()) {
            sender.sendMessage((args.length >= 2 ? args[1] : "You") + " have no active bounties.");
            return;
        }
        String name = args.length >= 2 ? args[1] : "You";
        sender.sendMessage(name + " " + (args.length >= 2 ? "has" : "have") + " " + active.size() + " active bounty/bounties:");
        for (Bounty bounty : active) {
            OfflinePlayer placer = Bukkit.getOfflinePlayer(bounty.placer().playerId());
            String placerName = placer.getName() != null ? placer.getName() : bounty.placer().playerId().toString();
            sender.sendMessage(" - placed by " + placerName + ", " + bounty.remainingQuantity() + "/"
                    + bounty.originalQuantity() + " remaining, reward " + bounty.totalReward());
        }
    }

    private void notify(Player sender, String[] args) {
        if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage("Usage: /oathbound-bounty notify <on|off>");
            return;
        }
        boolean turnOn = args[1].equalsIgnoreCase("on");
        boolean optedOut = !turnOn;
        if (optedOut) {
            plugin.bountyNotificationOptOuts().add(sender.getUniqueId());
        } else {
            plugin.bountyNotificationOptOuts().remove(sender.getUniqueId());
        }
        plugin.setBountyNotificationOptOutAsync(sender.getUniqueId(), optedOut);
        sender.sendMessage("Bounty login notices are now " + (turnOn ? "on" : "off") + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String option : SUB) {
                if (option.startsWith(args[0].toLowerCase())) {
                    result.add(option);
                }
            }
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("notify")) {
            List<String> result = new ArrayList<>();
            for (String option : List.of("on", "off")) {
                if (option.startsWith(args[1].toLowerCase())) {
                    result.add(option);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
}
