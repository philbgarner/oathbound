package com.google.gmail.philbgarner.oathbound.group;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record Role(String name, int rankWeight, Set<GroupPermission> permissions) {
    public Role {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Role name must not be blank");
        }
        permissions = permissions.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(permissions));
    }

    public boolean has(GroupPermission permission) {
        return permissions.contains(permission);
    }
}
