package com.google.gmail.philbgarner.oathbound.protection;

import com.google.gmail.philbgarner.oathbound.group.GroupPermission;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.group.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtectionAccessServiceTest {

    private ProtectionGroup groupWith(GroupPermission... permissions) {
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "Guild", owner, GroupTier.INDIVIDUAL);
        group.addRole(new Role("Member", 0, Set.copyOf(EnumSet.copyOf(List.of(permissions)))));
        return group;
    }

    private Protection protectionFor(ProtectionGroup group, ProtectionType type) {
        return new Protection(UUID.randomUUID(),
                new ProtectedLocation(UUID.randomUUID(), 0, 64, 0), type,
                new ProtectionGroupRef(group.id()), Instant.now());
    }

    @Test
    void memberWithRequiredPermissionCanAccess() {
        ProtectionGroup group = groupWith(GroupPermission.OPEN_CONTAINERS);
        PlayerRef member = new PlayerRef(UUID.randomUUID());
        group.addMember(new Member(member, "Member"));
        Protection protection = protectionFor(group, ProtectionType.CONTAINER);

        assertTrue(ProtectionAccessService.canAccess(protection, member, id -> Optional.of(group)));
    }

    @Test
    void memberWithoutRequiredPermissionIsDenied() {
        ProtectionGroup group = groupWith(GroupPermission.INVITE);
        PlayerRef member = new PlayerRef(UUID.randomUUID());
        group.addMember(new Member(member, "Member"));
        Protection protection = protectionFor(group, ProtectionType.CONTAINER);

        assertFalse(ProtectionAccessService.canAccess(protection, member, id -> Optional.of(group)));
    }

    @Test
    void nonMemberIsDenied() {
        ProtectionGroup group = groupWith(GroupPermission.OPEN_CONTAINERS);
        PlayerRef stranger = new PlayerRef(UUID.randomUUID());
        Protection protection = protectionFor(group, ProtectionType.CONTAINER);

        assertFalse(ProtectionAccessService.canAccess(protection, stranger, id -> Optional.of(group)));
    }

    @Test
    void missingGroupFailsSafeToDenied() {
        ProtectionGroup group = groupWith(GroupPermission.OPEN_CONTAINERS);
        PlayerRef member = new PlayerRef(UUID.randomUUID());
        group.addMember(new Member(member, "Member"));
        Protection protection = protectionFor(group, ProtectionType.CONTAINER);

        assertFalse(ProtectionAccessService.canAccess(protection, member, id -> Optional.empty()));
    }

    @Test
    void doorsAreGatedByOpenDoorsNotOpenContainers() {
        ProtectionGroup group = groupWith(GroupPermission.OPEN_CONTAINERS);
        PlayerRef member = new PlayerRef(UUID.randomUUID());
        group.addMember(new Member(member, "Member"));
        Protection door = protectionFor(group, ProtectionType.DOOR);

        assertFalse(ProtectionAccessService.canAccess(door, member, id -> Optional.of(group)));
    }

    @Test
    void bindableGroupsFiltersToGroupsWherePlayerAlreadyHasThePermission() {
        ProtectionGroup withPermission = groupWith(GroupPermission.OPEN_DOORS);
        ProtectionGroup withoutPermission = groupWith(GroupPermission.INVITE);
        PlayerRef player = new PlayerRef(UUID.randomUUID());
        withPermission.addMember(new Member(player, "Member"));
        withoutPermission.addMember(new Member(player, "Member"));

        List<ProtectionGroup> eligible = ProtectionAccessService.bindableGroups(
                player, ProtectionType.DOOR, List.of(withPermission, withoutPermission));

        assertEquals(List.of(withPermission), eligible);
    }
}
