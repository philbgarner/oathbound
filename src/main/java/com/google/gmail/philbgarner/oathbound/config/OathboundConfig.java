package com.google.gmail.philbgarner.oathbound.config;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Typed config surface for what's actually built so far. Later phases add their own fields here
 * rather than pre-declaring config for systems that don't exist yet.
 */
public final class OathboundConfig {
    private static final Map<GroupTier, Double> DEFAULT_TIER_RADIUS_MULTIPLIERS = Map.of(
            GroupTier.INDIVIDUAL, 1.0,
            GroupTier.COMPANY, 1.25,
            GroupTier.TOWN, 1.5,
            GroupTier.REGION, 2.0,
            GroupTier.KINGDOM, 3.0
    );

    private final Path sqliteFile;
    private final int resolverDepthCutoff;
    private final List<Currency> currencies;
    private final Material altarCapstoneMaterial;
    private final double altarPowerRadiusScale;
    private final Map<GroupTier, Double> altarTierRadiusMultipliers;
    private final Duration escrowClaimExpiry;
    private final Material protectionLockToolMaterial;

    private OathboundConfig(Path sqliteFile, int resolverDepthCutoff, List<Currency> currencies,
                             Material altarCapstoneMaterial, double altarPowerRadiusScale,
                             Map<GroupTier, Double> altarTierRadiusMultipliers, Duration escrowClaimExpiry,
                             Material protectionLockToolMaterial) {
        this.sqliteFile = sqliteFile;
        this.resolverDepthCutoff = resolverDepthCutoff;
        this.currencies = currencies;
        this.altarCapstoneMaterial = altarCapstoneMaterial;
        this.altarPowerRadiusScale = altarPowerRadiusScale;
        this.altarTierRadiusMultipliers = altarTierRadiusMultipliers;
        this.escrowClaimExpiry = escrowClaimExpiry;
        this.protectionLockToolMaterial = protectionLockToolMaterial;
    }

    public static OathboundConfig load(FileConfiguration config, Path dataFolder) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(dataFolder, "dataFolder");

        String sqliteFileName = config.getString("persistence.sqlite-file", "oathbound.db");
        int depthCutoff = config.getInt("ownership.resolver-depth-cutoff", 10);
        List<String> currencyIds = config.getStringList("economy.currencies");
        if (currencyIds.isEmpty()) {
            currencyIds = List.of("coin");
        }
        List<Currency> currencies = currencyIds.stream().map(Currency::new).toList();

        String capstoneName = config.getString("altar.capstone-material", "OBSIDIAN");
        Material capstoneMaterial = Material.matchMaterial(capstoneName);
        if (capstoneMaterial == null) {
            throw new IllegalArgumentException("Unknown altar.capstone-material: " + capstoneName);
        }
        double powerRadiusScale = config.getDouble("altar.power-radius-scale", 4.0);

        Map<GroupTier, Double> tierMultipliers = new EnumMap<>(DEFAULT_TIER_RADIUS_MULTIPLIERS);
        ConfigurationSection tierSection = config.getConfigurationSection("altar.tier-radius-multiplier");
        if (tierSection != null) {
            Set<String> keys = tierSection.getKeys(false);
            for (String key : keys) {
                tierMultipliers.put(GroupTier.valueOf(key.toUpperCase()), tierSection.getDouble(key));
            }
        }

        int escrowClaimExpiryDays = config.getInt("escrow.claim-expiry-days", 30);
        Duration escrowClaimExpiry = Duration.ofDays(escrowClaimExpiryDays);

        String lockToolName = config.getString("protection.lock-tool-material", "TRIPWIRE_HOOK");
        Material lockToolMaterial = Material.matchMaterial(lockToolName);
        if (lockToolMaterial == null) {
            throw new IllegalArgumentException("Unknown protection.lock-tool-material: " + lockToolName);
        }

        return new OathboundConfig(dataFolder.resolve(sqliteFileName), depthCutoff, currencies,
                capstoneMaterial, powerRadiusScale, Map.copyOf(tierMultipliers), escrowClaimExpiry,
                lockToolMaterial);
    }

    public Path sqliteFile() {
        return sqliteFile;
    }

    public int resolverDepthCutoff() {
        return resolverDepthCutoff;
    }

    public List<Currency> currencies() {
        return currencies;
    }

    public Material altarCapstoneMaterial() {
        return altarCapstoneMaterial;
    }

    public double altarPowerRadiusScale() {
        return altarPowerRadiusScale;
    }

    public Map<GroupTier, Double> altarTierRadiusMultipliers() {
        return altarTierRadiusMultipliers;
    }

    public Duration escrowClaimExpiry() {
        return escrowClaimExpiry;
    }

    public Material protectionLockToolMaterial() {
        return protectionLockToolMaterial;
    }
}
