package com.google.gmail.philbgarner.oathbound.command;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarRadiusCalculator;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerability;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerabilityTier;
import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.GroupPermission;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.group.Role;
import com.google.gmail.philbgarner.oathbound.honor.PlayerHonor;
import com.google.gmail.philbgarner.oathbound.notary.Notary;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import com.google.gmail.philbgarner.oathbound.oath.OathTransitionException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Minimal command surface to exercise the Phase 1 domain layer in-game before any GUI exists. */
public final class OathboundDebugCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TOP_LEVEL =
            List.of("group", "oath", "ledger", "altar", "honor", "notary", "board");
    private static final List<String> GROUP_SUB = List.of("create", "transfer", "info", "list");
    private static final List<String> OATH_SUB = List.of("create", "addflag", "confirm", "propose", "seal",
            "activate", "fulfill", "breach", "void", "info", "list");
    private static final List<String> ALTAR_SUB = List.of("list", "info");
    private static final List<String> HONOR_SUB = List.of("info", "adjust");
    private static final List<String> NOTARY_SUB = List.of("list", "info", "remove");
    private static final List<String> BOARD_SUB = List.of("list", "info", "remove");

    private final OathboundPlugin plugin;

    public OathboundDebugCommand(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <group|oath|ledger|altar|honor|notary|board> ...");
            return true;
        }
        try {
            switch (args[0].toLowerCase()) {
                case "group" -> handleGroup(player, args);
                case "oath" -> handleOath(player, args);
                case "ledger" -> handleLedger(player, args);
                case "altar" -> handleAltar(player, args);
                case "honor" -> handleHonor(player, args);
                case "notary" -> handleNotary(player, args);
                case "board" -> handleBoard(player, args);
                default -> sender.sendMessage("Unknown top-level command: " + args[0]);
            }
        } catch (OathTransitionException e) {
            sender.sendMessage("Illegal transition: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Error: " + e.getMessage());
        }
        return true;
    }

    // ---- group ----

    private void handleGroup(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug group <create|transfer|info|list> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> groupCreate(sender, args);
            case "transfer" -> groupTransfer(sender, args);
            case "info" -> groupInfo(sender, args);
            case "list" -> groupList(sender);
            default -> sender.sendMessage("Unknown group subcommand: " + args[1]);
        }
    }

    private void groupCreate(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug group create <name> [tier]");
            return;
        }
        String name = args[2];
        GroupTier tier = GroupTier.INDIVIDUAL;
        if (args.length >= 4) {
            try {
                tier = GroupTier.valueOf(args[3].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Unknown tier: " + args[3] + " (expected one of " + List.of(GroupTier.values()) + ")");
                return;
            }
        }
        PlayerRef ownerRef = new PlayerRef(sender.getUniqueId());
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), name, ownerRef, tier);
        Role ownerRole = new Role("Owner", 100, EnumSet.allOf(GroupPermission.class));
        group.addRole(ownerRole);
        group.addMember(new Member(ownerRef, "Owner"));
        plugin.groupCache().put(group.id(), group);
        plugin.persistGroupAsync(group);
        sender.sendMessage("Created group '" + name + "' (" + tier + ") id=" + group.id());
    }

    private void groupTransfer(Player sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("Usage: /oathbound-debug group transfer <groupId> <player|group> <targetNameOrId>");
            return;
        }
        Optional<ProtectionGroup> group = findGroup(args[2]);
        if (group.isEmpty()) {
            sender.sendMessage("No such group: " + args[2]);
            return;
        }
        EntityRef newOwner;
        if (args[3].equalsIgnoreCase("player")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[4]);
            newOwner = new PlayerRef(target.getUniqueId());
        } else if (args[3].equalsIgnoreCase("group")) {
            Optional<ProtectionGroup> targetGroup = findGroup(args[4]);
            if (targetGroup.isEmpty()) {
                sender.sendMessage("No such target group: " + args[4]);
                return;
            }
            newOwner = new ProtectionGroupRef(targetGroup.get().id());
        } else {
            sender.sendMessage("Target type must be 'player' or 'group'");
            return;
        }
        OwnershipResolver resolver = plugin.ownershipResolver();
        resolver.reassignOwner(group.get(), newOwner);
        plugin.persistGroupAsync(group.get());
        sender.sendMessage("Transferred ownership of '" + group.get().name() + "' to " + newOwner);
    }

    private void groupInfo(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug group info <groupId>");
            return;
        }
        Optional<ProtectionGroup> group = findGroup(args[2]);
        if (group.isEmpty()) {
            sender.sendMessage("No such group: " + args[2]);
            return;
        }
        ProtectionGroup g = group.get();
        OwnershipResolver.Resolution resolution = plugin.ownershipResolver().resolveTerminalOwner(g.owner());
        sender.sendMessage("Group " + g.id() + " '" + g.name() + "' tier=" + g.tier());
        sender.sendMessage("  owner=" + g.owner() + " terminal=" + resolution);
        sender.sendMessage("  members=" + g.members());
        sender.sendMessage("  roles=" + g.roles());
    }

    private void groupList(Player sender) {
        if (plugin.groupCache().isEmpty()) {
            sender.sendMessage("No groups.");
            return;
        }
        for (ProtectionGroup group : plugin.groupCache().values()) {
            sender.sendMessage(group.id() + " '" + group.name() + "' (" + group.tier() + ") owner=" + group.owner());
        }
    }

    // ---- oath ----

    private void handleOath(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug oath <create|addflag|propose|seal|activate|fulfill|breach|void|info|list> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> oathCreate(sender, args);
            case "addflag" -> oathAddFlag(sender, args);
            case "confirm" -> oathConfirm(sender, args);
            case "propose" -> oathTransition(sender, args, OathService::propose);
            case "seal" -> oathTransition(sender, args, OathService::seal);
            case "activate" -> oathTransition(sender, args, OathService::activate);
            case "fulfill" -> oathTransition(sender, args, OathService::fulfill);
            case "breach" -> oathTransition(sender, args, OathService::breach);
            case "void" -> oathTransition(sender, args, OathService::voidOath);
            case "info" -> oathInfo(sender, args);
            case "list" -> oathList(sender);
            default -> sender.sendMessage("Unknown oath subcommand: " + args[1]);
        }
    }

    private void oathCreate(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug oath create <otherPlayerName> [blood]");
            return;
        }
        OfflinePlayer other = Bukkit.getOfflinePlayer(args[2]);
        boolean blood = args.length >= 4 && Boolean.parseBoolean(args[3]);
        PlayerRef selfRef = new PlayerRef(sender.getUniqueId());
        PlayerRef otherRef = new PlayerRef(other.getUniqueId());
        Oath oath = plugin.oathService().createDraft(List.of(selfRef, otherRef), blood);
        plugin.oathCache().put(oath.id(), oath);
        plugin.persistOathAsync(oath);
        sender.sendMessage("Created draft oath id=" + oath.id() + " parties=[" + sender.getName() + ", " + args[2] + "] blood=" + blood);
    }

    private void oathAddFlag(Player sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /oathbound-debug oath addflag <oathId> <text...>");
            return;
        }
        Optional<Oath> oath = findOath(args[2]);
        if (oath.isEmpty()) {
            sender.sendMessage("No such oath: " + args[2]);
            return;
        }
        String text = String.join(" ", List.of(args).subList(3, args.length));
        plugin.oathService().addClause(oath.get(), new Clause.CustomFlagClause(text));
        plugin.persistOathAsync(oath.get());
        sender.sendMessage("Added CustomFlagClause to " + oath.get().id());
    }

    private void oathConfirm(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug oath confirm <oathId>");
            return;
        }
        Optional<Oath> oath = findOath(args[2]);
        if (oath.isEmpty()) {
            sender.sendMessage("No such oath: " + args[2]);
            return;
        }
        plugin.manualConfirmStore().confirm(oath.get().id(), new PlayerRef(sender.getUniqueId()));
        sender.sendMessage("Recorded your manual confirmation for oath " + oath.get().id()
                + ". It takes effect once a ManualConfirm-gated clause's condition engine tick runs.");
    }

    @FunctionalInterface
    private interface OathTransitionAction {
        void apply(OathService service, Oath oath, PlayerRef actor);
    }

    private void oathTransition(Player sender, String[] args, OathTransitionAction action) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug oath " + args[1] + " <oathId>");
            return;
        }
        Optional<Oath> oath = findOath(args[2]);
        if (oath.isEmpty()) {
            sender.sendMessage("No such oath: " + args[2]);
            return;
        }
        action.apply(plugin.oathService(), oath.get(), new PlayerRef(sender.getUniqueId()));
        plugin.persistOathAsync(oath.get());
        sender.sendMessage("Oath " + oath.get().id() + " is now " + oath.get().state());
    }

    private void oathInfo(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug oath info <oathId>");
            return;
        }
        Optional<Oath> oath = findOath(args[2]);
        if (oath.isEmpty()) {
            sender.sendMessage("No such oath: " + args[2]);
            return;
        }
        Oath o = oath.get();
        sender.sendMessage("Oath " + o.id() + " state=" + o.state() + " blood=" + o.bloodOath());
        sender.sendMessage("  parties=" + o.parties());
        sender.sendMessage("  clauses=" + o.clauses());
        sender.sendMessage("  createdAt=" + o.createdAt() + " sealedAt=" + o.sealedAt() + " resolvedAt=" + o.resolvedAt());
    }

    private void oathList(Player sender) {
        if (plugin.oathCache().isEmpty()) {
            sender.sendMessage("No oaths.");
            return;
        }
        for (Oath oath : plugin.oathCache().values()) {
            sender.sendMessage(oath.id() + " state=" + oath.state() + " parties=" + oath.parties());
        }
    }

    // ---- ledger ----

    private void handleLedger(Player sender, String[] args) {
        List<LedgerEntry> entries;
        if (args.length >= 2 && !args[1].equalsIgnoreCase("recent")) {
            Optional<Oath> oath = findOath(args[1]);
            if (oath.isEmpty()) {
                sender.sendMessage("No such oath: " + args[1]);
                return;
            }
            entries = plugin.ledger().entriesFor(oath.get().id());
        } else {
            int limit = 10;
            if (args.length >= 3) {
                try {
                    limit = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                    // fall through to default limit
                }
            }
            entries = plugin.ledger().recent(limit);
        }
        if (entries.isEmpty()) {
            sender.sendMessage("No ledger entries.");
            return;
        }
        for (LedgerEntry entry : entries) {
            sender.sendMessage(entry.timestamp() + " " + entry.oathId() + " " + entry.fromState() + " -> " + entry.toState());
        }
    }

    // ---- altar ----

    private void handleAltar(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug altar <list|info> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> altarList(sender);
            case "info" -> altarInfo(sender, args);
            default -> sender.sendMessage("Unknown altar subcommand: " + args[1]);
        }
    }

    private void altarList(Player sender) {
        if (plugin.altarCache().isEmpty()) {
            sender.sendMessage("No altars.");
            return;
        }
        Instant now = Instant.now();
        int decayDays = plugin.oathboundConfig().altarDecayDays();
        for (Altar altar : plugin.altarCache().values()) {
            sender.sendMessage(altar.id() + " owner=" + altar.owner()
                    + " power=" + altar.currentPower(now, decayDays) + " loc=" + altar.location());
        }
    }

    private void altarInfo(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug altar info <altarId>");
            return;
        }
        Optional<Altar> altar = findAltar(args[2]);
        if (altar.isEmpty()) {
            sender.sendMessage("No such altar: " + args[2]);
            return;
        }
        Altar a = altar.get();
        Instant now = Instant.now();
        int decayDays = plugin.oathboundConfig().altarDecayDays();
        long power = a.currentPower(now, decayDays);
        GroupTier tier = AltarRadiusCalculator.tierOf(a.owner(), id -> Optional.ofNullable(plugin.groupCache().get(id)));
        int radius = plugin.altarRadiusCalculator().radiusFor(power, tier);
        AltarVulnerabilityTier vulnerability = AltarVulnerability.classify(power,
                plugin.oathboundConfig().altarCriticalThreshold(), plugin.oathboundConfig().altarDecayingThreshold());
        sender.sendMessage("Altar " + a.id() + " owner=" + a.owner());
        sender.sendMessage("  location=" + a.location() + " power=" + power + " radius=" + radius
                + " tier=" + vulnerability);
        sender.sendMessage("  consecratedAt=" + a.consecratedAt() + " cooldownUntil=" + a.cooldownUntil()
                + " lastSacrificeValue=" + a.lastSacrificeValue());
    }

    // ---- honor ----

    private void handleHonor(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug honor <info|adjust> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "info" -> honorInfo(sender, args);
            case "adjust" -> honorAdjust(sender, args);
            default -> sender.sendMessage("Unknown honor subcommand: " + args[1]);
        }
    }

    private void honorInfo(Player sender, String[] args) {
        OfflinePlayer target = args.length >= 3 ? Bukkit.getOfflinePlayer(args[2]) : sender;
        PlayerRef targetRef = new PlayerRef(target.getUniqueId());
        long honor = plugin.honorService().honor(targetRef);
        String title = plugin.oathboundConfig().honorTiers().titleFor(honor);
        sender.sendMessage((args.length >= 3 ? args[2] : sender.getName()) + " has " + honor + " honor (" + title + ").");
    }

    private void honorAdjust(Player sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /oathbound-debug honor adjust <player> <delta>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        long delta;
        try {
            delta = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Delta must be a whole number: " + args[3]);
            return;
        }
        PlayerRef targetRef = new PlayerRef(target.getUniqueId());
        long newHonor = plugin.honorService().adjust(targetRef, delta);
        plugin.persistHonorAsync(new PlayerHonor(targetRef, newHonor));
        sender.sendMessage(args[2] + " now has " + newHonor + " honor.");
    }

    // ---- notary ----

    private void handleNotary(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug notary <list|info|remove> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> notaryList(sender);
            case "info" -> notaryInfo(sender, args);
            case "remove" -> notaryRemove(sender, args);
            default -> sender.sendMessage("Unknown notary subcommand: " + args[1]);
        }
    }

    private void notaryList(Player sender) {
        if (plugin.notaryCache().isEmpty()) {
            sender.sendMessage("No notaries.");
            return;
        }
        for (Notary notary : plugin.notaryCache().values()) {
            sender.sendMessage(notary.id() + " '" + notary.name() + "' owner=" + notary.owner()
                    + " loc=" + notary.location());
        }
    }

    private void notaryInfo(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug notary info <notaryId>");
            return;
        }
        Optional<Notary> notary = findNotary(args[2]);
        if (notary.isEmpty()) {
            sender.sendMessage("No such notary: " + args[2]);
            return;
        }
        Notary n = notary.get();
        sender.sendMessage("Notary " + n.id() + " '" + n.name() + "' owner=" + n.owner());
        sender.sendMessage("  entityId=" + n.entityId() + " location=" + n.location() + " installedAt=" + n.installedAt());
    }

    private void notaryRemove(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug notary remove <notaryId>");
            return;
        }
        Optional<Notary> notary = findNotary(args[2]);
        if (notary.isEmpty()) {
            sender.sendMessage("No such notary: " + args[2]);
            return;
        }
        Notary n = notary.get();
        Entity entity = Bukkit.getEntity(n.entityId());
        if (entity != null) {
            entity.remove();
        }
        plugin.notaryCache().remove(n.id());
        plugin.deleteNotaryAsync(n.id());
        sender.sendMessage("Removed notary '" + n.name() + "'.");
    }

    // ---- board ----

    private void handleBoard(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug board <list|info|remove> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> boardList(sender);
            case "info" -> boardInfo(sender, args);
            case "remove" -> boardRemove(sender, args);
            default -> sender.sendMessage("Unknown board subcommand: " + args[1]);
        }
    }

    private void boardList(Player sender) {
        if (plugin.oathBoardCache().isEmpty()) {
            sender.sendMessage("No oath boards.");
            return;
        }
        for (OathBoard board : plugin.oathBoardCache().values()) {
            sender.sendMessage(board.id() + " scope=" + (board.isCapital() ? "capital" : board.regionalGroup())
                    + " loc=" + board.location());
        }
    }

    private void boardInfo(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug board info <boardId>");
            return;
        }
        Optional<OathBoard> board = findOathBoard(args[2]);
        if (board.isEmpty()) {
            sender.sendMessage("No such board: " + args[2]);
            return;
        }
        OathBoard b = board.get();
        sender.sendMessage("Board " + b.id() + " scope=" + (b.isCapital() ? "capital" : b.regionalGroup()));
        sender.sendMessage("  location=" + b.location() + " installer=" + b.installer() + " installedAt=" + b.installedAt());
    }

    private void boardRemove(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug board remove <boardId>");
            return;
        }
        Optional<OathBoard> board = findOathBoard(args[2]);
        if (board.isEmpty()) {
            sender.sendMessage("No such board: " + args[2]);
            return;
        }
        plugin.oathBoardCache().remove(board.get().id());
        plugin.deleteOathBoardAsync(board.get().id());
        sender.sendMessage("Removed board.");
    }

    // ---- lookups ----

    private Optional<Altar> findAltar(String idString) {
        try {
            return Optional.ofNullable(plugin.altarCache().get(UUID.fromString(idString)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<ProtectionGroup> findGroup(String idString) {
        try {
            return Optional.ofNullable(plugin.groupCache().get(UUID.fromString(idString)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<Oath> findOath(String idString) {
        try {
            return Optional.ofNullable(plugin.oathCache().get(UUID.fromString(idString)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<Notary> findNotary(String idString) {
        try {
            return Optional.ofNullable(plugin.notaryCache().get(UUID.fromString(idString)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<OathBoard> findOathBoard(String idString) {
        try {
            return Optional.ofNullable(plugin.oathBoardCache().get(UUID.fromString(idString)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(TOP_LEVEL, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "group" -> filter(GROUP_SUB, args[1]);
                case "oath" -> filter(OATH_SUB, args[1]);
                case "altar" -> filter(ALTAR_SUB, args[1]);
                case "honor" -> filter(HONOR_SUB, args[1]);
                case "notary" -> filter(NOTARY_SUB, args[1]);
                case "board" -> filter(BOARD_SUB, args[1]);
                default -> new ArrayList<>();
            };
        }
        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
