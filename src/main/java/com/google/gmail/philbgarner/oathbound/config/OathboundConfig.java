package com.google.gmail.philbgarner.oathbound.config;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.honor.HonorTiers;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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
    private final long honorFulfillGainBase;
    private final long honorBreachLossBase;
    private final double honorBloodOathMultiplier;
    private final long honorMinForBloodOath;
    private final HonorTiers honorTiers;
    private final PotionEffectType honorBloodOathBreachDebuffEffect;
    private final Duration honorBloodOathBreachDebuffDuration;
    private final int honorBloodOathBreachDebuffAmplifier;
    private final Material sealingTableMaterial;
    private final int notaryPendingOfferCap;
    private final Duration notaryNegotiationExpiry;

    private OathboundConfig(Path sqliteFile, int resolverDepthCutoff, List<Currency> currencies,
                             Material altarCapstoneMaterial, double altarPowerRadiusScale,
                             Map<GroupTier, Double> altarTierRadiusMultipliers, Duration escrowClaimExpiry,
                             Material protectionLockToolMaterial, long honorFulfillGainBase,
                             long honorBreachLossBase, double honorBloodOathMultiplier, long honorMinForBloodOath,
                             HonorTiers honorTiers, PotionEffectType honorBloodOathBreachDebuffEffect,
                             Duration honorBloodOathBreachDebuffDuration, int honorBloodOathBreachDebuffAmplifier,
                             Material sealingTableMaterial, int notaryPendingOfferCap,
                             Duration notaryNegotiationExpiry) {
        this.sqliteFile = sqliteFile;
        this.resolverDepthCutoff = resolverDepthCutoff;
        this.currencies = currencies;
        this.altarCapstoneMaterial = altarCapstoneMaterial;
        this.altarPowerRadiusScale = altarPowerRadiusScale;
        this.altarTierRadiusMultipliers = altarTierRadiusMultipliers;
        this.escrowClaimExpiry = escrowClaimExpiry;
        this.protectionLockToolMaterial = protectionLockToolMaterial;
        this.honorFulfillGainBase = honorFulfillGainBase;
        this.honorBreachLossBase = honorBreachLossBase;
        this.honorBloodOathMultiplier = honorBloodOathMultiplier;
        this.honorMinForBloodOath = honorMinForBloodOath;
        this.honorTiers = honorTiers;
        this.honorBloodOathBreachDebuffEffect = honorBloodOathBreachDebuffEffect;
        this.honorBloodOathBreachDebuffDuration = honorBloodOathBreachDebuffDuration;
        this.honorBloodOathBreachDebuffAmplifier = honorBloodOathBreachDebuffAmplifier;
        this.sealingTableMaterial = sealingTableMaterial;
        this.notaryPendingOfferCap = notaryPendingOfferCap;
        this.notaryNegotiationExpiry = notaryNegotiationExpiry;
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

        long fulfillGainBase = config.getLong("honor.fulfill-gain-base", 10L);
        long breachLossBase = config.getLong("honor.breach-loss-base", 20L);
        double bloodOathMultiplier = config.getDouble("honor.blood-oath-multiplier", 2.0);
        long minForBloodOath = config.getLong("honor.min-honor-for-blood-oath", 0L);

        List<HonorTiers.Tier> tiers = new ArrayList<>();
        ConfigurationSection honorTierSection = config.getConfigurationSection("honor.tiers");
        if (honorTierSection != null) {
            for (String key : honorTierSection.getKeys(false)) {
                tiers.add(new HonorTiers.Tier(Long.parseLong(key), honorTierSection.getString(key)));
            }
        }
        if (tiers.isEmpty()) {
            tiers.addAll(List.of(
                    new HonorTiers.Tier(-1000L, "Faithbroken"),
                    new HonorTiers.Tier(0L, "Unproven"),
                    new HonorTiers.Tier(500L, "Oathkeeper"),
                    new HonorTiers.Tier(2000L, "Renowned")
            ));
        }
        HonorTiers honorTiers = new HonorTiers(tiers);

        String debuffEffectName = config.getString("honor.blood-oath-breach-debuff-effect", "WEAKNESS");
        PotionEffectType debuffEffect = PotionEffectType.getByName(debuffEffectName);
        if (debuffEffect == null) {
            throw new IllegalArgumentException("Unknown honor.blood-oath-breach-debuff-effect: " + debuffEffectName);
        }
        int debuffDurationSeconds = config.getInt("honor.blood-oath-breach-debuff-duration-seconds", 300);
        Duration debuffDuration = Duration.ofSeconds(debuffDurationSeconds);
        int debuffAmplifier = config.getInt("honor.blood-oath-breach-debuff-amplifier", 1);

        String sealingTableName = config.getString("notary.sealing-table-material", "LECTERN");
        Material sealingTableMaterial = Material.matchMaterial(sealingTableName);
        if (sealingTableMaterial == null) {
            throw new IllegalArgumentException("Unknown notary.sealing-table-material: " + sealingTableName);
        }
        int notaryPendingOfferCap = config.getInt("notary.pending-offer-cap-per-player", 10);
        int notaryNegotiationExpiryDays = config.getInt("notary.negotiation-expiry-days", 7);
        Duration notaryNegotiationExpiry = Duration.ofDays(notaryNegotiationExpiryDays);

        return new OathboundConfig(dataFolder.resolve(sqliteFileName), depthCutoff, currencies,
                capstoneMaterial, powerRadiusScale, Map.copyOf(tierMultipliers), escrowClaimExpiry,
                lockToolMaterial, fulfillGainBase, breachLossBase, bloodOathMultiplier, minForBloodOath,
                honorTiers, debuffEffect, debuffDuration, debuffAmplifier,
                sealingTableMaterial, notaryPendingOfferCap, notaryNegotiationExpiry);
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

    public long honorFulfillGainBase() {
        return honorFulfillGainBase;
    }

    public long honorBreachLossBase() {
        return honorBreachLossBase;
    }

    public double honorBloodOathMultiplier() {
        return honorBloodOathMultiplier;
    }

    public long honorMinForBloodOath() {
        return honorMinForBloodOath;
    }

    public HonorTiers honorTiers() {
        return honorTiers;
    }

    public PotionEffectType honorBloodOathBreachDebuffEffect() {
        return honorBloodOathBreachDebuffEffect;
    }

    public Duration honorBloodOathBreachDebuffDuration() {
        return honorBloodOathBreachDebuffDuration;
    }

    public int honorBloodOathBreachDebuffAmplifier() {
        return honorBloodOathBreachDebuffAmplifier;
    }

    public Material sealingTableMaterial() {
        return sealingTableMaterial;
    }

    public int notaryPendingOfferCap() {
        return notaryPendingOfferCap;
    }

    public Duration notaryNegotiationExpiry() {
        return notaryNegotiationExpiry;
    }
}
