package com.google.gmail.philbgarner.oathbound;

import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyService;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTemplateDefinition;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTrigger;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomacyService;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticRelation;
import com.google.gmail.philbgarner.oathbound.command.OathboundDebugCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundGroupCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundOathCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundTradeCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundVillagerCommand;
import com.google.gmail.philbgarner.oathbound.config.OathboundConfig;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.economy.PlayerBalance;
import com.google.gmail.philbgarner.oathbound.group.GroupPermission;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.gui.OathBoardGuiListener;
import com.google.gmail.philbgarner.oathbound.gui.OathBuilderListener;
import com.google.gmail.philbgarner.oathbound.gui.TradeGuiListener;
import com.google.gmail.philbgarner.oathbound.gui.ProtectionLockGuiListener;
import com.google.gmail.philbgarner.oathbound.honor.HonorCalculator;
import com.google.gmail.philbgarner.oathbound.honor.HonorService;
import com.google.gmail.philbgarner.oathbound.honor.PlayerHonor;
import com.google.gmail.philbgarner.oathbound.listener.CeremonyChatListener;
import com.google.gmail.philbgarner.oathbound.listener.CeremonyInteractListener;
import com.google.gmail.philbgarner.oathbound.listener.CeremonyTriggerBreakListener;
import com.google.gmail.philbgarner.oathbound.listener.CeremonyTriggerListener;
import com.google.gmail.philbgarner.oathbound.listener.DiplomaticPvpGuardListener;
import com.google.gmail.philbgarner.oathbound.listener.MobKillTrackingListener;
import com.google.gmail.philbgarner.oathbound.listener.DeathTrackingListener;
import com.google.gmail.philbgarner.oathbound.listener.HonorLedgerListener;
import com.google.gmail.philbgarner.oathbound.listener.OathBoardBlockListener;
import com.google.gmail.philbgarner.oathbound.listener.OathBoardBroadcastListener;
import com.google.gmail.philbgarner.oathbound.listener.OathDraftPromptListener;
import com.google.gmail.philbgarner.oathbound.listener.ProtectionLockListener;
import com.google.gmail.philbgarner.oathbound.listener.SealingTableListener;
import com.google.gmail.philbgarner.oathbound.oath.ConditionEngine;
import com.google.gmail.philbgarner.oathbound.oath.DeathTracker;
import com.google.gmail.philbgarner.oathbound.oath.EscrowClaim;
import com.google.gmail.philbgarner.oathbound.oath.EscrowExpiryService;
import com.google.gmail.philbgarner.oathbound.oath.Ledger;
import com.google.gmail.philbgarner.oathbound.oath.ManualConfirmStore;
import com.google.gmail.philbgarner.oathbound.oath.MobKillTracker;
import com.google.gmail.philbgarner.oathbound.oath.NegotiationExpiryService;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import com.google.gmail.philbgarner.oathbound.persistence.DataStore;
import com.google.gmail.philbgarner.oathbound.persistence.DataStoreException;
import com.google.gmail.philbgarner.oathbound.persistence.sqlite.SqliteDataStore;
import com.google.gmail.philbgarner.oathbound.protection.Protection;
import com.google.gmail.philbgarner.oathbound.villager.NpcRole;
import com.google.gmail.philbgarner.oathbound.villager.VillagerNpc;
import com.google.gmail.philbgarner.oathbound.worldguard.GroupRegionLink;
import com.google.gmail.philbgarner.oathbound.worldguard.WorldGuardIntegration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class OathboundPlugin extends JavaPlugin {

    private OathboundConfig oathboundConfig;
    private DataStore dataStore;
    private ExecutorService persistenceExecutor;

    private Ledger ledger;
    private OathService oathService;
    private EconomyService economyService;
    private OwnershipResolver ownershipResolver;
    private DeathTracker deathTracker;
    private MobKillTracker mobKillTracker;
    private ManualConfirmStore manualConfirmStore;
    private ConditionEngine conditionEngine;
    private EscrowExpiryService escrowExpiryService;
    private HonorService honorService;
    private HonorCalculator honorCalculator;
    private NegotiationExpiryService negotiationExpiryService;
    private OathDraftPromptListener oathDraftPromptListener;
    private CeremonyService ceremonyService;
    private CeremonyChatListener ceremonyChatListener;
    private DiplomacyService diplomacyService;

    private final Map<UUID, ProtectionGroup> groupCache = new ConcurrentHashMap<>();
    private final Map<UUID, Oath> oathCache = new ConcurrentHashMap<>();
    private final Map<UUID, TradeOffer> tradeOfferCache = new ConcurrentHashMap<>();
    private final Map<UUID, EscrowClaim> escrowClaimCache = new ConcurrentHashMap<>();
    private final Map<UUID, Protection> protectionCache = new ConcurrentHashMap<>();
    private final Map<UUID, OathBoard> oathBoardCache = new ConcurrentHashMap<>();
    private final Map<UUID, VillagerNpc> villagerNpcCache = new ConcurrentHashMap<>();
    private final Map<UUID, CeremonyTrigger> ceremonyTriggerCache = new ConcurrentHashMap<>();
    private final Map<UUID, GroupRegionLink> groupRegionLinkCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        oathboundConfig = OathboundConfig.load(getConfig(), getDataFolder().toPath());

        persistenceExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Oathbound-Persistence");
            thread.setDaemon(true);
            return thread;
        });

        dataStore = new SqliteDataStore(oathboundConfig.sqliteFile());
        try {
            dataStore.initialize();
        } catch (DataStoreException e) {
            getLogger().log(Level.SEVERE, "Failed to initialize the data store - disabling Oathbound", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ledger = new Ledger();
        ledger.addListener(entry -> persistenceExecutor.submit(() -> {
            try {
                dataStore.appendLedgerEntry(entry);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist ledger entry " + entry.id(), e);
            }
        }));
        honorService = new HonorService();
        honorCalculator = new HonorCalculator(oathboundConfig.honorFulfillGainBase(),
                oathboundConfig.honorBreachLossBase(), oathboundConfig.honorBloodOathMultiplier());
        HonorLedgerListener honorLedgerListener = new HonorLedgerListener(this);
        ledger.addListener(honorLedgerListener::onLedgerEntry);
        OathBoardBroadcastListener oathBoardBroadcastListener = new OathBoardBroadcastListener(this);
        ledger.addListener(oathBoardBroadcastListener::onLedgerEntry);

        deathTracker = new DeathTracker();
        deathTracker.addListener(record -> persistenceExecutor.submit(() -> {
            try {
                dataStore.appendDeathRecord(record);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist death record " + record.id(), e);
            }
        }));
        mobKillTracker = new MobKillTracker();
        mobKillTracker.addListener(record -> persistenceExecutor.submit(() -> {
            try {
                dataStore.appendMobKillRecord(record);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist mob kill record " + record.id(), e);
            }
        }));
        manualConfirmStore = new ManualConfirmStore();

        oathService = new OathService(ledger);
        economyService = new EconomyService(oathboundConfig.currencies());
        ownershipResolver = new OwnershipResolver(
                id -> Optional.ofNullable(groupCache.get(id)), oathboundConfig.resolverDepthCutoff());
        diplomacyService = new DiplomacyService(ownershipResolver);
        conditionEngine = new ConditionEngine(oathService, ownershipResolver, economyService,
                id -> Optional.ofNullable(groupCache.get(id)), deathTracker, mobKillTracker, manualConfirmStore,
                diplomacyService);
        escrowExpiryService = new EscrowExpiryService();
        negotiationExpiryService = new NegotiationExpiryService();
        oathDraftPromptListener = new OathDraftPromptListener(this);
        ceremonyService = new CeremonyService(groupCache::values, ownershipResolver);
        ceremonyChatListener = new CeremonyChatListener(this);

        loadExistingState();

        PluginCommand debugCommand = getCommand("oathbound-debug");
        if (debugCommand != null) {
            OathboundDebugCommand executor = new OathboundDebugCommand(this);
            debugCommand.setExecutor(executor);
            debugCommand.setTabCompleter(executor);
        }

        PluginCommand tradeCommand = getCommand("oathbound-trade");
        if (tradeCommand != null) {
            OathboundTradeCommand executor = new OathboundTradeCommand(this);
            tradeCommand.setExecutor(executor);
            tradeCommand.setTabCompleter(executor);
        }

        PluginCommand oathCommand = getCommand("oathbound-oath");
        if (oathCommand != null) {
            OathboundOathCommand executor = new OathboundOathCommand(this);
            oathCommand.setExecutor(executor);
            oathCommand.setTabCompleter(executor);
        }

        PluginCommand groupCommand = getCommand("oathbound-group");
        if (groupCommand != null) {
            OathboundGroupCommand executor = new OathboundGroupCommand(this);
            groupCommand.setExecutor(executor);
            groupCommand.setTabCompleter(executor);
        }

        for (NpcRole role : NpcRole.values()) {
            PluginCommand villagerCommand = getCommand(role.commandName());
            if (villagerCommand != null) {
                OathboundVillagerCommand executor = new OathboundVillagerCommand(this, role);
                villagerCommand.setExecutor(executor);
                villagerCommand.setTabCompleter(executor);
            }
        }

        getServer().getPluginManager().registerEvents(new TradeGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new OathBuilderListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathTrackingListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionLockListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionLockGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new SealingTableListener(this), this);
        getServer().getPluginManager().registerEvents(oathDraftPromptListener, this);
        getServer().getPluginManager().registerEvents(new OathBoardBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new OathBoardGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new MobKillTrackingListener(this), this);
        getServer().getPluginManager().registerEvents(new CeremonyInteractListener(this), this);
        getServer().getPluginManager().registerEvents(ceremonyChatListener, this);
        if (oathboundConfig.ceremonyBlockTriggersEnabled()) {
            getServer().getPluginManager().registerEvents(new CeremonyTriggerListener(this), this);
            getServer().getPluginManager().registerEvents(new CeremonyTriggerBreakListener(this), this);
            getServer().getScheduler().runTaskTimer(this, this::ambientCeremonyTriggerParticles, 20L, 20L);
        }
        if (oathboundConfig.pvpRestrictToDeclaredWars()) {
            getServer().getPluginManager().registerEvents(new DiplomaticPvpGuardListener(this), this);
        }

        getServer().getScheduler().runTaskTimer(this, this::runConditionEngineTick, 100L, 100L);

        getLogger().info("Oathbound enabled.");
    }

    private void runConditionEngineTick() {
        Instant now = Instant.now();
        try {
            ConditionEngine.TickResult result = conditionEngine.tick(oathCache.values(), now);
            result.changedOaths().forEach(this::persistOathAsync);
            for (EscrowClaim claim : result.newClaims()) {
                escrowClaimCache.put(claim.id(), claim);
                persistEscrowClaimAsync(claim);
            }
            result.changedRelations().forEach(this::persistDiplomaticRelationAsync);
            result.transferredGroups().forEach(this::syncLinkedRegions);
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Condition engine tick failed", e);
        }
        try {
            List<EscrowClaim> expired = escrowExpiryService.sweep(
                    escrowClaimCache.values(), now, oathboundConfig.escrowClaimExpiry());
            expired.forEach(this::persistEscrowClaimAsync);
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Escrow expiry sweep failed", e);
        }
        try {
            List<Oath> expiredNegotiations = negotiationExpiryService.sweep(
                    oathCache.values(), now, oathboundConfig.notaryNegotiationExpiry(), oathService);
            for (Oath oath : expiredNegotiations) {
                persistOathAsync(oath);
                Player creator = Bukkit.getPlayer(oath.parties().get(0).playerId());
                if (creator != null) {
                    creator.sendMessage("Your oath proposal expired unanswered.");
                }
            }
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Negotiation expiry sweep failed", e);
        }
    }

    /** Ambient particle aura at every bound pressure-plate/button trigger whose template
     * {@link CeremonyTemplateDefinition#hasRealStakes()} - the trigger-block half of the "this isn't a
     * zero-stakes item" warning ({@code bukkit.CeremonyItems} handles the item-in-hand half). Runs
     * independently of any player interacting with the block, since the whole point is to warn someone
     * *before* they step on it. */
    private void ambientCeremonyTriggerParticles() {
        for (CeremonyTrigger trigger : ceremonyTriggerCache.values()) {
            CeremonyTemplateDefinition template = oathboundConfig.ceremonyTemplates().stream()
                    .filter(t -> t.id().equals(trigger.templateId()))
                    .findFirst().orElse(null);
            if (template == null || !template.hasRealStakes()) {
                continue;
            }
            World world = Bukkit.getWorld(trigger.location().worldId());
            if (world == null) {
                continue;
            }
            Location location = new Location(world, trigger.location().x() + 0.5,
                    trigger.location().y() + 0.5, trigger.location().z() + 0.5);
            world.spawnParticle(Particle.ENCHANT, location, 6, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Pushes {@code group}'s current {@link GroupPermission#BUILD}-holding members into every
     * WorldGuard region linked to it (see {@code /oathbound-group link-region}) - called whenever a
     * {@code TransferClause} reassigns the group's owner, so a linked region's owner list never drifts
     * out of sync with a real ownership change (the AWOL-owner/heir scenario this exists for). A no-op
     * per link if WorldGuard is absent or the link's world/region can't be resolved right now -
     * {@link WorldGuardIntegration#syncOwners} already treats that as "nothing to sync to," not an error. */
    private void syncLinkedRegions(ProtectionGroup group) {
        List<PlayerRef> buildableMembers = group.members().stream()
                .map(Member::player)
                .filter(player -> group.hasPermission(player, GroupPermission.BUILD))
                .toList();
        for (GroupRegionLink link : groupRegionLinkCache.values()) {
            if (!link.groupId().equals(group.id())) {
                continue;
            }
            World world = Bukkit.getWorld(link.worldId());
            WorldGuardIntegration.syncOwners(world, link.regionId(), buildableMembers);
        }
    }

    @Override
    public void onDisable() {
        if (persistenceExecutor != null) {
            persistenceExecutor.shutdown();
            try {
                if (!persistenceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    getLogger().warning("Persistence tasks did not finish in time during shutdown.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (dataStore != null) {
            try {
                dataStore.close();
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to close the data store cleanly", e);
            }
        }
    }

    private void loadExistingState() {
        try {
            for (ProtectionGroup group : dataStore.loadAllGroups()) {
                groupCache.put(group.id(), group);
            }
            for (Oath oath : dataStore.loadAllOaths()) {
                oathCache.put(oath.id(), oath);
            }
            for (PlayerBalance balance : dataStore.loadAllBalances()) {
                economyService.loadBalance(balance);
            }
            for (TradeOffer offer : dataStore.loadAllTradeOffers()) {
                tradeOfferCache.put(offer.oathId(), offer);
            }
            int deathRecordCount = 0;
            for (var record : dataStore.loadAllDeathRecords()) {
                deathTracker.loadExisting(record);
                deathRecordCount++;
            }
            int mobKillRecordCount = 0;
            for (var record : dataStore.loadAllMobKillRecords()) {
                mobKillTracker.loadExisting(record);
                mobKillRecordCount++;
            }
            int diplomaticRelationCount = 0;
            for (DiplomaticRelation relation : dataStore.loadAllDiplomaticRelations()) {
                diplomacyService.loadExisting(relation);
                diplomaticRelationCount++;
            }
            for (EscrowClaim claim : dataStore.loadAllEscrowClaims()) {
                escrowClaimCache.put(claim.id(), claim);
            }
            for (Protection protection : dataStore.loadAllProtections()) {
                protectionCache.put(protection.id(), protection);
            }
            int honorCount = 0;
            for (PlayerHonor honor : dataStore.loadAllHonor()) {
                honorService.loadHonor(honor);
                honorCount++;
            }
            for (OathBoard board : dataStore.loadAllOathBoards()) {
                oathBoardCache.put(board.id(), board);
            }
            for (VillagerNpc npc : dataStore.loadAllVillagerNpcs()) {
                villagerNpcCache.put(npc.id(), npc);
            }
            for (CeremonyTrigger trigger : dataStore.loadAllCeremonyTriggers()) {
                ceremonyTriggerCache.put(trigger.id(), trigger);
            }
            for (GroupRegionLink link : dataStore.loadAllGroupRegionLinks()) {
                groupRegionLinkCache.put(link.id(), link);
            }
            getLogger().info("Loaded " + groupCache.size() + " group(s), " + oathCache.size() + " oath(s), "
                    + tradeOfferCache.size() + " trade offer(s), "
                    + deathRecordCount + " death record(s), " + escrowClaimCache.size() + " escrow claim(s), "
                    + protectionCache.size() + " protection(s), " + honorCount + " honor record(s), "
                    + oathBoardCache.size() + " oath board(s), "
                    + villagerNpcCache.size() + " villager NPC(s), " + mobKillRecordCount + " mob kill record(s), "
                    + ceremonyTriggerCache.size() + " ceremony trigger(s), "
                    + groupRegionLinkCache.size() + " WorldGuard region link(s), "
                    + diplomaticRelationCount + " diplomatic relation(s) from storage.");
        } catch (DataStoreException e) {
            getLogger().log(Level.SEVERE, "Failed to load persisted state", e);
        }
    }

    public void persistGroupAsync(ProtectionGroup group) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveGroup(group);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist group " + group.id(), e);
            }
        });
    }

    public void persistOathAsync(Oath oath) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveOath(oath);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist oath " + oath.id(), e);
            }
        });
    }

    public void persistBalanceAsync(PlayerBalance balance) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveBalance(balance);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist balance for " + balance.player().playerId(), e);
            }
        });
    }

    public void persistTradeOfferAsync(TradeOffer offer) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveTradeOffer(offer);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist trade offer " + offer.oathId(), e);
            }
        });
    }

    public void persistEscrowClaimAsync(EscrowClaim claim) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveEscrowClaim(claim);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist escrow claim " + claim.id(), e);
            }
        });
    }

    public void persistProtectionAsync(Protection protection) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveProtection(protection);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist protection " + protection.id(), e);
            }
        });
    }

    public void deleteProtectionAsync(UUID id) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.deleteProtection(id);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to delete protection " + id, e);
            }
        });
    }

    public void persistHonorAsync(PlayerHonor honor) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveHonor(honor);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist honor for " + honor.player().playerId(), e);
            }
        });
    }

    public void persistOathBoardAsync(OathBoard board) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveOathBoard(board);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist oath board " + board.id(), e);
            }
        });
    }

    public void deleteOathBoardAsync(UUID id) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.deleteOathBoard(id);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to delete oath board " + id, e);
            }
        });
    }

    public void persistCeremonyTriggerAsync(CeremonyTrigger trigger) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveCeremonyTrigger(trigger);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist ceremony trigger " + trigger.id(), e);
            }
        });
    }

    public void deleteCeremonyTriggerAsync(UUID id) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.deleteCeremonyTrigger(id);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to delete ceremony trigger " + id, e);
            }
        });
    }

    public void persistVillagerNpcAsync(VillagerNpc npc) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveVillagerNpc(npc);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist villager npc " + npc.id(), e);
            }
        });
    }

    public void deleteVillagerNpcAsync(UUID id) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.deleteVillagerNpc(id);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to delete villager npc " + id, e);
            }
        });
    }

    public void persistDiplomaticRelationAsync(DiplomaticRelation relation) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveDiplomaticRelation(relation);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist diplomatic relation " + relation.groupA() + "/" + relation.groupB(), e);
            }
        });
    }

    public void persistGroupRegionLinkAsync(GroupRegionLink link) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveGroupRegionLink(link);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist group region link " + link.id(), e);
            }
        });
    }

    public void deleteGroupRegionLinkAsync(UUID id) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.deleteGroupRegionLink(id);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to delete group region link " + id, e);
            }
        });
    }

    public OathboundConfig oathboundConfig() {
        return oathboundConfig;
    }

    public OathService oathService() {
        return oathService;
    }

    public EconomyService economyService() {
        return economyService;
    }

    public OwnershipResolver ownershipResolver() {
        return ownershipResolver;
    }

    public Ledger ledger() {
        return ledger;
    }

    public DeathTracker deathTracker() {
        return deathTracker;
    }

    public MobKillTracker mobKillTracker() {
        return mobKillTracker;
    }

    public ManualConfirmStore manualConfirmStore() {
        return manualConfirmStore;
    }

    public Map<UUID, ProtectionGroup> groupCache() {
        return groupCache;
    }

    public Map<UUID, Oath> oathCache() {
        return oathCache;
    }

    public Map<UUID, TradeOffer> tradeOfferCache() {
        return tradeOfferCache;
    }

    public Map<UUID, EscrowClaim> escrowClaimCache() {
        return escrowClaimCache;
    }

    public Map<UUID, Protection> protectionCache() {
        return protectionCache;
    }

    public HonorService honorService() {
        return honorService;
    }

    public HonorCalculator honorCalculator() {
        return honorCalculator;
    }

    public OathDraftPromptListener oathDraftPromptListener() {
        return oathDraftPromptListener;
    }

    public Map<UUID, OathBoard> oathBoardCache() {
        return oathBoardCache;
    }

    public Map<UUID, VillagerNpc> villagerNpcCache() {
        return villagerNpcCache;
    }

    public CeremonyService ceremonyService() {
        return ceremonyService;
    }

    public CeremonyChatListener ceremonyChatListener() {
        return ceremonyChatListener;
    }

    public Map<UUID, CeremonyTrigger> ceremonyTriggerCache() {
        return ceremonyTriggerCache;
    }

    public DiplomacyService diplomacyService() {
        return diplomacyService;
    }

    public Map<UUID, GroupRegionLink> groupRegionLinkCache() {
        return groupRegionLinkCache;
    }
}
