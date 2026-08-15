package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A batch of items released from an {@link Clause.EscrowClause} once its release schedule fired,
 * waiting to be claimed rather than pushed directly to an inventory (items need a real delivery target,
 * which is a Bukkit concern this Bukkit-free domain layer deliberately doesn't touch).
 *
 * <p>{@link #holder()} is normally the clause's recipient. If it goes unclaimed past the configured
 * expiry window, {@link #expire()} flips the holder to the original depositor instead of attempting
 * delivery itself - the same claim GUI just starts listing it under the depositor's name. Escheat-to-
 * Notary and breach-split abandonment policies from the master plan aren't implemented since neither
 * a Notary nor a breach-split system exists yet; "return to depositor" is the only policy for now.
 */
public final class EscrowClaim {
    private final UUID id;
    private final UUID oathId;
    private final int clauseIndex;
    private final PlayerRef depositor;
    private final PlayerRef recipient;
    private final List<SerializedItemStack> items;
    private final Instant releasedAt;
    private boolean claimed;
    private boolean expired;

    public EscrowClaim(UUID id, UUID oathId, int clauseIndex, PlayerRef depositor, PlayerRef recipient,
                        List<SerializedItemStack> items, Instant releasedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.oathId = Objects.requireNonNull(oathId, "oathId");
        this.clauseIndex = clauseIndex;
        this.depositor = Objects.requireNonNull(depositor, "depositor");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.releasedAt = Objects.requireNonNull(releasedAt, "releasedAt");
    }

    /** Rehydrates a persisted claim, including in-progress or resolved state. */
    public static EscrowClaim reconstruct(UUID id, UUID oathId, int clauseIndex, PlayerRef depositor,
                                           PlayerRef recipient, List<SerializedItemStack> items, Instant releasedAt,
                                           boolean claimed, boolean expired) {
        EscrowClaim claim = new EscrowClaim(id, oathId, clauseIndex, depositor, recipient, items, releasedAt);
        claim.claimed = claimed;
        claim.expired = expired;
        return claim;
    }

    public UUID id() {
        return id;
    }

    public UUID oathId() {
        return oathId;
    }

    public int clauseIndex() {
        return clauseIndex;
    }

    public PlayerRef depositor() {
        return depositor;
    }

    public PlayerRef recipient() {
        return recipient;
    }

    public List<SerializedItemStack> items() {
        return items;
    }

    public Instant releasedAt() {
        return releasedAt;
    }

    public boolean claimed() {
        return claimed;
    }

    public boolean expired() {
        return expired;
    }

    /** Who can currently claim this - the recipient, unless it's expired back to the depositor. */
    public PlayerRef holder() {
        return expired ? depositor : recipient;
    }

    public void claim() {
        if (claimed) {
            throw new IllegalStateException("Escrow claim " + id + " was already claimed");
        }
        claimed = true;
    }

    public void expire() {
        if (claimed) {
            throw new IllegalStateException("Escrow claim " + id + " was already claimed - cannot expire it");
        }
        expired = true;
    }
}
