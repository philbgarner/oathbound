package com.google.gmail.philbgarner.oathbound.notary;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A rooted, invulnerable Villager NPC that opens the named-party oath builder on right-click.
 * {@code entityId} is the backing Bukkit entity's UUID - the sole lookup key at interact-time, since a
 * spawned entity (unlike a placed block) isn't independently addressable by coordinate. {@code owner}
 * mirrors {@link com.google.gmail.philbgarner.oathbound.altar.Altar}'s player-or-group owner shape,
 * even though nothing currently restricts who may use a Notary - it's recorded for future admin/removal
 * permission checks.
 */
public record Notary(UUID id, UUID entityId, EntityRef owner, String name, NotaryLocation location,
                      Instant installedAt) {
    public Notary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(installedAt, "installedAt");
    }
}
