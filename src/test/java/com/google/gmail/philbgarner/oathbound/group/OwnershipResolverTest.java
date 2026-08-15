package com.google.gmail.philbgarner.oathbound.group;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OwnershipResolverTest {

    private final Map<UUID, ProtectionGroup> groups = new HashMap<>();
    private final OwnershipResolver resolver = new OwnershipResolver(this::find, 10);

    private Optional<ProtectionGroup> find(UUID id) {
        return Optional.ofNullable(groups.get(id));
    }

    private ProtectionGroup newGroup(EntityRef owner) {
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "g-" + groups.size(), owner, GroupTier.INDIVIDUAL);
        groups.put(group.id(), group);
        return group;
    }

    @Test
    void resolvesDirectlyOwnedByPlayer() {
        PlayerRef player = new PlayerRef(UUID.randomUUID());
        ProtectionGroup group = newGroup(player);

        OwnershipResolver.Resolution resolution = resolver.resolveTerminalOwner(new ProtectionGroupRef(group.id()));

        var resolved = assertInstanceOf(OwnershipResolver.Resolved.class, resolution);
        assertEquals(player, resolved.terminalOwner());
        assertEquals(1, resolved.hops());
    }

    @Test
    void resolvesThroughAChainOfGroups() {
        PlayerRef king = new PlayerRef(UUID.randomUUID());
        ProtectionGroup crown = newGroup(king);
        ProtectionGroup duchy = newGroup(new ProtectionGroupRef(crown.id()));
        ProtectionGroup town = newGroup(new ProtectionGroupRef(duchy.id()));

        OwnershipResolver.Resolution resolution = resolver.resolveTerminalOwner(new ProtectionGroupRef(town.id()));

        var resolved = assertInstanceOf(OwnershipResolver.Resolved.class, resolution);
        assertEquals(king, resolved.terminalOwner());
        assertEquals(3, resolved.hops());
    }

    @Test
    void resolutionFailsSafeWhenChainExceedsDepthCutoff() {
        OwnershipResolver shallowResolver = new OwnershipResolver(this::find, 3);
        PlayerRef player = new PlayerRef(UUID.randomUUID());
        ProtectionGroup previous = newGroup(player);
        for (int i = 0; i < 5; i++) {
            previous = newGroup(new ProtectionGroupRef(previous.id()));
        }

        OwnershipResolver.Resolution resolution = shallowResolver.resolveTerminalOwner(new ProtectionGroupRef(previous.id()));

        assertInstanceOf(OwnershipResolver.Unresolved.class, resolution);
    }

    @Test
    void resolutionFailsSafeWhenAGroupInTheChainIsMissing() {
        ProtectionGroupRef danglingRef = new ProtectionGroupRef(UUID.randomUUID());
        ProtectionGroup group = newGroup(danglingRef);

        OwnershipResolver.Resolution resolution = resolver.resolveTerminalOwner(new ProtectionGroupRef(group.id()));

        assertInstanceOf(OwnershipResolver.Unresolved.class, resolution);
    }

    @Test
    void directSelfOwnershipIsACycle() {
        ProtectionGroup group = newGroup(new PlayerRef(UUID.randomUUID()));
        ProtectionGroupRef selfRef = new ProtectionGroupRef(group.id());

        assertTrue(resolver.wouldCreateCycle(selfRef, selfRef));
    }

    @Test
    void indirectCycleIsRejected() {
        ProtectionGroup a = newGroup(new PlayerRef(UUID.randomUUID()));
        ProtectionGroup b = newGroup(new ProtectionGroupRef(a.id()));
        ProtectionGroup c = newGroup(new ProtectionGroupRef(b.id()));

        // Attempting to make A owned by C would close the loop A -> C -> B -> A.
        assertTrue(resolver.wouldCreateCycle(new ProtectionGroupRef(a.id()), new ProtectionGroupRef(c.id())));
        assertThrows(IllegalArgumentException.class, () -> resolver.reassignOwner(a, new ProtectionGroupRef(c.id())));
    }

    @Test
    void cycleCheckFailsSafeWhenChainExceedsDepthCutoff() {
        OwnershipResolver shallowResolver = new OwnershipResolver(this::find, 3);
        PlayerRef player = new PlayerRef(UUID.randomUUID());
        ProtectionGroup previous = newGroup(player);
        for (int i = 0; i < 5; i++) {
            previous = newGroup(new ProtectionGroupRef(previous.id()));
        }
        ProtectionGroup target = newGroup(player);

        // No actual cycle exists here, but the chain can't be proven cycle-free within the cutoff.
        assertTrue(shallowResolver.wouldCreateCycle(new ProtectionGroupRef(target.id()), new ProtectionGroupRef(previous.id())));
    }

    @Test
    void reassignOwnerSucceedsWhenNoCycle() {
        ProtectionGroup a = newGroup(new PlayerRef(UUID.randomUUID()));
        ProtectionGroup b = newGroup(new PlayerRef(UUID.randomUUID()));

        resolver.reassignOwner(a, new ProtectionGroupRef(b.id()));

        assertEquals(new ProtectionGroupRef(b.id()), a.owner());
    }
}
