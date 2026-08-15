package com.google.gmail.philbgarner.oathbound.altar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure valuation math for a sacrifice deposit. Only enchantments matter - item type is irrelevant.
 *
 * Per enchantment, {@code baseWeight = weightScale / maxLevel}, so maxing out any single enchantment is
 * worth exactly {@code weightScale} regardless of how many levels it has (normalizes a five-level
 * enchantment like Sharpness against a one-level enchantment like Mending without a hardcoded
 * per-enchantment table). An item's value is the sum of {@code baseWeight * level} across its
 * enchantments.
 *
 * Diminishing returns apply across the whole batch, not per item: every occurrence of the same
 * enchantment type across all deposited items is collected, sorted by descending level (so the
 * strongest copy keeps full value), and the n-th occurrence (0-indexed) is scaled by
 * {@code repeatDecay^n} - discourages volume-stuffing many low-value copies of the same enchantment.
 */
public final class SacrificeValuationService {

    private SacrificeValuationService() {
    }

    public static long valueOf(List<Map<String, Integer>> depositedItemEnchants,
                                EnchantmentMaxLevelLookup maxLevels, double weightScale, double repeatDecay) {
        Map<String, List<Integer>> levelsByEnchant = new HashMap<>();
        for (Map<String, Integer> itemEnchants : depositedItemEnchants) {
            for (Map.Entry<String, Integer> entry : itemEnchants.entrySet()) {
                levelsByEnchant.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(entry.getValue());
            }
        }

        double total = 0;
        for (Map.Entry<String, List<Integer>> entry : levelsByEnchant.entrySet()) {
            List<Integer> levels = entry.getValue();
            levels.sort(Comparator.reverseOrder());
            int maxLevel = Math.max(1, maxLevels.maxLevelOf(entry.getKey()));
            double baseWeight = weightScale / maxLevel;
            for (int occurrence = 0; occurrence < levels.size(); occurrence++) {
                total += baseWeight * levels.get(occurrence) * Math.pow(repeatDecay, occurrence);
            }
        }
        return Math.round(total);
    }
}
