package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AltarNestingServiceTest {

    private final AltarRadiusCalculator calculator =
            new AltarRadiusCalculator(4.0, Map.of(GroupTier.INDIVIDUAL, 1.0, GroupTier.KINGDOM, 3.0));
    private final UUID worldId = UUID.randomUUID();
    private final Instant now = Instant.now();

    private Altar altarWithPower(EntityRef owner, AltarLocation location, long power) {
        Altar altar = new Altar(UUID.randomUUID(), owner, location, now);
        altar.applySacrifice(power, now, Duration.ZERO, power);
        return altar;
    }

    @Test
    void noAltarsInRangeIsAlwaysLegal() {
        AltarNestingService.NestingResult result = AltarNestingService.checkPlacement(
                new AltarLocation(worldId, 0, 64, 0), GroupTier.INDIVIDUAL, List.of(), now, 5, calculator,
                id -> Optional.empty());
        assertTrue(result.legal());
    }

    @Test
    void newIndividualBlockedByExistingIndividualAtTheSameSpot() {
        AltarLocation spot = new AltarLocation(worldId, 0, 64, 0);
        Altar existing = altarWithPower(new PlayerRef(UUID.randomUUID()), spot, 100L);

        AltarNestingService.NestingResult result = AltarNestingService.checkPlacement(
                spot, GroupTier.INDIVIDUAL, List.of(existing), now, 5, calculator, id -> Optional.empty());

        assertFalse(result.legal());
        assertEquals(existing, result.blockingAltar().orElseThrow());
    }

    @Test
    void newIndividualLegalFullyInsideAnExistingKingdomsRadius() {
        ProtectionGroup kingdom = new ProtectionGroup(UUID.randomUUID(), "Kingdom",
                new PlayerRef(UUID.randomUUID()), GroupTier.KINGDOM);
        AltarLocation kingdomAltarLoc = new AltarLocation(worldId, 0, 64, 0);
        Altar kingdomAltar = altarWithPower(new ProtectionGroupRef(kingdom.id()), kingdomAltarLoc, 10_000L);

        AltarLocation newSpot = new AltarLocation(worldId, 5, 64, 5);
        AltarNestingService.NestingResult result = AltarNestingService.checkPlacement(
                newSpot, GroupTier.INDIVIDUAL, List.of(kingdomAltar), now, 5, calculator,
                id -> Optional.of(kingdom));

        assertTrue(result.legal());
    }

    @Test
    void newKingdomBlockedByAnExistingIndividualItWouldSwallow() {
        AltarLocation spot = new AltarLocation(worldId, 0, 64, 0);
        Altar existingIndividual = altarWithPower(new PlayerRef(UUID.randomUUID()), spot, 100L);

        AltarNestingService.NestingResult result = AltarNestingService.checkPlacement(
                spot, GroupTier.KINGDOM, List.of(existingIndividual), now, 5, calculator, id -> Optional.empty());

        assertFalse(result.legal());
        assertEquals(existingIndividual, result.blockingAltar().orElseThrow());
    }
}
