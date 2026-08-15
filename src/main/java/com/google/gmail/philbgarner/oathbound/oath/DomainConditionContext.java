package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Real {@link ConditionContext} backend for {@link ConditionEngine}, scoped to one oath's evaluation.
 * Death counts, manual confirmations, and payments are live; ballot tracking depends on the not-yet-built
 * Election Oaths system and is an honest no-op stub until that lands, same as {@link ConditionContext}'s
 * own seam javadoc already documents.
 */
final class DomainConditionContext implements ConditionContext {

    private final DeathTracker deathTracker;
    private final ManualConfirmStore manualConfirms;
    private final Function<UUID, Optional<ProtectionGroup>> groupLookup;
    private final UUID oathId;
    private final List<Clause> clauses;
    private final Instant since;

    DomainConditionContext(DeathTracker deathTracker, ManualConfirmStore manualConfirms,
                            Function<UUID, Optional<ProtectionGroup>> groupLookup, UUID oathId,
                            List<Clause> clauses, Instant since) {
        this.deathTracker = Objects.requireNonNull(deathTracker, "deathTracker");
        this.manualConfirms = Objects.requireNonNull(manualConfirms, "manualConfirms");
        this.groupLookup = Objects.requireNonNull(groupLookup, "groupLookup");
        this.oathId = Objects.requireNonNull(oathId, "oathId");
        this.clauses = Objects.requireNonNull(clauses, "clauses");
        this.since = Objects.requireNonNull(since, "since");
    }

    @Override
    public int deathCount(EntityRef target) {
        return switch (target) {
            case PlayerRef player -> deathTracker.countSince(player, since);
            case ProtectionGroupRef groupRef -> groupLookup.apply(groupRef.groupId())
                    .map(group -> group.members().stream()
                            .mapToInt(member -> deathTracker.countSince(member.player(), since))
                            .sum())
                    .orElse(0);
        };
    }

    /** Sums how much of {@code currency} this oath's own escrow clauses hold - deposited items/currency
     * are withdrawn from the depositor the instant the clause is created, so "paid" from a gating
     * condition's point of view means "committed to this oath's escrow," not "released to the recipient." */
    @Override
    public long amountPaid(Currency currency) {
        long total = 0L;
        for (Clause clause : clauses) {
            if (clause instanceof Clause.EscrowClause escrow) {
                total += escrow.currency().getOrDefault(currency, 0L);
            }
        }
        return total;
    }

    @Override
    public boolean isBallotDecided(UUID ballotRef) {
        return false;
    }

    @Override
    public boolean isManuallyConfirmed(PlayerRef party) {
        return manualConfirms.isConfirmed(oathId, party);
    }
}
