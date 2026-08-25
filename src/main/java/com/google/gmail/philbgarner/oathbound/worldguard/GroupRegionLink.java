package com.google.gmail.philbgarner.oathbound.worldguard;

import java.util.Objects;
import java.util.UUID;

/** Associates a {@code ProtectionGroup} with a WorldGuard region - {@code worldId} is a Bukkit world
 * UUID (matching {@code VillagerNpcLocation}'s convention), resolved to a live {@code org.bukkit.World}
 * only at sync time. A group can hold any number of links (one per region it wants kept in sync); each
 * link is its own row, looked up by scanning the cache for matching {@code groupId} - same in-memory
 * cache + linear scan pattern every other domain object in this plugin uses, rather than an indexed
 * query. */
public record GroupRegionLink(UUID id, UUID groupId, UUID worldId, String regionId) {
    public GroupRegionLink {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(regionId, "regionId");
    }
}
