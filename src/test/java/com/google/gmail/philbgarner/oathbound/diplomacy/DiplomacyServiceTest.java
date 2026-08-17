package com.google.gmail.philbgarner.oathbound.diplomacy;

import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DiplomacyServiceTest {

    private final Map<UUID, ProtectionGroup> groups = new HashMap<>();
    private final OwnershipResolver ownershipResolver = new OwnershipResolver(id -> Optional.ofNullable(groups.get(id)), 10);
    private final DiplomacyService service = new DiplomacyService(ownershipResolver);

    private ProtectionGroup group(String name, com.google.gmail.philbgarner.oathbound.group.EntityRef owner, GroupTier tier) {
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), name, owner, tier);
        groups.put(group.id(), group);
        return group;
    }

    @Test
    void defaultsToNeutralForAnUnrecordedPair() {
        ProtectionGroup a = group("A", new PlayerRef(UUID.randomUUID()), GroupTier.KINGDOM);
        ProtectionGroup b = group("B", new PlayerRef(UUID.randomUUID()), GroupTier.KINGDOM);

        assertEquals(DiplomaticState.NEUTRAL, service.currentState(a.id(), b.id()));
    }

    @Test
    void queryingEitherOrderOfAPairHitsTheSameRecord() {
        ProtectionGroup a = group("A", new PlayerRef(UUID.randomUUID()), GroupTier.KINGDOM);
        ProtectionGroup b = group("B", new PlayerRef(UUID.randomUUID()), GroupTier.KINGDOM);

        service.setState(a.id(), b.id(), DiplomaticState.WAR, Instant.now());

        assertEquals(DiplomaticState.WAR, service.currentState(a.id(), b.id()));
        assertEquals(DiplomaticState.WAR, service.currentState(b.id(), a.id()));
    }

    @Test
    void settingAndQueryingViaAVassalProducesTheSameResultAsViaItsLiege() {
        ProtectionGroup kingdomA = group("Cream", new PlayerRef(UUID.randomUUID()), GroupTier.KINGDOM);
        ProtectionGroup duchyA = group("Cheese", new ProtectionGroupRef(kingdomA.id()), GroupTier.TOWN);
        ProtectionGroup kingdomB = group("Petroleum", new PlayerRef(UUID.randomUUID()), GroupTier.KINGDOM);
        ProtectionGroup duchyB = group("Oil", new ProtectionGroupRef(kingdomB.id()), GroupTier.TOWN);

        // Declared using the vassals' ids...
        service.setState(duchyA.id(), duchyB.id(), DiplomaticState.WAR, Instant.now());

        // ...but the relation applies at the liege level, and every combination of vassal/liege agrees.
        assertEquals(DiplomaticState.WAR, service.currentState(kingdomA.id(), kingdomB.id()));
        assertEquals(DiplomaticState.WAR, service.currentState(duchyA.id(), kingdomB.id()));
        assertEquals(DiplomaticState.WAR, service.currentState(kingdomA.id(), duchyB.id()));
        assertEquals(DiplomaticState.WAR, service.currentState(duchyA.id(), duchyB.id()));
    }

    @Test
    void rootOfResolvesAVassalToItsLiege() {
        ProtectionGroup kingdom = group("Cream", new PlayerRef(UUID.randomUUID()), GroupTier.KINGDOM);
        ProtectionGroup duchy = group("Cheese", new ProtectionGroupRef(kingdom.id()), GroupTier.TOWN);

        assertEquals(kingdom.id(), service.rootOf(duchy.id()));
    }
}
