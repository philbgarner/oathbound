package com.google.gmail.philbgarner.oathbound.villager;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A rooted, invulnerable Villager NPC that opens a fixed buy/sell shop menu for its {@link NpcRole} on
 * right-click, instead of the vanilla trade GUI. {@code entityId} is the backing Bukkit entity's UUID -
 * the sole lookup key at interact-time, mirroring
 * {@link com.google.gmail.philbgarner.oathbound.notary.Notary}. */
public record VillagerNpc(UUID id, UUID entityId, EntityRef owner, NpcRole role, String name,
                           VillagerNpcLocation location, Instant installedAt) {
    public VillagerNpc {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(installedAt, "installedAt");
    }
}
