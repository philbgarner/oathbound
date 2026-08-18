package com.google.gmail.philbgarner.oathbound.altar;

/** Resolves an enchantment's value multiplier from its string key (e.g. Bukkit's namespaced key
 * toString()), keeping {@link SacrificeValuationService} itself free of any Bukkit enchantment
 * dependency - mirrors {@link EnchantmentMaxLevelLookup}'s shape exactly. */
@FunctionalInterface
public interface EnchantmentRarityLookup {
    double multiplierOf(String enchantmentKey);
}
