package com.google.gmail.philbgarner.oathbound.config;

import com.google.gmail.philbgarner.oathbound.bounty.PveContractDefinition;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyClauseSpec;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTemplateDefinition;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.honor.HonorTiers;
import com.google.gmail.philbgarner.oathbound.villager.NpcRole;
import com.google.gmail.philbgarner.oathbound.villager.VillagerRoleTrades;
import com.google.gmail.philbgarner.oathbound.villager.VillagerTradeOffer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
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
    private final int altarDecayDays;
    private final long altarCriticalThreshold;
    private final long altarDecayingThreshold;
    private final double altarXpLootConversionRate;
    private final Duration altarReconsecrationCooldown;
    private final double altarEnchantmentWeightScale;
    private final double altarRepeatEnchantmentDecay;
    private final long altarDesecrationHonorPenalty;
    private final long altarLootHonorPenalty;
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
    private final Material oathBoardMaterial;
    private final int oathBoardFeedSize;
    private final Map<NpcRole, VillagerRoleTrades> villagerTrades;
    private final long bountyFeeBase;
    private final double bountyHeatFeeMultiplier;
    private final Duration bountyHeatDecayWindow;
    private final int bountyMaxPlacementsPer24h;
    private final Duration bountyBreachDiscountWindow;
    private final double bountyBreachDiscountFraction;
    private final Duration bountyAbandonInactivityThreshold;
    private final long banishmentMinHours;
    private final long banishmentMaxHours;
    private final long banishmentHoursPerCurrencyUnit;
    private final long banishmentStackCapHours;
    private final BanishmentPenSpec banishmentPen;
    private final List<PveContractDefinition> pveContracts;
    private final List<CeremonyTemplateDefinition> ceremonyTemplates;

    private OathboundConfig(Path sqliteFile, int resolverDepthCutoff, List<Currency> currencies,
                             Material altarCapstoneMaterial, double altarPowerRadiusScale,
                             Map<GroupTier, Double> altarTierRadiusMultipliers, int altarDecayDays,
                             long altarCriticalThreshold, long altarDecayingThreshold,
                             double altarXpLootConversionRate, Duration altarReconsecrationCooldown,
                             double altarEnchantmentWeightScale, double altarRepeatEnchantmentDecay,
                             long altarDesecrationHonorPenalty, long altarLootHonorPenalty,
                             Duration escrowClaimExpiry,
                             Material protectionLockToolMaterial, long honorFulfillGainBase,
                             long honorBreachLossBase, double honorBloodOathMultiplier, long honorMinForBloodOath,
                             HonorTiers honorTiers, PotionEffectType honorBloodOathBreachDebuffEffect,
                             Duration honorBloodOathBreachDebuffDuration, int honorBloodOathBreachDebuffAmplifier,
                             Material sealingTableMaterial, int notaryPendingOfferCap,
                             Duration notaryNegotiationExpiry, Material oathBoardMaterial, int oathBoardFeedSize,
                             Map<NpcRole, VillagerRoleTrades> villagerTrades, long bountyFeeBase,
                             double bountyHeatFeeMultiplier, Duration bountyHeatDecayWindow, int bountyMaxPlacementsPer24h,
                             Duration bountyBreachDiscountWindow, double bountyBreachDiscountFraction,
                             Duration bountyAbandonInactivityThreshold, long banishmentMinHours, long banishmentMaxHours,
                             long banishmentHoursPerCurrencyUnit, long banishmentStackCapHours,
                             BanishmentPenSpec banishmentPen, List<PveContractDefinition> pveContracts,
                             List<CeremonyTemplateDefinition> ceremonyTemplates) {
        this.sqliteFile = sqliteFile;
        this.resolverDepthCutoff = resolverDepthCutoff;
        this.currencies = currencies;
        this.altarCapstoneMaterial = altarCapstoneMaterial;
        this.altarPowerRadiusScale = altarPowerRadiusScale;
        this.altarTierRadiusMultipliers = altarTierRadiusMultipliers;
        this.altarDecayDays = altarDecayDays;
        this.altarCriticalThreshold = altarCriticalThreshold;
        this.altarDecayingThreshold = altarDecayingThreshold;
        this.altarXpLootConversionRate = altarXpLootConversionRate;
        this.altarReconsecrationCooldown = altarReconsecrationCooldown;
        this.altarEnchantmentWeightScale = altarEnchantmentWeightScale;
        this.altarRepeatEnchantmentDecay = altarRepeatEnchantmentDecay;
        this.altarDesecrationHonorPenalty = altarDesecrationHonorPenalty;
        this.altarLootHonorPenalty = altarLootHonorPenalty;
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
        this.oathBoardMaterial = oathBoardMaterial;
        this.oathBoardFeedSize = oathBoardFeedSize;
        this.villagerTrades = villagerTrades;
        this.bountyFeeBase = bountyFeeBase;
        this.bountyHeatFeeMultiplier = bountyHeatFeeMultiplier;
        this.bountyHeatDecayWindow = bountyHeatDecayWindow;
        this.bountyMaxPlacementsPer24h = bountyMaxPlacementsPer24h;
        this.bountyBreachDiscountWindow = bountyBreachDiscountWindow;
        this.bountyBreachDiscountFraction = bountyBreachDiscountFraction;
        this.bountyAbandonInactivityThreshold = bountyAbandonInactivityThreshold;
        this.banishmentMinHours = banishmentMinHours;
        this.banishmentMaxHours = banishmentMaxHours;
        this.banishmentHoursPerCurrencyUnit = banishmentHoursPerCurrencyUnit;
        this.banishmentStackCapHours = banishmentStackCapHours;
        this.banishmentPen = banishmentPen;
        this.pveContracts = pveContracts;
        this.ceremonyTemplates = ceremonyTemplates;
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

        int altarDecayDays = config.getInt("altar.decay-days", 5);
        long altarCriticalThreshold = config.getLong("altar.critical-threshold", 10L);
        long altarDecayingThreshold = config.getLong("altar.decaying-threshold", 100L);
        double altarXpLootConversionRate = config.getDouble("altar.xp-loot-conversion-rate", 1.0);
        int altarReconsecrationCooldownSeconds = config.getInt("altar.reconsecration-cooldown-seconds", 300);
        Duration altarReconsecrationCooldown = Duration.ofSeconds(altarReconsecrationCooldownSeconds);
        double altarEnchantmentWeightScale = config.getDouble("altar.enchantment-weight-scale", 10.0);
        double altarRepeatEnchantmentDecay = config.getDouble("altar.repeat-enchantment-decay", 0.5);
        long altarDesecrationHonorPenalty = config.getLong("altar.desecration-honor-penalty", 50L);
        long altarLootHonorPenalty = config.getLong("altar.loot-honor-penalty", 0L);

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

        String oathBoardMaterialName = config.getString("oath-board.material", "OAK_SIGN");
        Material oathBoardMaterial = Material.matchMaterial(oathBoardMaterialName);
        if (oathBoardMaterial == null) {
            throw new IllegalArgumentException("Unknown oath-board.material: " + oathBoardMaterialName);
        }
        int oathBoardFeedSize = config.getInt("oath-board.feed-size", 50);

        Map<NpcRole, VillagerRoleTrades> villagerTrades = new EnumMap<>(NpcRole.class);
        for (NpcRole role : NpcRole.values()) {
            String base = "villagers." + role.configKey();
            List<VillagerTradeOffer> sells = parseTradeOffers(config.getMapList(base + ".sells"));
            List<VillagerTradeOffer> buys = parseTradeOffers(config.getMapList(base + ".buys"));
            villagerTrades.put(role, new VillagerRoleTrades(sells, buys));
        }

        long bountyFeeBase = config.getLong("bounty.fee-base", 100L);
        double bountyHeatFeeMultiplier = config.getDouble("bounty.heat-fee-multiplier", 0.5);
        Duration bountyHeatDecayWindow = Duration.ofHours(config.getInt("bounty.heat-decay-hours", 72));
        int bountyMaxPlacementsPer24h = config.getInt("bounty.max-placements-per-24h", 3);
        Duration bountyBreachDiscountWindow = Duration.ofDays(config.getInt("bounty.breach-discount-window-days", 14));
        double bountyBreachDiscountFraction = config.getDouble("bounty.breach-discount-fraction", 0.5);
        Duration bountyAbandonInactivityThreshold = Duration.ofDays(config.getInt("bounty.abandon-inactivity-days", 30));

        long banishmentMinHours = config.getLong("banishment.min-hours", 1L);
        long banishmentMaxHours = config.getLong("banishment.max-hours", 72L);
        long banishmentHoursPerCurrencyUnit = config.getLong("banishment.hours-per-currency-unit", 50L);
        long banishmentStackCapHours = config.getLong("banishment.stack-cap-hours", 168L);
        BanishmentPenSpec banishmentPen = new BanishmentPenSpec(
                config.getString("banishment.pen.world", "world_the_end"),
                config.getDouble("banishment.pen.x", 0.5),
                config.getDouble("banishment.pen.y", 64.0),
                config.getDouble("banishment.pen.z", 0.5),
                (float) config.getDouble("banishment.pen.yaw", 0.0),
                (float) config.getDouble("banishment.pen.pitch", 0.0));

        List<PveContractDefinition> pveContracts = parsePveContracts(config.getMapList("pve-contracts"));
        List<CeremonyTemplateDefinition> ceremonyTemplates = parseCeremonyTemplates(config.getMapList("ceremony-templates"));

        return new OathboundConfig(dataFolder.resolve(sqliteFileName), depthCutoff, currencies,
                capstoneMaterial, powerRadiusScale, Map.copyOf(tierMultipliers),
                altarDecayDays, altarCriticalThreshold, altarDecayingThreshold, altarXpLootConversionRate,
                altarReconsecrationCooldown, altarEnchantmentWeightScale, altarRepeatEnchantmentDecay,
                altarDesecrationHonorPenalty, altarLootHonorPenalty, escrowClaimExpiry,
                lockToolMaterial, fulfillGainBase, breachLossBase, bloodOathMultiplier, minForBloodOath,
                honorTiers, debuffEffect, debuffDuration, debuffAmplifier,
                sealingTableMaterial, notaryPendingOfferCap, notaryNegotiationExpiry,
                oathBoardMaterial, oathBoardFeedSize, Map.copyOf(villagerTrades),
                bountyFeeBase, bountyHeatFeeMultiplier, bountyHeatDecayWindow, bountyMaxPlacementsPer24h,
                bountyBreachDiscountWindow, bountyBreachDiscountFraction, bountyAbandonInactivityThreshold,
                banishmentMinHours, banishmentMaxHours, banishmentHoursPerCurrencyUnit, banishmentStackCapHours,
                banishmentPen, pveContracts, ceremonyTemplates);
    }

    private static List<PveContractDefinition> parsePveContracts(List<Map<?, ?>> raw) {
        List<PveContractDefinition> result = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            String id = String.valueOf(entry.get("id"));
            String displayName = String.valueOf(entry.get("display-name"));
            String mobName = String.valueOf(entry.get("mob"));
            String mobTypeName;
            try {
                mobTypeName = EntityType.valueOf(mobName.toUpperCase()).name();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown pve-contracts mob: " + mobName, e);
            }
            int quantity = ((Number) entry.get("quantity")).intValue();
            Map<Currency, Long> reward = new java.util.HashMap<>();
            Object rewardRaw = entry.get("reward");
            if (rewardRaw instanceof Map<?, ?> rewardMap) {
                for (Map.Entry<?, ?> rewardEntry : rewardMap.entrySet()) {
                    reward.put(new Currency(String.valueOf(rewardEntry.getKey())),
                            ((Number) rewardEntry.getValue()).longValue());
                }
            }
            result.add(new PveContractDefinition(id, displayName, mobTypeName, quantity, reward));
        }
        return result;
    }

    private static List<CeremonyTemplateDefinition> parseCeremonyTemplates(List<Map<?, ?>> raw) {
        List<CeremonyTemplateDefinition> result = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            String id = String.valueOf(entry.get("id"));
            String displayName = String.valueOf(entry.get("display-name"));
            String itemMaterialName = String.valueOf(entry.get("item-material"));
            if (Material.matchMaterial(itemMaterialName) == null) {
                throw new IllegalArgumentException("Unknown ceremony-templates item-material: " + itemMaterialName);
            }
            String itemDisplayName = String.valueOf(entry.get("item-display-name"));
            List<String> dialogue = stringList(entry.get("dialogue"));
            List<String> confirmPhrases = stringList(entry.get("confirm-phrases"));
            List<String> declinePhrases = stringList(entry.get("decline-phrases"));
            int promptTimeoutSeconds = entry.containsKey("prompt-timeout-seconds")
                    ? ((Number) entry.get("prompt-timeout-seconds")).intValue() : 60;
            boolean bloodOath = Boolean.TRUE.equals(entry.get("blood-oath"));
            List<CeremonyClauseSpec> clauses = parseCeremonyClauses(rawMapList(entry.get("clauses")));
            result.add(new CeremonyTemplateDefinition(id, displayName, itemMaterialName, itemDisplayName,
                    dialogue, confirmPhrases, declinePhrases, promptTimeoutSeconds, bloodOath, clauses));
        }
        return result;
    }

    private static List<CeremonyClauseSpec> parseCeremonyClauses(List<Map<?, ?>> raw) {
        List<CeremonyClauseSpec> result = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            String type = String.valueOf(entry.get("type"));
            result.add(switch (type) {
                case "transfer" -> new CeremonyClauseSpec.TransferSpec(
                        ((Number) entry.get("pvp-death-count")).intValue());
                case "tribute" -> {
                    String materialName = String.valueOf(entry.get("material"));
                    if (Material.matchMaterial(materialName) == null) {
                        throw new IllegalArgumentException("Unknown ceremony-templates tribute material: " + materialName);
                    }
                    yield new CeremonyClauseSpec.TributeSpec(String.valueOf(entry.get("recipient")), materialName,
                            ((Number) entry.get("quantity")).intValue());
                }
                case "mob-kill" -> {
                    String mobName = String.valueOf(entry.get("mob"));
                    String mobTypeName;
                    try {
                        mobTypeName = EntityType.valueOf(mobName.toUpperCase()).name();
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Unknown ceremony-templates mob: " + mobName, e);
                    }
                    yield new CeremonyClauseSpec.MobKillSpec(mobTypeName, ((Number) entry.get("quantity")).intValue());
                }
                case "custom-flag" -> new CeremonyClauseSpec.CustomFlagSpec(String.valueOf(entry.get("text")));
                default -> throw new IllegalArgumentException("Unknown ceremony-templates clause type: " + type);
            });
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> rawMapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return (List<Map<?, ?>>) list;
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static List<VillagerTradeOffer> parseTradeOffers(List<Map<?, ?>> raw) {
        List<VillagerTradeOffer> result = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            String materialName = String.valueOf(entry.get("material"));
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                throw new IllegalArgumentException("Unknown villager trade material: " + materialName);
            }
            long price = ((Number) entry.get("price")).longValue();
            result.add(new VillagerTradeOffer(material, price));
        }
        return result;
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

    public int altarDecayDays() {
        return altarDecayDays;
    }

    public long altarCriticalThreshold() {
        return altarCriticalThreshold;
    }

    public long altarDecayingThreshold() {
        return altarDecayingThreshold;
    }

    public double altarXpLootConversionRate() {
        return altarXpLootConversionRate;
    }

    public Duration altarReconsecrationCooldown() {
        return altarReconsecrationCooldown;
    }

    public double altarEnchantmentWeightScale() {
        return altarEnchantmentWeightScale;
    }

    public double altarRepeatEnchantmentDecay() {
        return altarRepeatEnchantmentDecay;
    }

    public long altarDesecrationHonorPenalty() {
        return altarDesecrationHonorPenalty;
    }

    public long altarLootHonorPenalty() {
        return altarLootHonorPenalty;
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

    public Material oathBoardMaterial() {
        return oathBoardMaterial;
    }

    public int oathBoardFeedSize() {
        return oathBoardFeedSize;
    }

    public Map<NpcRole, VillagerRoleTrades> villagerTrades() {
        return villagerTrades;
    }

    public long bountyFeeBase() {
        return bountyFeeBase;
    }

    public double bountyHeatFeeMultiplier() {
        return bountyHeatFeeMultiplier;
    }

    public Duration bountyHeatDecayWindow() {
        return bountyHeatDecayWindow;
    }

    public int bountyMaxPlacementsPer24h() {
        return bountyMaxPlacementsPer24h;
    }

    public Duration bountyBreachDiscountWindow() {
        return bountyBreachDiscountWindow;
    }

    public double bountyBreachDiscountFraction() {
        return bountyBreachDiscountFraction;
    }

    public Duration bountyAbandonInactivityThreshold() {
        return bountyAbandonInactivityThreshold;
    }

    public long banishmentMinHours() {
        return banishmentMinHours;
    }

    public long banishmentMaxHours() {
        return banishmentMaxHours;
    }

    public long banishmentHoursPerCurrencyUnit() {
        return banishmentHoursPerCurrencyUnit;
    }

    public long banishmentStackCapHours() {
        return banishmentStackCapHours;
    }

    public BanishmentPenSpec banishmentPen() {
        return banishmentPen;
    }

    public List<PveContractDefinition> pveContracts() {
        return pveContracts;
    }

    public List<CeremonyTemplateDefinition> ceremonyTemplates() {
        return ceremonyTemplates;
    }
}
