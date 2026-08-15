package com.google.gmail.philbgarner.oathbound.group;

import java.util.Objects;
import java.util.UUID;

public record ProtectionGroupRef(UUID groupId) implements EntityRef {
    public ProtectionGroupRef {
        Objects.requireNonNull(groupId, "groupId");
    }
}
