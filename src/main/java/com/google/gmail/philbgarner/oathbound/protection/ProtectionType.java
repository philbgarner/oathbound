package com.google.gmail.philbgarner.oathbound.protection;

import com.google.gmail.philbgarner.oathbound.group.GroupPermission;

/** What kind of block a {@link Protection} binds - determines which permission gates access to it. */
public enum ProtectionType {
    CONTAINER(GroupPermission.OPEN_CONTAINERS),
    DOOR(GroupPermission.OPEN_DOORS);

    private final GroupPermission requiredPermission;

    ProtectionType(GroupPermission requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public GroupPermission requiredPermission() {
        return requiredPermission;
    }
}
