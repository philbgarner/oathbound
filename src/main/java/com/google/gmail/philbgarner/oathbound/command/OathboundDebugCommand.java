package com.google.gmail.philbgarner.oathbound.command;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarRadiusCalculator;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerability;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerabilityTier;
import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.bounty.Banishment;
import com.google.gmail.philbgarner.oathbound.bounty.Bounty;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTarget;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTargeting;
import com.google.gmail.philbgarner.oathbound.bounty.HeatCalculator;
import com.google.gmail.philbgarner.oathbound.bukkit.CeremonyItems;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTemplateDefinition;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTrigger;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticState;
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
import com.google.gmail.philbgarner.oathbound.oath.Condition;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import com.google.gmail.philbgarner.oathbound.oath.OathTransitionException;
import com.google.gmail.philbgarner.oathbound.villager.VillagerNpc;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
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
            List.of("group", "oath", "ledger", "altar", "honor", "notary", "board", "villager", "bounty",
                    "banishment", "ceremony", "diplomacy");
    private static final List<String> GROUP_SUB = List.of("create", "transfer", "info", "list");
    private static final List<String> OATH_SUB = List.of("create", "addflag", "adddiplomacy", "confirm", "propose",
            "seal", "activate", "fulfill", "breach", "void", "info", "list");
    private static final List<String> ALTAR_SUB = List.of("list", "info");
    private static final List<String> HONOR_SUB = List.of("info", "adjust");
    private static final List<String> NOTARY_SUB = List.of("list", "info", "remove");
    private static final List<String> BOARD_SUB = List.of("list", "info", "remove");
    private static final List<String> VILLAGER_SUB = List.of("list", "info", "remove");
    private static final List<String> BOUNTY_SUB = List.of("list", "info", "cancel", "heat");
    private static final List<String> BANISHMENT_SUB = List.of("list", "info", "release", "set-pen");
    private static final List<String> CEREMONY_SUB = List.of("give", "list", "triggers");
    private static final List<String> DIPLOMACY_SUB = List.of("declare-war", "info", "list");

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
            sender.sendMessage("Usage: /" + label + " <group|oath|ledger|altar|honor|notary|board|villager> ...");
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
                case "villager" -> handleVillager(player, args);
                case "bounty" -> handleBounty(player, args);
                case "banishment" -> handleBanishment(player, args);
                case "ceremony" -> handleCeremony(player, args);
                case "diplomacy" -> handleDiplomacy(player, args);
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
            sender.sendMessage("Usage: /oathbound-debug oath <create|addflag|adddiplomacy|addbanishmentrelease|propose|seal|activate|fulfill|breach|void|info|list> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> oathCreate(sender, args);
            case "addflag" -> oathAddFlag(sender, args);
            case "adddiplomacy" -> oathAddDiplomacy(sender, args);
            case "addbanishmentrelease" -> oathAddBanishmentRelease(sender, args);
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

    private void oathAddDiplomacy(Player sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage("Usage: /oathbound-debug oath adddiplomacy <oathId> <groupA> <groupB> <war|peace|alliance>");
            return;
        }
        Optional<Oath> oath = findOath(args[2]);
        if (oath.isEmpty()) {
            sender.sendMessage("No such oath: " + args[2]);
            return;
        }
        Optional<ProtectionGroup> groupA = findGroup(args[3]);
        Optional<ProtectionGroup> groupB = findGroup(args[4]);
        if (groupA.isEmpty() || groupB.isEmpty()) {
            sender.sendMessage("No such group: " + (groupA.isEmpty() ? args[3] : args[4]));
            return;
        }
        DiplomaticState newState;
        try {
            newState = DiplomaticState.valueOf(args[5].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("State must be one of: war, peace, alliance");
            return;
        }
        if (newState == DiplomaticState.NEUTRAL) {
            sender.sendMessage("A treaty can't declare neutrality - that's only the default for a pair with no relation.");
            return;
        }

        Optional<ProtectionGroup> rootA = resolveDiplomaticRoot(groupA.get());
        Optional<ProtectionGroup> rootB = resolveDiplomaticRoot(groupB.get());
        if (rootA.isEmpty() || rootB.isEmpty()) {
            sender.sendMessage("Could not resolve a root group for diplomacy.");
            return;
        }
        Optional<String> tierError = diplomaticTierError(rootA.get(), rootB.get());
        if (tierError.isPresent()) {
            sender.sendMessage(tierError.get());
            return;
        }
        PlayerRef actorRef = new PlayerRef(sender.getUniqueId());
        boolean authorized = rootA.get().hasPermission(actorRef, GroupPermission.ACCEPT_ON_BEHALF)
                || rootB.get().hasPermission(actorRef, GroupPermission.ACCEPT_ON_BEHALF);
        if (!authorized) {
            sender.sendMessage("You don't hold ACCEPT_ON_BEHALF on " + rootA.get().name() + " or " + rootB.get().name()
                    + " - diplomatic relations belong to the most senior group in your chain, not any vassal.");
            return;
        }

        Clause.DiplomacyClause clause = new Clause.DiplomacyClause(new ProtectionGroupRef(groupA.get().id()),
                new ProtectionGroupRef(groupB.get().id()), newState, new Condition.Immediate());
        plugin.oathService().addClause(oath.get(), clause);
        plugin.persistOathAsync(oath.get());
        sender.sendMessage("Added a treaty clause to " + oath.get().id() + ": " + groupA.get().name() + " <-> "
                + groupB.get().name() + " becomes " + newState + " once sealed.");
    }

    /** Diplomatic authority belongs to the most senior group in an ownership chain, not any vassal under
     * it - every diplomacy command resolves through this before checking tier or permissions. */
    private Optional<ProtectionGroup> resolveDiplomaticRoot(ProtectionGroup group) {
        UUID rootId = plugin.diplomacyService().rootOf(group.id());
        return Optional.ofNullable(plugin.groupCache().get(rootId));
    }

    private Optional<String> diplomaticTierError(ProtectionGroup rootA, ProtectionGroup rootB) {
        if (rootA.tier() != GroupTier.REGION && rootA.tier() != GroupTier.KINGDOM) {
            return Optional.of(rootA.name() + " (the senior-most group in that chain) is only " + rootA.tier()
                    + " tier - only REGION/KINGDOM-tier groups can participate in diplomacy.");
        }
        if (rootB.tier() != GroupTier.REGION && rootB.tier() != GroupTier.KINGDOM) {
            return Optional.of(rootB.name() + " (the senior-most group in that chain) is only " + rootB.tier()
                    + " tier - only REGION/KINGDOM-tier groups can participate in diplomacy.");
        }
        return Optional.empty();
    }

    /** The release-oath hook: an ally negotiates with whoever holds authority over a banishment sentence
     * via the normal propose/seal Oath handshake, then this clause fires once activated - consent already
     * happened at sealing, same as {@link #oathAddDiplomacy}'s treaty clause, so this defaults to
     * {@code Immediate} rather than layering on an extra ManualConfirm. */
    private void oathAddBanishmentRelease(Player sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("Usage: /oathbound-debug oath addbanishmentrelease <oathId> <bannedPlayerName> <hours|full>");
            return;
        }
        Optional<Oath> oath = findOath(args[2]);
        if (oath.isEmpty()) {
            sender.sendMessage("No such oath: " + args[2]);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
        PlayerRef targetRef = new PlayerRef(target.getUniqueId());

        boolean fullRelease = args[4].equalsIgnoreCase("full");
        Duration reduction = Duration.ZERO;
        if (!fullRelease) {
            try {
                reduction = Duration.ofHours(Long.parseLong(args[4]));
            } catch (NumberFormatException e) {
                sender.sendMessage("Reduction must be a whole number of hours, or 'full'.");
                return;
            }
        }

        Clause.BanishmentReleaseClause clause = new Clause.BanishmentReleaseClause(
                targetRef, reduction, fullRelease, new Condition.Immediate());
        plugin.oathService().addClause(oath.get(), clause);
        plugin.persistOathAsync(oath.get());
        sender.sendMessage("Added a banishment release clause to " + oath.get().id() + ": " + args[3]
                + (fullRelease ? " is fully pardoned" : "'s sentence is cut by " + reduction.toHours() + "h")
                + " once sealed.");
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

    // ---- villager ----

    private void handleVillager(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug villager <list|info|remove> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> villagerList(sender);
            case "info" -> villagerInfo(sender, args);
            case "remove" -> villagerRemove(sender, args);
            default -> sender.sendMessage("Unknown villager subcommand: " + args[1]);
        }
    }

    private void villagerList(Player sender) {
        if (plugin.villagerNpcCache().isEmpty()) {
            sender.sendMessage("No villager shop NPCs.");
            return;
        }
        for (VillagerNpc npc : plugin.villagerNpcCache().values()) {
            sender.sendMessage(npc.id() + " '" + npc.name() + "' role=" + npc.role() + " owner=" + npc.owner()
                    + " loc=" + npc.location());
        }
    }

    private void villagerInfo(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug villager info <villagerNpcId>");
            return;
        }
        Optional<VillagerNpc> npc = findVillagerNpc(args[2]);
        if (npc.isEmpty()) {
            sender.sendMessage("No such villager shop NPC: " + args[2]);
            return;
        }
        VillagerNpc n = npc.get();
        sender.sendMessage("Villager " + n.id() + " '" + n.name() + "' role=" + n.role() + " owner=" + n.owner());
        sender.sendMessage("  entityId=" + n.entityId() + " location=" + n.location() + " installedAt=" + n.installedAt());
    }

    private void villagerRemove(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug villager remove <villagerNpcId>");
            return;
        }
        Optional<VillagerNpc> npc = findVillagerNpc(args[2]);
        if (npc.isEmpty()) {
            sender.sendMessage("No such villager shop NPC: " + args[2]);
            return;
        }
        VillagerNpc n = npc.get();
        Entity entity = Bukkit.getEntity(n.entityId());
        if (entity != null) {
            entity.remove();
        }
        plugin.villagerNpcCache().remove(n.id());
        plugin.deleteVillagerNpcAsync(n.id());
        sender.sendMessage("Removed villager shop NPC '" + n.name() + "'.");
    }

    // ---- bounty ----

    private void handleBounty(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug bounty <list|info|cancel|heat> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> bountyList(sender, args);
            case "info" -> bountyInfo(sender, args);
            case "cancel" -> bountyCancel(sender, args);
            case "heat" -> bountyHeat(sender, args);
            default -> sender.sendMessage("Unknown bounty subcommand: " + args[1]);
        }
    }

    private void bountyList(Player sender, String[] args) {
        if (plugin.bountyCache().isEmpty()) {
            sender.sendMessage("No bounties.");
            return;
        }
        Optional<PlayerRef> filterTarget = args.length >= 3
                ? Optional.of(new PlayerRef(Bukkit.getOfflinePlayer(args[2]).getUniqueId()))
                : Optional.empty();
        for (Bounty bounty : plugin.bountyCache().values()) {
            if (filterTarget.isPresent() && !BountyTargeting.matches(bounty.target(), filterTarget.get(),
                    id -> Optional.ofNullable(plugin.groupCache().get(id)))) {
                continue;
            }
            sender.sendMessage(bounty.id() + " placer=" + bounty.placer() + " target=" + bounty.target()
                    + " status=" + bounty.status() + " " + bounty.remainingQuantity() + "/" + bounty.originalQuantity());
        }
    }

    private void bountyInfo(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug bounty info <bountyId>");
            return;
        }
        Optional<Bounty> bounty = findBounty(args[2]);
        if (bounty.isEmpty()) {
            sender.sendMessage("No such bounty: " + args[2]);
            return;
        }
        Bounty b = bounty.get();
        sender.sendMessage("Bounty " + b.id() + " status=" + b.status() + " placer=" + b.placer());
        sender.sendMessage("  target=" + b.target() + " " + b.remainingQuantity() + "/" + b.originalQuantity() + " remaining");
        sender.sendMessage("  reward=" + b.totalReward() + " feePaid=" + b.feePaid() + " placedAt=" + b.placedAt());
    }

    private void bountyCancel(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug bounty cancel <bountyId>");
            return;
        }
        Optional<Bounty> bounty = findBounty(args[2]);
        if (bounty.isEmpty()) {
            sender.sendMessage("No such bounty: " + args[2]);
            return;
        }
        plugin.bountyService().cancel(bounty.get());
        plugin.persistBountyAsync(bounty.get());
        sender.sendMessage("Cancelled bounty " + bounty.get().id() + " and refunded the unpaid remainder.");
    }

    private void bountyHeat(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug bounty heat <player>");
            return;
        }
        PlayerRef targetRef = new PlayerRef(Bukkit.getOfflinePlayer(args[2]).getUniqueId());
        BountyTarget target = new BountyTarget.Solo(targetRef);
        double heat = HeatCalculator.heatOf(target, plugin.bountyCache().values(),
                id -> Optional.ofNullable(plugin.groupCache().get(id)), Instant.now(), plugin.oathboundConfig().bountyHeatDecayWindow());
        long fee = HeatCalculator.feeFor(heat, plugin.oathboundConfig().bountyFeeBase(),
                plugin.oathboundConfig().bountyHeatFeeMultiplier());
        sender.sendMessage(args[2] + " current heat=" + heat + ", next placement fee (before any discount)=" + fee);
    }

    // ---- banishment ----

    private void handleBanishment(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug banishment <list|info|release|set-pen> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list" -> banishmentList(sender);
            case "info" -> banishmentInfo(sender, args);
            case "release" -> banishmentRelease(sender, args);
            case "set-pen" -> banishmentSetPen(sender);
            default -> sender.sendMessage("Unknown banishment subcommand: " + args[1]);
        }
    }

    private void banishmentList(Player sender) {
        if (plugin.banishmentCache().isEmpty()) {
            sender.sendMessage("No banishments.");
            return;
        }
        for (Banishment banishment : plugin.banishmentCache().values()) {
            sender.sendMessage(banishment.id() + " player=" + banishment.player() + " released=" + banishment.released()
                    + " releaseAt=" + banishment.releaseAt());
        }
    }

    private void banishmentInfo(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug banishment info <player>");
            return;
        }
        Optional<Banishment> banishment = findBanishmentForPlayer(args[2]);
        if (banishment.isEmpty()) {
            sender.sendMessage(args[2] + " has no banishment record.");
            return;
        }
        Banishment b = banishment.get();
        sender.sendMessage("Banishment " + b.id() + " player=" + args[2] + " released=" + b.released());
        sender.sendMessage("  servingSince=" + b.servingSince() + " releaseAt=" + b.releaseAt()
                + " triggeringBounty=" + b.triggeringBountyId());
    }

    private void banishmentRelease(Player sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /oathbound-debug banishment release <player>");
            return;
        }
        Optional<Banishment> banishment = findBanishmentForPlayer(args[2]);
        if (banishment.isEmpty()) {
            sender.sendMessage(args[2] + " has no banishment record.");
            return;
        }
        Banishment b = banishment.get();
        plugin.banishmentService().forceRelease(b);
        plugin.persistBanishmentAsync(b);
        Player onlineTarget = Bukkit.getPlayer(b.player().playerId());
        if (onlineTarget != null) {
            onlineTarget.teleport(plugin.toBukkitLocation(b.returnLocation()));
            onlineTarget.sendMessage("An admin has released you from banishment.");
        }
        sender.sendMessage("Released " + args[2] + " from banishment.");
    }

    private void banishmentSetPen(Player sender) {
        plugin.setBanishmentPenAndReload(sender.getLocation());
        sender.sendMessage("Banishment pen set to your current location and saved to config.yml.");
    }

    // ---- ceremony ----

    private void handleCeremony(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug ceremony <give|list|triggers> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "give" -> ceremonyGive(sender, args);
            case "list" -> ceremonyList(sender);
            case "triggers" -> ceremonyTriggers(sender);
            default -> sender.sendMessage("Unknown ceremony subcommand: " + args[1]);
        }
    }

    private void ceremonyGive(Player sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /oathbound-debug ceremony give <templateId> <groupId> [player]");
            return;
        }
        Optional<CeremonyTemplateDefinition> template = plugin.oathboundConfig().ceremonyTemplates().stream()
                .filter(t -> t.id().equals(args[2]))
                .findFirst();
        if (template.isEmpty()) {
            sender.sendMessage("No such ceremony template: " + args[2]);
            return;
        }
        Optional<ProtectionGroup> group = findGroup(args[3]);
        if (group.isEmpty()) {
            sender.sendMessage("No such group: " + args[3]);
            return;
        }
        Player recipient = args.length >= 5 ? Bukkit.getPlayer(args[4]) : sender;
        if (recipient == null) {
            sender.sendMessage("Player not online: " + args[4]);
            return;
        }
        ItemStack item = CeremonyItems.build(plugin, template.get(), group.get().id());
        recipient.getInventory().addItem(item);
        sender.sendMessage("Gave " + recipient.getName() + " a '" + template.get().displayName()
                + "' ceremony item bound to group '" + group.get().name() + "'.");
    }

    private void ceremonyList(Player sender) {
        if (plugin.oathboundConfig().ceremonyTemplates().isEmpty()) {
            sender.sendMessage("No ceremony templates configured.");
            return;
        }
        for (CeremonyTemplateDefinition template : plugin.oathboundConfig().ceremonyTemplates()) {
            sender.sendMessage(template.id() + " '" + template.displayName() + "' clauses=" + template.clauses().size());
        }
    }

    private void ceremonyTriggers(Player sender) {
        if (plugin.ceremonyTriggerCache().isEmpty()) {
            sender.sendMessage("No ceremony triggers bound.");
            return;
        }
        for (CeremonyTrigger trigger : plugin.ceremonyTriggerCache().values()) {
            OfflinePlayer installer = Bukkit.getOfflinePlayer(trigger.installer().playerId());
            String installerName = installer.getName() != null ? installer.getName() : trigger.installer().playerId().toString();
            sender.sendMessage(trigger.id() + " template=" + trigger.templateId() + " installer=" + installerName
                    + " loc=" + trigger.location());
        }
    }

    // ---- diplomacy ----

    private void handleDiplomacy(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /oathbound-debug diplomacy <declare-war|info|list> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "declare-war" -> diplomacyDeclareWar(sender, args);
            case "info" -> diplomacyInfo(sender, args);
            case "list" -> diplomacyList(sender);
            default -> sender.sendMessage("Unknown diplomacy subcommand: " + args[1]);
        }
    }

    private void diplomacyDeclareWar(Player sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /oathbound-debug diplomacy declare-war <groupA> <groupB>");
            return;
        }
        Optional<ProtectionGroup> groupA = findGroup(args[2]);
        Optional<ProtectionGroup> groupB = findGroup(args[3]);
        if (groupA.isEmpty() || groupB.isEmpty()) {
            sender.sendMessage("No such group: " + (groupA.isEmpty() ? args[2] : args[3]));
            return;
        }
        Optional<ProtectionGroup> rootA = resolveDiplomaticRoot(groupA.get());
        Optional<ProtectionGroup> rootB = resolveDiplomaticRoot(groupB.get());
        if (rootA.isEmpty() || rootB.isEmpty()) {
            sender.sendMessage("Could not resolve a root group for diplomacy.");
            return;
        }
        Optional<String> tierError = diplomaticTierError(rootA.get(), rootB.get());
        if (tierError.isPresent()) {
            sender.sendMessage(tierError.get());
            return;
        }
        PlayerRef actorRef = new PlayerRef(sender.getUniqueId());
        if (!rootA.get().hasPermission(actorRef, GroupPermission.ACCEPT_ON_BEHALF)) {
            sender.sendMessage("You don't hold ACCEPT_ON_BEHALF on " + rootA.get().name()
                    + " - relations are inherited from your liege, not declared by a vassal.");
            return;
        }

        DiplomaticState previous = plugin.diplomacyService().currentState(rootA.get().id(), rootB.get().id());
        var relation = plugin.diplomacyService().setState(rootA.get().id(), rootB.get().id(), DiplomaticState.WAR, Instant.now());
        plugin.persistDiplomaticRelationAsync(relation);

        sender.sendMessage(rootA.get().name() + " has declared war on " + rootB.get().name() + ".");
        if (previous == DiplomaticState.PEACE || previous == DiplomaticState.ALLIANCE) {
            long newHonor = plugin.honorService().adjust(actorRef, -plugin.oathboundConfig().diplomacyBetrayalHonorPenalty());
            plugin.persistHonorAsync(new PlayerHonor(actorRef, newHonor));
            sender.sendMessage("Breaking a standing " + previous + " cost you "
                    + plugin.oathboundConfig().diplomacyBetrayalHonorPenalty() + " honor (now " + newHonor + ").");
        }
    }

    private void diplomacyInfo(Player sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /oathbound-debug diplomacy info <groupA> <groupB>");
            return;
        }
        Optional<ProtectionGroup> groupA = findGroup(args[2]);
        Optional<ProtectionGroup> groupB = findGroup(args[3]);
        if (groupA.isEmpty() || groupB.isEmpty()) {
            sender.sendMessage("No such group: " + (groupA.isEmpty() ? args[2] : args[3]));
            return;
        }
        Optional<ProtectionGroup> rootA = resolveDiplomaticRoot(groupA.get());
        Optional<ProtectionGroup> rootB = resolveDiplomaticRoot(groupB.get());
        if (rootA.isEmpty() || rootB.isEmpty()) {
            sender.sendMessage("Could not resolve a root group for diplomacy.");
            return;
        }
        DiplomaticState state = plugin.diplomacyService().currentState(rootA.get().id(), rootB.get().id());
        sender.sendMessage(groupA.get().name() + " (-> " + rootA.get().name() + ") and " + groupB.get().name()
                + " (-> " + rootB.get().name() + ") are: " + state);
    }

    private void diplomacyList(Player sender) {
        if (plugin.diplomacyService().allRelations().isEmpty()) {
            sender.sendMessage("No recorded diplomatic relations.");
            return;
        }
        for (var relation : plugin.diplomacyService().allRelations()) {
            ProtectionGroup groupA = plugin.groupCache().get(relation.groupA());
            ProtectionGroup groupB = plugin.groupCache().get(relation.groupB());
            sender.sendMessage((groupA != null ? groupA.name() : relation.groupA()) + " <-> "
                    + (groupB != null ? groupB.name() : relation.groupB()) + ": " + relation.state()
                    + " (since " + relation.since() + ")");
        }
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

    private Optional<VillagerNpc> findVillagerNpc(String idString) {
        try {
            return Optional.ofNullable(plugin.villagerNpcCache().get(UUID.fromString(idString)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<Bounty> findBounty(String idString) {
        try {
            return Optional.ofNullable(plugin.bountyCache().get(UUID.fromString(idString)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<Banishment> findBanishmentForPlayer(String playerName) {
        PlayerRef playerRef = new PlayerRef(Bukkit.getOfflinePlayer(playerName).getUniqueId());
        return plugin.banishmentCache().values().stream()
                .filter(banishment -> banishment.player().equals(playerRef))
                .findFirst();
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
                case "villager" -> filter(VILLAGER_SUB, args[1]);
                case "bounty" -> filter(BOUNTY_SUB, args[1]);
                case "banishment" -> filter(BANISHMENT_SUB, args[1]);
                case "ceremony" -> filter(CEREMONY_SUB, args[1]);
                case "diplomacy" -> filter(DIPLOMACY_SUB, args[1]);
                default -> new ArrayList<>();
            };
        }
        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
