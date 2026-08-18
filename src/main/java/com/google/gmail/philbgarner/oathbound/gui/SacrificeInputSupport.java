package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.EnchantmentMaxLevelLookup;
import com.google.gmail.philbgarner.oathbound.altar.EnchantmentRarityLookup;
import com.google.gmail.philbgarner.oathbound.altar.EnchantmentRarityTier;
import com.google.gmail.philbgarner.oathbound.altar.SacrificeValuationService;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Enchantment extraction and valuation shared by every GUI that spends an "enchanted items only" deposit
 * slot range against {@link SacrificeValuationService} - the altar sacrifice ritual and the banishment
 * prayer/intercession ritual both spend the same currency (enchanted-item value), just pay it into
 * different places. Keeps {@link SacrificeValuationService} itself Bukkit-free. */
final class SacrificeInputSupport {
    private SacrificeInputSupport() {
    }

    static final EnchantmentMaxLevelLookup MAX_LEVELS = key -> {
        Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.fromString(key));
        return enchantment == null ? 1 : enchantment.getMaxLevel();
    };

    /** Needs config (the per-rarity multipliers), so it can't be a static field - built per call,
     * capturing {@code plugin}. Buckets {@code Enchantment.getWeight()} via {@link EnchantmentRarityTier#of}
     * rather than using the deprecated-for-removal {@code Enchantment.getRarity()}/{@code EnchantmentRarity}. */
    static EnchantmentRarityLookup rarityLookup(OathboundPlugin plugin) {
        return key -> {
            Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.fromString(key));
            EnchantmentRarityTier tier = EnchantmentRarityTier.of(enchantment == null ? 10 : enchantment.getWeight());
            return plugin.oathboundConfig().altarRarityMultiplier(tier);
        };
    }

    static Map<String, Integer> enchantmentsOf(ItemStack stack) {
        Map<Enchantment, Integer> enchantments;
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchantments = storageMeta.getStoredEnchants();
        } else {
            enchantments = stack.getEnchantments();
        }
        Map<String, Integer> byKey = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            byKey.put(entry.getKey().getKey().toString(), entry.getValue());
        }
        return byKey;
    }

    /** {@code true} if any non-air item in {@code [0, endExclusive)} has no enchantments - callers reject
     * the whole deposit rather than silently skipping mixed-in junk. */
    static boolean hasNonEnchantedItem(Inventory inventory, int endExclusive) {
        for (int slot = 0; slot < endExclusive; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR && enchantmentsOf(stack).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static long valueOfDeposits(Inventory inventory, int endExclusive, OathboundPlugin plugin) {
        List<Map<String, Integer>> perItemEnchants = new ArrayList<>();
        for (int slot = 0; slot < endExclusive; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                perItemEnchants.add(enchantmentsOf(stack));
            }
        }
        return SacrificeValuationService.valueOf(perItemEnchants, MAX_LEVELS, rarityLookup(plugin),
                plugin.oathboundConfig().altarEnchantmentWeightScale(), plugin.oathboundConfig().altarRepeatEnchantmentDecay());
    }

    /** Clears every deposit slot in {@code [0, endExclusive)} - used on confirm, after valuation has
     * already been read, to consume the deposit permanently. */
    static void clearDeposits(Inventory inventory, int endExclusive) {
        for (int slot = 0; slot < endExclusive; slot++) {
            inventory.setItem(slot, null);
        }
    }

    /** Returns every deposited item to {@code player} - used when a deposit GUI closes without a
     * confirmed ritual. */
    static void returnDeposits(Inventory inventory, int endExclusive, org.bukkit.entity.Player player) {
        for (int slot = 0; slot < endExclusive; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                inventory.setItem(slot, null);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                leftover.values().forEach(remaining -> player.getWorld().dropItemNaturally(player.getLocation(), remaining));
            }
        }
    }
}
