package com.google.gmail.philbgarner.oathbound.altar;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SacrificeValuationServiceTest {

    private static final EnchantmentMaxLevelLookup MAX_LEVELS = key -> switch (key) {
        case "sharpness" -> 5;
        case "mending" -> 1;
        case "unbreaking" -> 3;
        default -> 1;
    };

    /** Neutral - every enchantment worth the same multiplier - so existing tests keep testing
     * level-normalization in isolation, holding rarity constant. */
    private static final EnchantmentRarityLookup NEUTRAL_RARITY = key -> 1.0;

    @Test
    void emptyDepositIsWorthNothing() {
        assertEquals(0L, SacrificeValuationService.valueOf(List.of(), MAX_LEVELS, NEUTRAL_RARITY, 10.0, 0.5));
    }

    @Test
    void singleItemSingleEnchantValuesByBaseWeightTimesLevel() {
        long value = SacrificeValuationService.valueOf(
                List.of(Map.of("sharpness", 5)), MAX_LEVELS, NEUTRAL_RARITY, 10.0, 0.5);
        // baseWeight = 10/5 = 2.0, value = 2.0 * 5 = 10
        assertEquals(10L, value);
    }

    @Test
    void singleItemMultipleDistinctEnchantsSum() {
        long value = SacrificeValuationService.valueOf(
                List.of(Map.of("sharpness", 5, "mending", 1)), MAX_LEVELS, NEUTRAL_RARITY, 10.0, 0.5);
        // sharpness: 10/5*5 = 10, mending: 10/1*1 = 10 -> 20
        assertEquals(20L, value);
    }

    @Test
    void twoItemsSharingAnEnchantTypeTriggerRepeatDecayOnTheSecondOccurrence() {
        long value = SacrificeValuationService.valueOf(
                List.of(Map.of("sharpness", 5), Map.of("sharpness", 5)), MAX_LEVELS, NEUTRAL_RARITY, 10.0, 0.5);
        // first occurrence: 10, second occurrence: 10 * 0.5 = 5 -> 15
        assertEquals(15L, value);
    }

    @Test
    void repeatDecayAppliesRegardlessOfWhichItemTheRepeatIsOn() {
        long combined = SacrificeValuationService.valueOf(
                List.of(Map.of("sharpness", 5, "unbreaking", 3), Map.of("sharpness", 3)),
                MAX_LEVELS, NEUTRAL_RARITY, 10.0, 0.5);
        // sharpness occurrences sorted desc: [5, 3] -> 10 + (10/5*3)*0.5 = 10 + 3 = 13
        // unbreaking single occurrence: 10/3*3 = 10
        assertEquals(23L, combined);
    }

    @Test
    void aMaxedLevelOneAndAMaxedLevelFiveEnchantScoreEqualWhenRarityIsHeldConstant() {
        long maxedSharpness = SacrificeValuationService.valueOf(
                List.of(Map.of("sharpness", 5)), MAX_LEVELS, NEUTRAL_RARITY, 10.0, 0.5);
        long maxedMending = SacrificeValuationService.valueOf(
                List.of(Map.of("mending", 1)), MAX_LEVELS, NEUTRAL_RARITY, 10.0, 0.5);
        assertEquals(maxedSharpness, maxedMending);
    }

    @Test
    void rarerEnchantmentsAreWorthMoreEvenWhenBothAreMaxed() {
        EnchantmentRarityLookup rarity = key -> switch (key) {
            case "sharpness" -> 1.0;
            case "mending" -> 4.0;
            default -> 1.0;
        };
        long maxedSharpness = SacrificeValuationService.valueOf(
                List.of(Map.of("sharpness", 5)), MAX_LEVELS, rarity, 10.0, 0.5);
        long maxedMending = SacrificeValuationService.valueOf(
                List.of(Map.of("mending", 1)), MAX_LEVELS, rarity, 10.0, 0.5);
        // both maxed out, but mending's rarity multiplier (4.0) beats sharpness's (1.0)
        assertEquals(10L, maxedSharpness);
        assertEquals(40L, maxedMending);
    }

    @Test
    void rarityMultiplierAppliesOncePerEnchantmentTypeNotPerRepeatOccurrence() {
        EnchantmentRarityLookup doubled = key -> 2.0;
        long value = SacrificeValuationService.valueOf(
                List.of(Map.of("sharpness", 5), Map.of("sharpness", 5)), MAX_LEVELS, doubled, 10.0, 0.5);
        // first occurrence: 10*2 = 20, second occurrence: 20 * 0.5 (repeatDecay) = 10 -> 30
        assertEquals(30L, value);
    }
}
