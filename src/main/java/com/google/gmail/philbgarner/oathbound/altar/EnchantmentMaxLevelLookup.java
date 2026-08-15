package com.google.gmail.philbgarner.oathbound.altar;

/** Resolves an enchantment's max level from its string key (e.g. Bukkit's namespaced key toString()),
 * keeping {@link SacrificeValuationService} itself free of any Bukkit enchantment dependency. */
@FunctionalInterface
public interface EnchantmentMaxLevelLookup {
    int maxLevelOf(String enchantmentKey);
}
