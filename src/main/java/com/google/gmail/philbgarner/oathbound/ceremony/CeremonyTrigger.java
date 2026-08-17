package com.google.gmail.philbgarner.oathbound.ceremony;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A pressure plate or push button bound to a {@link CeremonyTemplateDefinition} - stepping on/pressing
 * it starts the same confirm-in-chat flow as the player-to-player item right-click, with
 * {@code installer} standing in as initiator whether or not they're currently online. Reusable: binding
 * doesn't consume the ceremony item, and activating doesn't remove the trigger. */
public record CeremonyTrigger(UUID id, CeremonyTriggerLocation location, String templateId, UUID liegeGroupId,
                               PlayerRef installer, Instant installedAt) {
    public CeremonyTrigger {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(liegeGroupId, "liegeGroupId");
        Objects.requireNonNull(installer, "installer");
        Objects.requireNonNull(installedAt, "installedAt");
    }
}
