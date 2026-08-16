package com.google.gmail.philbgarner.oathbound;

import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarDecaySweepService;
import com.google.gmail.philbgarner.oathbound.altar.AltarRadiusCalculator;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerabilityTier;
import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.command.OathboundDebugCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundNotaryCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundOathCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundTradeCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundVillagerCommand;
import com.google.gmail.philbgarner.oathbound.config.OathboundConfig;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.economy.PlayerBalance;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.gui.AltarSacrificeGuiListener;
import com.google.gmail.philbgarner.oathbound.gui.NotaryMenuGuiListener;
import com.google.gmail.philbgarner.oathbound.gui.OathBoardGuiListener;
import com.google.gmail.philbgarner.oathbound.gui.OathBuilderListener;
import com.google.gmail.philbgarner.oathbound.gui.TradeGuiListener;
import com.google.gmail.philbgarner.oathbound.gui.ProtectionLockGuiListener;
import com.google.gmail.philbgarner.oathbound.gui.VillagerShopGuiListener;
import com.google.gmail.philbgarner.oathbound.honor.HonorCalculator;
import com.google.gmail.philbgarner.oathbound.honor.HonorService;
import com.google.gmail.philbgarner.oathbound.honor.PlayerHonor;
import com.google.gmail.philbgarner.oathbound.listener.AltarConsecrationListener;
import com.google.gmail.philbgarner.oathbound.listener.AltarDesecrationListener;
import com.google.gmail.philbgarner.oathbound.listener.AltarInteractListener;
import com.google.gmail.philbgarner.oathbound.listener.AltarMonsterSpawnGuardListener;
import com.google.gmail.philbgarner.oathbound.listener.AltarWarningListener;
import com.google.gmail.philbgarner.oathbound.listener.ClaimBuildGuardListener;
import com.google.gmail.philbgarner.oathbound.listener.DeathTrackingListener;
import com.google.gmail.philbgarner.oathbound.listener.HonorLedgerListener;
import com.google.gmail.philbgarner.oathbound.listener.NotaryInteractListener;
import com.google.gmail.philbgarner.oathbound.listener.OathBoardBlockListener;
import com.google.gmail.philbgarner.oathbound.listener.OathBoardBroadcastListener;
import com.google.gmail.philbgarner.oathbound.listener.OathDraftPromptListener;
import com.google.gmail.philbgarner.oathbound.listener.ProtectionLockListener;
import com.google.gmail.philbgarner.oathbound.listener.SealingTableListener;
import com.google.gmail.philbgarner.oathbound.listener.VillagerNpcInteractListener;
import com.google.gmail.philbgarner.oathbound.notary.Notary;
import com.google.gmail.philbgarner.oathbound.oath.ConditionEngine;
import com.google.gmail.philbgarner.oathbound.oath.DeathTracker;
import com.google.gmail.philbgarner.oathbound.oath.EscrowClaim;
import com.google.gmail.philbgarner.oathbound.oath.EscrowExpiryService;
import com.google.gmail.philbgarner.oathbound.oath.Ledger;
import com.google.gmail.philbgarner.oathbound.oath.ManualConfirmStore;
import com.google.gmail.philbgarner.oathbound.oath.NegotiationExpiryService;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import com.google.gmail.philbgarner.oathbound.persistence.DataStore;
import com.google.gmail.philbgarner.oathbound.persistence.DataStoreException;
import com.google.gmail.philbgarner.oathbound.persistence.sqlite.SqliteDataStore;
import com.google.gmail.philbgarner.oathbound.protection.Protection;
import com.google.gmail.philbgarner.oathbound.villager.NpcRole;
import com.google.gmail.philbgarner.oathbound.villager.VillagerNpc;
import org.bukkit.Bukkit;
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
    private AltarRadiusCalculator altarRadiusCalculator;
    private DeathTracker deathTracker;
    private ManualConfirmStore manualConfirmStore;
    private ConditionEngine conditionEngine;
    private EscrowExpiryService escrowExpiryService;
    private HonorService honorService;
    private HonorCalculator honorCalculator;
    private NegotiationExpiryService negotiationExpiryService;
    private AltarDecaySweepService altarDecaySweepService;
    private OathDraftPromptListener oathDraftPromptListener;

    private final Map<UUID, ProtectionGroup> groupCache = new ConcurrentHashMap<>();
    private final Map<UUID, Oath> oathCache = new ConcurrentHashMap<>();
    private final Map<UUID, Altar> altarCache = new ConcurrentHashMap<>();
    private final Map<UUID, TradeOffer> tradeOfferCache = new ConcurrentHashMap<>();
    private final Map<UUID, EscrowClaim> escrowClaimCache = new ConcurrentHashMap<>();
    private final Map<UUID, Protection> protectionCache = new ConcurrentHashMap<>();
    private final Map<UUID, Notary> notaryCache = new ConcurrentHashMap<>();
    private final Map<UUID, OathBoard> oathBoardCache = new ConcurrentHashMap<>();
    private final Map<UUID, VillagerNpc> villagerNpcCache = new ConcurrentHashMap<>();

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
        manualConfirmStore = new ManualConfirmStore();

        oathService = new OathService(ledger);
        economyService = new EconomyService(oathboundConfig.currencies());
        ownershipResolver = new OwnershipResolver(
                id -> Optional.ofNullable(groupCache.get(id)), oathboundConfig.resolverDepthCutoff());
        altarRadiusCalculator = new AltarRadiusCalculator(
                oathboundConfig.altarPowerRadiusScale(), oathboundConfig.altarTierRadiusMultipliers());
        conditionEngine = new ConditionEngine(oathService, ownershipResolver, economyService,
                id -> Optional.ofNullable(groupCache.get(id)), deathTracker, manualConfirmStore);
        escrowExpiryService = new EscrowExpiryService();
        negotiationExpiryService = new NegotiationExpiryService();
        altarDecaySweepService = new AltarDecaySweepService();
        oathDraftPromptListener = new OathDraftPromptListener(this);

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

        PluginCommand notaryCommand = getCommand("oathbound-notary");
        if (notaryCommand != null) {
            OathboundNotaryCommand executor = new OathboundNotaryCommand(this);
            notaryCommand.setExecutor(executor);
            notaryCommand.setTabCompleter(executor);
        }

        for (NpcRole role : NpcRole.values()) {
            PluginCommand villagerCommand = getCommand(role.commandName());
            if (villagerCommand != null) {
                OathboundVillagerCommand executor = new OathboundVillagerCommand(this, role);
                villagerCommand.setExecutor(executor);
                villagerCommand.setTabCompleter(executor);
            }
        }

        getServer().getPluginManager().registerEvents(new AltarConsecrationListener(this), this);
        getServer().getPluginManager().registerEvents(new AltarInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new AltarDesecrationListener(this), this);
        getServer().getPluginManager().registerEvents(new AltarMonsterSpawnGuardListener(this), this);
        getServer().getPluginManager().registerEvents(new AltarWarningListener(this), this);
        getServer().getPluginManager().registerEvents(new AltarSacrificeGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new OathBuilderListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathTrackingListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionLockListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionLockGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ClaimBuildGuardListener(this), this);
        getServer().getPluginManager().registerEvents(new NotaryInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new SealingTableListener(this), this);
        getServer().getPluginManager().registerEvents(new NotaryMenuGuiListener(this), this);
        getServer().getPluginManager().registerEvents(oathDraftPromptListener, this);
        getServer().getPluginManager().registerEvents(new OathBoardBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new OathBoardGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new VillagerNpcInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new VillagerShopGuiListener(this), this);

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
        try {
            List<AltarDecaySweepService.TierCrossing> crossings = altarDecaySweepService.sweep(
                    altarCache.values(), now, oathboundConfig.altarDecayDays(),
                    oathboundConfig.altarCriticalThreshold(), oathboundConfig.altarDecayingThreshold());
            for (AltarDecaySweepService.TierCrossing crossing : crossings) {
                persistAltarAsync(crossing.altar());
                if (crossing.to() == AltarVulnerabilityTier.DECAYING || crossing.to() == AltarVulnerabilityTier.CRITICAL) {
                    notifyAltarOwnersIfOnline(crossing.altar(), crossing.to());
                }
            }
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Altar decay sweep failed", e);
        }
    }

    private void notifyAltarOwnersIfOnline(Altar altar, AltarVulnerabilityTier tier) {
        String message = tier == AltarVulnerabilityTier.CRITICAL
                ? "One of your altars has gone CRITICAL - it is fully raidable until topped up again!"
                : "...the sacrifice grows cold at one of your altars...";
        EntityRef owner = altar.owner();
        if (owner instanceof PlayerRef playerRef) {
            Player player = Bukkit.getPlayer(playerRef.playerId());
            if (player != null) {
                player.sendMessage(message);
            }
        } else if (owner instanceof ProtectionGroupRef groupRef) {
            ProtectionGroup group = groupCache.get(groupRef.groupId());
            if (group == null) {
                return;
            }
            for (Member member : group.members()) {
                Player player = Bukkit.getPlayer(member.player().playerId());
                if (player != null) {
                    player.sendMessage(message);
                }
            }
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
            for (Altar altar : dataStore.loadAllAltars()) {
                altarCache.put(altar.id(), altar);
            }
            for (TradeOffer offer : dataStore.loadAllTradeOffers()) {
                tradeOfferCache.put(offer.oathId(), offer);
            }
            int deathRecordCount = 0;
            for (var record : dataStore.loadAllDeathRecords()) {
                deathTracker.loadExisting(record);
                deathRecordCount++;
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
            for (Notary notary : dataStore.loadAllNotaries()) {
                notaryCache.put(notary.id(), notary);
            }
            for (OathBoard board : dataStore.loadAllOathBoards()) {
                oathBoardCache.put(board.id(), board);
            }
            for (VillagerNpc npc : dataStore.loadAllVillagerNpcs()) {
                villagerNpcCache.put(npc.id(), npc);
            }
            getLogger().info("Loaded " + groupCache.size() + " group(s), " + oathCache.size() + " oath(s), "
                    + altarCache.size() + " altar(s), " + tradeOfferCache.size() + " trade offer(s), "
                    + deathRecordCount + " death record(s), " + escrowClaimCache.size() + " escrow claim(s), "
                    + protectionCache.size() + " protection(s), " + honorCount + " honor record(s), "
                    + notaryCache.size() + " notary/notaries, " + oathBoardCache.size() + " oath board(s), "
                    + villagerNpcCache.size() + " villager shop NPC(s) from storage.");
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

    public void persistAltarAsync(Altar altar) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveAltar(altar);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist altar " + altar.id(), e);
            }
        });
    }

    public void deleteAltarAsync(UUID altarId) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.deleteAltar(altarId);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to delete altar " + altarId, e);
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

    public void persistNotaryAsync(Notary notary) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveNotary(notary);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist notary " + notary.id(), e);
            }
        });
    }

    public void deleteNotaryAsync(UUID id) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.deleteNotary(id);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to delete notary " + id, e);
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

    public AltarRadiusCalculator altarRadiusCalculator() {
        return altarRadiusCalculator;
    }

    public Ledger ledger() {
        return ledger;
    }

    public DeathTracker deathTracker() {
        return deathTracker;
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

    public Map<UUID, Altar> altarCache() {
        return altarCache;
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

    public Map<UUID, Notary> notaryCache() {
        return notaryCache;
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
}
