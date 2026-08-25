package com.google.gmail.philbgarner.oathbound.villager;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A rooted, invulnerable Villager NPC of a given {@link NpcRole} - AI disabled and removal suppressed so
 * it can't wander off or despawn, but otherwise a completely standard villager: right-clicking it opens
 * the normal vanilla trade GUI for its profession, no custom menu or price list involved. {@code entityId}
 * is the backing Bukkit entity's UUID, the sole lookup key for admin lookups
 * ({@code /oathbound-debug villager}). */
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
