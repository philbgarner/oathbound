package com.google.gmail.philbgarner.oathbound;

import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarRadiusCalculator;
import com.google.gmail.philbgarner.oathbound.command.OathboundDebugCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundOathCommand;
import com.google.gmail.philbgarner.oathbound.command.OathboundTradeCommand;
import com.google.gmail.philbgarner.oathbound.config.OathboundConfig;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.economy.PlayerBalance;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.gui.OathBuilderListener;
import com.google.gmail.philbgarner.oathbound.gui.TradeGuiListener;
import com.google.gmail.philbgarner.oathbound.listener.AltarConsecrationListener;
import com.google.gmail.philbgarner.oathbound.oath.Ledger;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import com.google.gmail.philbgarner.oathbound.persistence.DataStore;
import com.google.gmail.philbgarner.oathbound.persistence.DataStoreException;
import com.google.gmail.philbgarner.oathbound.persistence.sqlite.SqliteDataStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

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

    private final Map<UUID, ProtectionGroup> groupCache = new ConcurrentHashMap<>();
    private final Map<UUID, Oath> oathCache = new ConcurrentHashMap<>();
    private final Map<UUID, Altar> altarCache = new ConcurrentHashMap<>();
    private final Map<UUID, TradeOffer> tradeOfferCache = new ConcurrentHashMap<>();

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

        oathService = new OathService(ledger);
        economyService = new EconomyService(oathboundConfig.currencies());
        ownershipResolver = new OwnershipResolver(
                id -> Optional.ofNullable(groupCache.get(id)), oathboundConfig.resolverDepthCutoff());
        altarRadiusCalculator = new AltarRadiusCalculator(
                oathboundConfig.altarPowerRadiusScale(), oathboundConfig.altarTierRadiusMultipliers());

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

        getServer().getPluginManager().registerEvents(new AltarConsecrationListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new OathBuilderListener(this), this);

        getLogger().info("Oathbound enabled.");
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
            getLogger().info("Loaded " + groupCache.size() + " group(s), " + oathCache.size() + " oath(s), "
                    + altarCache.size() + " altar(s), " + tradeOfferCache.size() + " trade offer(s) from storage.");
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

    public void persistTradeOfferAsync(TradeOffer offer) {
        persistenceExecutor.submit(() -> {
            try {
                dataStore.saveTradeOffer(offer);
            } catch (DataStoreException e) {
                getLogger().log(Level.SEVERE, "Failed to persist trade offer " + offer.oathId(), e);
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
}
