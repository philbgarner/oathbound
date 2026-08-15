package com.google.gmail.philbgarner.oathbound.altar;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.GroupPermission;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.group.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClaimAccessServiceTest {

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
    void pointOutsideEveryAltarsRadiusHasNoCoveringAltar() {
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        Altar altar = altarWithPower(owner, new AltarLocation(worldId, 0, 64, 0), 100L);

        AltarLocation farAway = new AltarLocation(worldId, 1000, 64, 1000);
        assertTrue(ClaimAccessService.coveringAltar(farAway, List.of(altar), now, 5, calculator, id -> Optional.empty())
                .isEmpty());
    }

    @Test
    void zeroPowerAltarNeverCoversAnyPoint() {
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        AltarLocation altarLoc = new AltarLocation(worldId, 0, 64, 0);
        Altar altar = altarWithPower(owner, altarLoc, 0L);

        assertTrue(ClaimAccessService.coveringAltar(altarLoc, List.of(altar), now, 5, calculator, id -> Optional.empty())
                .isEmpty());
    }

    @Test
    void pointInsideRadiusIsCoveredByThatAltar() {
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        Altar altar = altarWithPower(owner, new AltarLocation(worldId, 0, 64, 0), 100L);

        AltarLocation nearby = new AltarLocation(worldId, 10, 64, 10);
        Optional<Altar> covering =
                ClaimAccessService.coveringAltar(nearby, List.of(altar), now, 5, calculator, id -> Optional.empty());
        assertEquals(altar, covering.orElseThrow());
    }

    @Test
    void overlappingAltarsPickTheSmallestRadius() {
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        Altar large = altarWithPower(owner, new AltarLocation(worldId, 0, 64, 0), 100L);
        Altar small = altarWithPower(owner, new AltarLocation(worldId, 5, 64, 5), 25L);

        AltarLocation point = new AltarLocation(worldId, 5, 64, 5);
        Optional<Altar> covering = ClaimAccessService.coveringAltar(
                point, List.of(large, small), now, 5, calculator, id -> Optional.empty());
        assertEquals(small, covering.orElseThrow());
    }

    @Test
    void equalRadiusOverlapBreaksTieBySmallestGroupTier() {
        // Same multiplier for both tiers in this local calculator, so identical power yields an
        // identical radius by construction - isolating the tier-ordinal tiebreak from the radius formula.
        AltarRadiusCalculator equalMultiplierCalculator =
                new AltarRadiusCalculator(4.0, Map.of(GroupTier.INDIVIDUAL, 1.0, GroupTier.COMPANY, 1.0));
        ProtectionGroup company = new ProtectionGroup(UUID.randomUUID(), "Company",
                new PlayerRef(UUID.randomUUID()), GroupTier.COMPANY);
        AltarLocation point = new AltarLocation(worldId, 5, 64, 5);

        Altar individual = altarWithPower(new PlayerRef(UUID.randomUUID()), point, 100L);
        Altar companyAltar = altarWithPower(new ProtectionGroupRef(company.id()), point, 100L);
        assertEquals(equalMultiplierCalculator.radiusFor(100, GroupTier.INDIVIDUAL),
                equalMultiplierCalculator.radiusFor(100, GroupTier.COMPANY));

        Optional<Altar> covering = ClaimAccessService.coveringAltar(point, List.of(companyAltar, individual),
                now, 5, equalMultiplierCalculator, id -> Optional.of(company));
        assertEquals(individual, covering.orElseThrow());
    }

    @Test
    void protectedAltarExcludesAnAltarMidReconsecrationCooldown() {
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        AltarLocation altarLoc = new AltarLocation(worldId, 0, 64, 0);
        Altar altar = new Altar(UUID.randomUUID(), owner, altarLoc, now);
        altar.applySacrifice(100L, now, Duration.ofMinutes(5), 100L);

        assertTrue(ClaimAccessService.coveringAltar(altarLoc, List.of(altar), now, 5, calculator, id -> Optional.empty())
                .isPresent());
        assertTrue(ClaimAccessService.protectedAltar(altarLoc, List.of(altar), now, 5, calculator, id -> Optional.empty())
                .isEmpty());

        Instant afterCooldown = now.plus(Duration.ofMinutes(6));
        assertTrue(ClaimAccessService.protectedAltar(altarLoc, List.of(altar), afterCooldown, 5, calculator, id -> Optional.empty())
                .isPresent());
    }

    @Test
    void playerOwnedAltarIsOnlyBuildableByThatPlayer() {
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        PlayerRef other = new PlayerRef(UUID.randomUUID());
        Altar altar = altarWithPower(owner, new AltarLocation(worldId, 0, 64, 0), 100L);

        assertTrue(ClaimAccessService.canBuild(altar, owner, id -> Optional.empty()));
        assertFalse(ClaimAccessService.canBuild(altar, other, id -> Optional.empty()));
    }

    @Test
    void groupOwnedAltarIsGatedByBuildPermission() {
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "Town",
                new PlayerRef(UUID.randomUUID()), GroupTier.TOWN);
        group.addRole(new Role("Builder", 0, Set.copyOf(EnumSet.of(GroupPermission.BUILD))));
        group.addRole(new Role("Visitor", 0, Set.of()));
        PlayerRef builder = new PlayerRef(UUID.randomUUID());
        PlayerRef visitor = new PlayerRef(UUID.randomUUID());
        group.addMember(new Member(builder, "Builder"));
        group.addMember(new Member(visitor, "Visitor"));

        Altar altar = altarWithPower(new ProtectionGroupRef(group.id()), new AltarLocation(worldId, 0, 64, 0), 100L);

        assertTrue(ClaimAccessService.canBuild(altar, builder, id -> Optional.of(group)));
        assertFalse(ClaimAccessService.canBuild(altar, visitor, id -> Optional.of(group)));
    }

    @Test
    void groupOwnedAltarFailsSafeToDeniedWhenGroupUnresolved() {
        PlayerRef someone = new PlayerRef(UUID.randomUUID());
        Altar altar = altarWithPower(new ProtectionGroupRef(UUID.randomUUID()), new AltarLocation(worldId, 0, 64, 0), 100L);

        assertFalse(ClaimAccessService.canBuild(altar, someone, id -> Optional.empty()));
    }
}
