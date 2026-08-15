package com.google.gmail.philbgarner.oathbound.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The universal ownership/permission primitive - chests, doors, claims, factions, kingdoms, dynasties, companies.
 * Owner mutation must go through {@link OwnershipResolver#reassignOwner}, never {@link #setOwner} directly,
 * so cycle prevention is always enforced at write-time.
 */
public final class ProtectionGroup {
    private final UUID id;
    private String name;
    private EntityRef owner;
    private final List<Member> members = new ArrayList<>();
    private final List<Role> roles = new ArrayList<>();
    private GroupTier tier;

    public ProtectionGroup(UUID id, String name, EntityRef owner, GroupTier tier) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireNonBlank(name);
        this.owner = Objects.requireNonNull(owner, "owner");
        this.tier = Objects.requireNonNull(tier, "tier");
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = requireNonBlank(name);
    }

    public EntityRef owner() {
        return owner;
    }

    /** Package-private: the only sanctioned caller is {@link OwnershipResolver}, after cycle validation. */
    void setOwner(EntityRef owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public GroupTier tier() {
        return tier;
    }

    public void setTier(GroupTier tier) {
        this.tier = Objects.requireNonNull(tier, "tier");
    }

    public List<Member> members() {
        return List.copyOf(members);
    }

    public void addMember(Member member) {
        Objects.requireNonNull(member, "member");
        removeMember(member.player());
        members.add(member);
    }

    public void removeMember(PlayerRef player) {
        members.removeIf(m -> m.player().equals(player));
    }

    public Optional<Member> findMember(PlayerRef player) {
        return members.stream().filter(m -> m.player().equals(player)).findFirst();
    }

    public List<Role> roles() {
        return List.copyOf(roles);
    }

    public void addRole(Role role) {
        Objects.requireNonNull(role, "role");
        removeRole(role.name());
        roles.add(role);
    }

    public void removeRole(String roleName) {
        roles.removeIf(r -> r.name().equals(roleName));
    }

    public Optional<Role> findRole(String roleName) {
        return roles.stream().filter(r -> r.name().equals(roleName)).findFirst();
    }

    public boolean hasPermission(PlayerRef player, GroupPermission permission) {
        return findMember(player)
                .flatMap(member -> findRole(member.roleName()))
                .map(role -> role.has(permission))
                .orElse(false);
    }

    private static String requireNonBlank(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return value;
    }
}
