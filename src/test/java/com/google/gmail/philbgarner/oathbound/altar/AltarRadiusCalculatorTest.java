package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AltarRadiusCalculatorTest {

    private final AltarRadiusCalculator calculator = new AltarRadiusCalculator(4.0, Map.of(
            GroupTier.INDIVIDUAL, 1.0,
            GroupTier.KINGDOM, 3.0
    ));

    @Test
    void zeroPowerAlwaysYieldsZeroRadius() {
        assertEquals(0, calculator.radiusFor(0, GroupTier.INDIVIDUAL));
        assertEquals(0, calculator.radiusFor(0, GroupTier.KINGDOM));
    }

    @Test
    void higherTierYieldsLargerRadiusForTheSamePower() {
        int individualRadius = calculator.radiusFor(100, GroupTier.INDIVIDUAL);
        int kingdomRadius = calculator.radiusFor(100, GroupTier.KINGDOM);

        assertTrue(kingdomRadius > individualRadius);
    }

    @Test
    void unknownTierFallsBackToADefaultMultiplier() {
        assertEquals(calculator.radiusFor(100, GroupTier.INDIVIDUAL), calculator.radiusFor(100, GroupTier.TOWN));
    }

    @Test
    void radiusGrowthHasDiminishingReturnsPerUnitOfPower() {
        int radiusAt100 = calculator.radiusFor(100, GroupTier.INDIVIDUAL);
        int radiusAt200 = calculator.radiusFor(200, GroupTier.INDIVIDUAL);
        int radiusAt300 = calculator.radiusFor(300, GroupTier.INDIVIDUAL);

        int firstJump = radiusAt200 - radiusAt100;
        int secondJump = radiusAt300 - radiusAt200;

        assertTrue(secondJump <= firstJump);
    }

    @Test
    void tierOfIsIndividualForAPlayerAndResolvesGroupTierForAGroupRef() {
        PlayerRef player = new PlayerRef(UUID.randomUUID());
        assertEquals(GroupTier.INDIVIDUAL, AltarRadiusCalculator.tierOf(player, id -> Optional.empty()));

        ProtectionGroup kingdom = new ProtectionGroup(UUID.randomUUID(), "Kingdom", player, GroupTier.KINGDOM);
        assertEquals(GroupTier.KINGDOM,
                AltarRadiusCalculator.tierOf(new ProtectionGroupRef(kingdom.id()), id -> Optional.of(kingdom)));
    }
}
