package com.google.gmail.philbgarner.oathbound.persistence.dto;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.Role;

import java.util.List;
import java.util.UUID;

public record GroupDto(UUID id, String name, EntityRef owner, List<Member> members, List<Role> roles,
                        GroupTier tier) {

    public static GroupDto from(ProtectionGroup group) {
        return new GroupDto(group.id(), group.name(), group.owner(), group.members(), group.roles(), group.tier());
    }

    public ProtectionGroup toDomain() {
        ProtectionGroup group = new ProtectionGroup(id, name, owner, tier);
        members.forEach(group::addMember);
        roles.forEach(group::addRole);
        return group;
    }
}
