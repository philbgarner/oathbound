package com.google.gmail.philbgarner.oathbound.config;

import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyClauseSpec;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTemplateDefinition;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticState;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.honor.HonorTiers;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed config surface for what's actually built so far. Later phases add their own fields here
 * rather than pre-declaring config for systems that don't exist yet.
 */
public final class OathboundConfig {

    private final Path sqliteFile;
    private final int resolverDepthCutoff;
    private final List<Currency> currencies;
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
    private final int notaryPendingOfferCapPerSenderRecipient;
    private final Duration notaryNegotiationExpiry;
    private final Material oathBoardMaterial;
    private final int oathBoardFeedSize;
    private final List<CeremonyTemplateDefinition> ceremonyTemplates;
    private final long diplomacyBetrayalHonorPenalty;
    private final boolean pvpRestrictToDeclaredWars;
    private final boolean ceremonyBlockTriggersEnabled;

    private OathboundConfig(Path sqliteFile, int resolverDepthCutoff, List<Currency> currencies,
                             Duration escrowClaimExpiry,
                             Material protectionLockToolMaterial, long honorFulfillGainBase,
                             long honorBreachLossBase, double honorBloodOathMultiplier, long honorMinForBloodOath,
                             HonorTiers honorTiers, PotionEffectType honorBloodOathBreachDebuffEffect,
                             Duration honorBloodOathBreachDebuffDuration, int honorBloodOathBreachDebuffAmplifier,
                             Material sealingTableMaterial, int notaryPendingOfferCap,
                             int notaryPendingOfferCapPerSenderRecipient,
                             Duration notaryNegotiationExpiry, Material oathBoardMaterial, int oathBoardFeedSize,
                             List<CeremonyTemplateDefinition> ceremonyTemplates, long diplomacyBetrayalHonorPenalty,
                             boolean pvpRestrictToDeclaredWars, boolean ceremonyBlockTriggersEnabled) {
        this.sqliteFile = sqliteFile;
        this.resolverDepthCutoff = resolverDepthCutoff;
        this.currencies = currencies;
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
        this.notaryPendingOfferCapPerSenderRecipient = notaryPendingOfferCapPerSenderRecipient;
        this.notaryNegotiationExpiry = notaryNegotiationExpiry;
        this.oathBoardMaterial = oathBoardMaterial;
        this.oathBoardFeedSize = oathBoardFeedSize;
        this.ceremonyTemplates = ceremonyTemplates;
        this.diplomacyBetrayalHonorPenalty = diplomacyBetrayalHonorPenalty;
        this.pvpRestrictToDeclaredWars = pvpRestrictToDeclaredWars;
        this.ceremonyBlockTriggersEnabled = ceremonyBlockTriggersEnabled;
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
        int notaryPendingOfferCapPerSenderRecipient = config.getInt("notary.pending-offer-cap-per-sender-recipient", 2);
        int notaryNegotiationExpiryDays = config.getInt("notary.negotiation-expiry-days", 7);
        Duration notaryNegotiationExpiry = Duration.ofDays(notaryNegotiationExpiryDays);

        String oathBoardMaterialName = config.getString("oath-board.material", "OAK_SIGN");
        Material oathBoardMaterial = Material.matchMaterial(oathBoardMaterialName);
        if (oathBoardMaterial == null) {
            throw new IllegalArgumentException("Unknown oath-board.material: " + oathBoardMaterialName);
        }
        int oathBoardFeedSize = config.getInt("oath-board.feed-size", 50);

        List<CeremonyTemplateDefinition> ceremonyTemplates = parseCeremonyTemplates(config.getMapList("ceremony-templates"));
        long diplomacyBetrayalHonorPenalty = config.getLong("diplomacy.betrayal-honor-penalty", 20L);
        boolean pvpRestrictToDeclaredWars = config.getBoolean("pvp.restrict-to-declared-wars", true);
        boolean ceremonyBlockTriggersEnabled = config.getBoolean("ceremony-block-triggers-enabled", true);

        return new OathboundConfig(dataFolder.resolve(sqliteFileName), depthCutoff, currencies,
                escrowClaimExpiry,
                lockToolMaterial, fulfillGainBase, breachLossBase, bloodOathMultiplier, minForBloodOath,
                honorTiers, debuffEffect, debuffDuration, debuffAmplifier,
                sealingTableMaterial, notaryPendingOfferCap, notaryPendingOfferCapPerSenderRecipient, notaryNegotiationExpiry,
                oathBoardMaterial, oathBoardFeedSize,
                ceremonyTemplates, diplomacyBetrayalHonorPenalty,
                pvpRestrictToDeclaredWars, ceremonyBlockTriggersEnabled);
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
            int promptTimeoutSeconds = entry.containsKey("prompt-timeout-seconds")
                    ? ((Number) entry.get("prompt-timeout-seconds")).intValue() : 60;
            boolean bloodOath = Boolean.TRUE.equals(entry.get("blood-oath"));
            List<CeremonyClauseSpec> clauses = parseCeremonyClauses(rawMapList(entry.get("clauses")));
            result.add(new CeremonyTemplateDefinition(id, displayName, itemMaterialName, itemDisplayName,
                    dialogue, promptTimeoutSeconds, bloodOath, clauses));
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
                case "diplomacy" -> {
                    String stateName = String.valueOf(entry.get("state"));
                    DiplomaticState diplomaticState;
                    try {
                        diplomaticState = DiplomaticState.valueOf(stateName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Unknown ceremony-templates diplomacy state: " + stateName, e);
                    }
                    yield new CeremonyClauseSpec.DiplomacySpec(diplomaticState);
                }
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

    public Path sqliteFile() {
        return sqliteFile;
    }

    public int resolverDepthCutoff() {
        return resolverDepthCutoff;
    }

    public List<Currency> currencies() {
        return currencies;
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

    public int notaryPendingOfferCapPerSenderRecipient() {
        return notaryPendingOfferCapPerSenderRecipient;
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

    public List<CeremonyTemplateDefinition> ceremonyTemplates() {
        return ceremonyTemplates;
    }

    public long diplomacyBetrayalHonorPenalty() {
        return diplomacyBetrayalHonorPenalty;
    }

    public boolean pvpRestrictToDeclaredWars() {
        return pvpRestrictToDeclaredWars;
    }

    public boolean ceremonyBlockTriggersEnabled() {
        return ceremonyBlockTriggersEnabled;
    }
}
