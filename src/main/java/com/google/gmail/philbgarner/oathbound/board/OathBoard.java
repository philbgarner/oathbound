package com.google.gmail.philbgarner.oathbound.board;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A physical, right-clickable board posting witnessed oath activity. {@code regionalGroup} is
 * {@code null} for a capital board (shows everything); otherwise it's scoped to that group's business -
 * see {@link com.google.gmail.philbgarner.oathbound.oath.OathBoardEligibility#isRegionallyRelevant}.
 */
public record OathBoard(UUID id, ProtectionGroupRef regionalGroup, BoardLocation location, PlayerRef installer,
                         Instant installedAt) {
    public OathBoard {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(installer, "installer");
        Objects.requireNonNull(installedAt, "installedAt");
    }

    public boolean isCapital() {
        return regionalGroup == null;
    }
}
