package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BountyAbandonmentSweepServiceTest {

    private final PlayerRef placer = new PlayerRef(UUID.randomUUID());
    private final PlayerRef bob = new PlayerRef(UUID.randomUUID());
    private final PlayerRef carol = new PlayerRef(UUID.randomUUID());
    private final Currency coin = new Currency("coin");
    private final BountyAbandonmentSweepService sweepService = new BountyAbandonmentSweepService();

    @Test
    void decrementsRemainingQuantityExactlyOncePerNewlyAbandonedMember() {
        Map<UUID, ProtectionGroup> groups = new HashMap<>();
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "Raiders", placer, GroupTier.INDIVIDUAL);
        group.addMember(new Member(bob, "member"));
        group.addMember(new Member(carol, "member"));
        groups.put(group.id(), group);

        EconomyService economyService = new EconomyService(List.of(coin));
        economyService.deposit(placer, coin, 1000L);
        BountyService bountyService = new BountyService(economyService, id -> Optional.ofNullable(groups.get(id)),
                List::of, List::of, 0L, 0.0, Duration.ofHours(72), 100, Duration.ofDays(14), 0.0);
        Bounty bounty = bountyService.place(placer, new BountyTarget.Group(new ProtectionGroupRef(group.id())), 2,
                Map.of(coin, 200L), coin, Instant.now());

        Instant now = Instant.now();
        // bob inactive past threshold, carol still active
        Map<UUID, Instant> lastPlayed = Map.of(
                bob.playerId(), now.minus(Duration.ofDays(60)),
                carol.playerId(), now.minus(Duration.ofDays(1)));

        List<Bounty> changed = sweepService.sweep(List.of(bounty), id -> Optional.ofNullable(groups.get(id)),
                id -> lastPlayed.get(id), Duration.ofDays(30), now, bountyService);

        assertEquals(1, changed.size());
        assertEquals(1, bounty.remainingQuantity());
        assertEquals(BountyStatus.ACTIVE, bounty.status());

        // A second sweep at the same instant must not double-decrement for bob.
        List<Bounty> secondSweep = sweepService.sweep(List.of(bounty), id -> Optional.ofNullable(groups.get(id)),
                id -> lastPlayed.get(id), Duration.ofDays(30), now, bountyService);
        assertTrue(secondSweep.isEmpty());
        assertEquals(1, bounty.remainingQuantity());
    }

    @Test
    void ignoresSoloTargetedBounties() {
        EconomyService economyService = new EconomyService(List.of(coin));
        economyService.deposit(placer, coin, 1000L);
        BountyService bountyService = new BountyService(economyService, id -> Optional.empty(),
                List::of, List::of, 0L, 0.0, Duration.ofHours(72), 100, Duration.ofDays(14), 0.0);
        Bounty bounty = bountyService.place(placer, new BountyTarget.Solo(bob), 1, Map.of(coin, 200L), coin, Instant.now());

        List<Bounty> changed = sweepService.sweep(List.of(bounty), id -> Optional.empty(),
                id -> Instant.EPOCH, Duration.ofDays(30), Instant.now(), bountyService);

        assertTrue(changed.isEmpty());
        assertEquals(1, bounty.remainingQuantity());
    }
}
