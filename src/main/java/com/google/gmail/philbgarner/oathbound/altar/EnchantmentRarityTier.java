package com.google.gmail.philbgarner.oathbound.altar;

/** A coarse rarity bucket derived from an enchantment's Minecraft "weight" (how likely it is to be
 * offered/selected - lower weight is rarer). Deliberately our own type rather than Bukkit's
 * {@code io.papermc.paper.enchantments.EnchantmentRarity}: that class, and
 * {@code Enchantment.getRarity()}, are marked deprecated for removal on this Paper API version, while
 * the plain {@code Enchantment.getWeight()} int is not - this buckets that instead, so sacrifice
 * valuation isn't built on an API that's already scheduled to disappear. */
public enum EnchantmentRarityTier {
    VERY_RARE, RARE, UNCOMMON, COMMON;

    /** Mirrors vanilla's own historical weight bands (COMMON=10, UNCOMMON=5, RARE=2, VERY_RARE=1) - a
     * modded/data-pack enchantment with an unusual weight falls into whichever band its weight meets
     * or exceeds. */
    public static EnchantmentRarityTier of(int weight) {
        if (weight >= 10) {
            return COMMON;
        }
        if (weight >= 5) {
            return UNCOMMON;
        }
        if (weight >= 2) {
            return RARE;
        }
        return VERY_RARE;
    }
}
