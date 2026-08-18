package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.bounty.Banishment;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomacyService;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticRelation;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;

import java.time.Duration;
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
 * {@link Clause.KillCountClause} resolves once {@link DeathTracker} confirms {@code quantity} deaths of
 * {@code target} since activation (reusing the same machinery {@link Condition.DeathCount} already
 * provides) - it has no execution side effect of its own, unlike Transfer/Escrow, so resolving its
 * condition <em>is</em> fulfilling it. An oath is only auto-carried to {@code FULFILLED} once every clause
 * it contains is one this engine can and did fully resolve.
 */
public final class ConditionEngine {

    public record TickResult(List<Oath> changedOaths, List<EscrowClaim> newClaims,
                              List<DiplomaticRelation> changedRelations, List<Banishment> changedBanishments) {
    }

    private final OathService oathService;
    private final OwnershipResolver ownershipResolver;
    private final EconomyService economyService;
    private final Function<UUID, Optional<ProtectionGroup>> groupLookup;
    private final DeathTracker deathTracker;
    private final MobKillTracker mobKillTracker;
    private final ManualConfirmStore manualConfirms;
    private final DiplomacyService diplomacyService;
    private final Function<PlayerRef, Optional<Banishment>> activeBanishmentLookup;
    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    public ConditionEngine(OathService oathService, OwnershipResolver ownershipResolver,
                            EconomyService economyService, Function<UUID, Optional<ProtectionGroup>> groupLookup,
                            DeathTracker deathTracker, MobKillTracker mobKillTracker, ManualConfirmStore manualConfirms,
                            DiplomacyService diplomacyService, Function<PlayerRef, Optional<Banishment>> activeBanishmentLookup) {
        this.oathService = Objects.requireNonNull(oathService, "oathService");
        this.ownershipResolver = Objects.requireNonNull(ownershipResolver, "ownershipResolver");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.groupLookup = Objects.requireNonNull(groupLookup, "groupLookup");
        this.deathTracker = Objects.requireNonNull(deathTracker, "deathTracker");
        this.mobKillTracker = Objects.requireNonNull(mobKillTracker, "mobKillTracker");
        this.manualConfirms = Objects.requireNonNull(manualConfirms, "manualConfirms");
        this.diplomacyService = Objects.requireNonNull(diplomacyService, "diplomacyService");
        this.activeBanishmentLookup = Objects.requireNonNull(activeBanishmentLookup, "activeBanishmentLookup");
    }

    /** Evaluates every ACTIVE oath in {@code oaths}, returning the oaths that changed (for the caller to
     * persist), any newly released escrow item batches, any diplomatic relations a sealed treaty clause
     * changed this tick, and any banishments a release clause reduced/forgave this tick. Oaths in any
     * other state are ignored. */
    public TickResult tick(Collection<Oath> oaths, Instant now) {
        List<Oath> changedOaths = new ArrayList<>();
        List<EscrowClaim> newClaims = new ArrayList<>();
        List<DiplomaticRelation> changedRelations = new ArrayList<>();
        List<Banishment> changedBanishments = new ArrayList<>();
        for (Oath oath : oaths) {
            if (oath.state() == OathState.ACTIVE
                    && tickOne(oath, now, newClaims, changedRelations, changedBanishments)) {
                changedOaths.add(oath);
            }
        }
        return new TickResult(changedOaths, newClaims, changedRelations, changedBanishments);
    }

    private boolean tickOne(Oath oath, Instant now, List<EscrowClaim> newClaims,
                             List<DiplomaticRelation> changedRelations, List<Banishment> changedBanishments) {
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
            if (!(clause instanceof Clause.TransferClause) && !(clause instanceof Clause.EscrowClause)
                    && !(clause instanceof Clause.KillCountClause) && !(clause instanceof Clause.MobKillClause)
                    && !(clause instanceof Clause.DiplomacyClause) && !(clause instanceof Clause.BanishmentReleaseClause)) {
                allAutoResolvableAndDone = false;
                continue;
            }
            if (context == null) {
                context = new DomainConditionContext(
                        deathTracker, mobKillTracker, manualConfirms, groupLookup, oath.id(), clauses, oath.activatedAt());
            }

            boolean resolved = switch (clause) {
                case Clause.TransferClause transfer ->
                        evaluator.evaluate(transfer.condition(), oath.activatedAt(), now, context)
                                && executeTransfer(transfer);
                case Clause.EscrowClause escrow ->
                        evaluator.evaluate(releaseCondition(escrow), oath.activatedAt(), now, context)
                                && executeEscrowRelease(oath, i, escrow, now, newClaims);
                case Clause.KillCountClause killCount ->
                        evaluator.evaluate(new Condition.DeathCount(killCount.target(), killCount.quantity()),
                                oath.activatedAt(), now, context);
                case Clause.MobKillClause mobKill ->
                        evaluator.evaluate(new Condition.MobKillCount(mobKill.obligor(), mobKill.mobTypeName(), mobKill.quantity()),
                                oath.activatedAt(), now, context);
                case Clause.DiplomacyClause diplomacy ->
                        evaluator.evaluate(diplomacy.condition(), oath.activatedAt(), now, context)
                                && executeDiplomacy(diplomacy, now, changedRelations);
                case Clause.BanishmentReleaseClause release ->
                        evaluator.evaluate(release.condition(), oath.activatedAt(), now, context)
                                && executeBanishmentRelease(release, now, changedBanishments);
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

    private boolean executeDiplomacy(Clause.DiplomacyClause diplomacy, Instant now, List<DiplomaticRelation> changedRelations) {
        DiplomaticRelation relation = diplomacyService.setState(diplomacy.groupA().groupId(),
                diplomacy.groupB().groupId(), diplomacy.newState(), now);
        changedRelations.add(relation);
        return true;
    }

    /** Resolves quietly (returns {@code true}, nothing to do) if {@code target} isn't currently serving an
     * active sentence - a release oath sealed before the sentence starts, or one that's simply outlived
     * by an already-expired/forgiven sentence, shouldn't hang the oath forever waiting on nothing. */
    private boolean executeBanishmentRelease(Clause.BanishmentReleaseClause release, Instant now,
                                              List<Banishment> changedBanishments) {
        Optional<Banishment> maybeBanishment = activeBanishmentLookup.apply(release.target());
        if (maybeBanishment.isEmpty() || !maybeBanishment.get().active(now)) {
            return true;
        }
        Banishment banishment = maybeBanishment.get();
        Duration reduction = release.fullRelease()
                ? Duration.between(now, banishment.releaseAt())
                : release.reduction();
        banishment.reduceSentence(reduction, now);
        changedBanishments.add(banishment);
        return true;
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
