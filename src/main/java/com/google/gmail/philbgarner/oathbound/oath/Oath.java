package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mutation beyond construction/reconstruction is package-private: state transitions and clause/witness
 * additions are only meant to happen through {@link OathService}, which enforces lifecycle rules.
 */
public final class Oath {
    private final UUID id;
    private final List<PlayerRef> parties;
    private final List<PlayerRef> witnesses = new ArrayList<>();
    private final List<Clause> clauses = new ArrayList<>();
    private final boolean bloodOath;
    private final boolean open;
    private final Instant createdAt;
    private final Set<Integer> fulfilledClauseIndices = new HashSet<>();
    private OathState state = OathState.DRAFT;
    private Instant sealedAt;
    private Instant activatedAt;
    private Instant resolvedAt;
    private UUID negotiationRef;

    public Oath(UUID id, List<PlayerRef> parties, boolean bloodOath) {
        this(id, parties, bloodOath, false, Instant.now());
    }

    /** An open oath starts with a single party; its second slot is filled by whoever accepts it. */
    public static Oath openDraft(UUID id, PlayerRef creator, boolean bloodOath) {
        return new Oath(id, List.of(creator), bloodOath, true, Instant.now());
    }

    private Oath(UUID id, List<PlayerRef> parties, boolean bloodOath, boolean open, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.parties = new ArrayList<>(Objects.requireNonNull(parties, "parties"));
        this.bloodOath = bloodOath;
        this.open = open;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Rehydrates a persisted Oath, bypassing the DRAFT-only construction path used for new oaths. */
    public static Oath reconstruct(UUID id, List<PlayerRef> parties, List<PlayerRef> witnesses, List<Clause> clauses,
                                    OathState state, boolean bloodOath, boolean open, Instant createdAt,
                                    Instant sealedAt, Instant activatedAt, Instant resolvedAt, UUID negotiationRef,
                                    Set<Integer> fulfilledClauseIndices) {
        Oath oath = new Oath(id, parties, bloodOath, open, createdAt);
        oath.witnesses.addAll(witnesses);
        oath.clauses.addAll(clauses);
        oath.state = state;
        oath.sealedAt = sealedAt;
        oath.activatedAt = activatedAt;
        oath.resolvedAt = resolvedAt;
        oath.negotiationRef = negotiationRef;
        oath.fulfilledClauseIndices.addAll(fulfilledClauseIndices);
        return oath;
    }

    public UUID id() {
        return id;
    }

    public List<PlayerRef> parties() {
        return List.copyOf(parties);
    }

    public List<PlayerRef> witnesses() {
        return List.copyOf(witnesses);
    }

    public List<Clause> clauses() {
        return List.copyOf(clauses);
    }

    public OathState state() {
        return state;
    }

    public boolean bloodOath() {
        return bloodOath;
    }

    public boolean open() {
        return open;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant sealedAt() {
        return sealedAt;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public UUID negotiationRef() {
        return negotiationRef;
    }

    public Set<Integer> fulfilledClauseIndices() {
        return Set.copyOf(fulfilledClauseIndices);
    }

    public boolean isClauseFulfilled(int clauseIndex) {
        return fulfilledClauseIndices.contains(clauseIndex);
    }

    void addClause(Clause clause) {
        clauses.add(Objects.requireNonNull(clause, "clause"));
    }

    void addParty(PlayerRef party) {
        parties.add(Objects.requireNonNull(party, "party"));
    }

    void addWitness(PlayerRef witness) {
        witnesses.add(Objects.requireNonNull(witness, "witness"));
    }

    void setState(OathState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    void setSealedAt(Instant sealedAt) {
        this.sealedAt = sealedAt;
    }

    void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    void setNegotiationRef(UUID negotiationRef) {
        this.negotiationRef = negotiationRef;
    }

    void markClauseFulfilled(int clauseIndex) {
        if (clauseIndex < 0 || clauseIndex >= clauses.size()) {
            throw new IndexOutOfBoundsException("No clause at index " + clauseIndex + " on oath " + id);
        }
        fulfilledClauseIndices.add(clauseIndex);
    }
}
