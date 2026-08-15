package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Periodically evaluates every {@code ACTIVE} oath's clauses against their conditions and executes
 * whichever are now satisfied - the piece of "full condition-engine wiring" that turns a met condition
 * into an actual effect rather than a value {@link ConditionEvaluator} merely computes.
 *
 * <p>{@link Clause.TransferClause} reassigns a {@link ProtectionGroup}'s owner via
 * {@link OwnershipResolver}. {@link Clause.EscrowClause} pays its currency straight into the recipient's
 * balance and, if it holds items, produces an {@link EscrowClaim} for them - items need a real inventory
 * to land in, which is a Bukkit concern this Bukkit-free engine deliberately doesn't touch, so they wait
 * to be claimed instead of being pushed anywhere. A release schedule with several steps fires atomically:
 * everything releases once every step's condition is simultaneously true (an implicit AND across the
 * whole schedule), not incrementally per step - true partial/incremental release execution is future
 * work. {@link Clause.CustomFlagClause} has no condition and no effect, so it never blocks anything.
 * {@link Clause.KillCountClause} isn't executed here - its resolution semantics belong to the not-yet-
 * built Bounty/Kill Contracts phase. An oath is only auto-carried to {@code FULFILLED} once every clause
 * it contains is one this engine can and did fully resolve - if it contains a kill-count clause, it's
 * deliberately left {@code ACTIVE} rather than falsely marked complete.
 */
public final class ConditionEngine {

    public record TickResult(List<Oath> changedOaths, List<EscrowClaim> newClaims) {
    }

    private final OathService oathService;
    private final OwnershipResolver ownershipResolver;
    private final EconomyService economyService;
    private final Function<UUID, Optional<ProtectionGroup>> groupLookup;
    private final DeathTracker deathTracker;
    private final ManualConfirmStore manualConfirms;
    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    public ConditionEngine(OathService oathService, OwnershipResolver ownershipResolver,
                            EconomyService economyService, Function<UUID, Optional<ProtectionGroup>> groupLookup,
                            DeathTracker deathTracker, ManualConfirmStore manualConfirms) {
        this.oathService = Objects.requireNonNull(oathService, "oathService");
        this.ownershipResolver = Objects.requireNonNull(ownershipResolver, "ownershipResolver");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.groupLookup = Objects.requireNonNull(groupLookup, "groupLookup");
        this.deathTracker = Objects.requireNonNull(deathTracker, "deathTracker");
        this.manualConfirms = Objects.requireNonNull(manualConfirms, "manualConfirms");
    }

    /** Evaluates every ACTIVE oath in {@code oaths}, returning the oaths that changed (for the caller to
     * persist) and any newly released escrow item batches. Oaths in any other state are ignored. */
    public TickResult tick(Collection<Oath> oaths, Instant now) {
        List<Oath> changedOaths = new ArrayList<>();
        List<EscrowClaim> newClaims = new ArrayList<>();
        for (Oath oath : oaths) {
            if (oath.state() == OathState.ACTIVE && tickOne(oath, now, newClaims)) {
                changedOaths.add(oath);
            }
        }
        return new TickResult(changedOaths, newClaims);
    }

    private boolean tickOne(Oath oath, Instant now, List<EscrowClaim> newClaims) {
        if (oath.activatedAt() == null) {
            // Legacy/rehydrated data from before activatedAt existed - nothing safe to evaluate against.
            return false;
        }

        boolean changed = false;
        boolean allAutoResolvableAndDone = true;
        List<Clause> clauses = oath.clauses();
        DomainConditionContext context = null;

        for (int i = 0; i < clauses.size(); i++) {
            Clause clause = clauses.get(i);
            if (clause instanceof Clause.CustomFlagClause) {
                continue;
            }
            if (oath.isClauseFulfilled(i)) {
                continue;
            }
            if (!(clause instanceof Clause.TransferClause) && !(clause instanceof Clause.EscrowClause)) {
                allAutoResolvableAndDone = false;
                continue;
            }
            if (context == null) {
                context = new DomainConditionContext(
                        deathTracker, manualConfirms, groupLookup, oath.id(), clauses, oath.activatedAt());
            }

            boolean resolved = switch (clause) {
                case Clause.TransferClause transfer ->
                        evaluator.evaluate(transfer.condition(), oath.activatedAt(), now, context)
                                && executeTransfer(transfer);
                case Clause.EscrowClause escrow ->
                        evaluator.evaluate(releaseCondition(escrow), oath.activatedAt(), now, context)
                                && executeEscrowRelease(oath, i, escrow, now, newClaims);
                default -> false;
            };

            if (resolved) {
                oathService.markClauseFulfilled(oath, i);
                changed = true;
            } else {
                allAutoResolvableAndDone = false;
            }
        }

        if (allAutoResolvableAndDone) {
            oathService.fulfill(oath, PlayerRef.SYSTEM);
            changed = true;
        }
        return changed;
    }

    /** The whole release schedule fires as one unit: every step's condition must hold at once. */
    private Condition releaseCondition(Clause.EscrowClause escrow) {
        List<Condition> stepConditions = escrow.releaseSchedule().stream().map(Clause.ReleaseStep::condition).toList();
        if (stepConditions.isEmpty()) {
            return new Condition.Immediate();
        }
        if (stepConditions.size() == 1) {
            return stepConditions.get(0);
        }
        return new Condition.Compound(Condition.CompoundOperator.AND, stepConditions);
    }

    private boolean executeTransfer(Clause.TransferClause transfer) {
        Optional<ProtectionGroup> subject = groupLookup.apply(transfer.subjectGroup().groupId());
        if (subject.isEmpty()) {
            return false;
        }
        try {
            ownershipResolver.reassignOwner(subject.get(), transfer.target());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean executeEscrowRelease(Oath oath, int clauseIndex, Clause.EscrowClause escrow, Instant now,
                                          List<EscrowClaim> newClaims) {
        // Validate every currency up front so a single unknown one can't leave a partial deposit behind
        // that would then be re-applied on a later retry.
        if (!economyService.currencies().containsAll(escrow.currency().keySet())) {
            return false;
        }
        for (Map.Entry<Currency, Long> entry : escrow.currency().entrySet()) {
            if (entry.getValue() > 0) {
                economyService.deposit(escrow.recipient(), entry.getKey(), entry.getValue());
            }
        }
        if (!escrow.items().isEmpty()) {
            newClaims.add(new EscrowClaim(UUID.randomUUID(), oath.id(), clauseIndex,
                    escrow.depositor(), escrow.recipient(), escrow.items(), now));
        }
        return true;
    }
}
