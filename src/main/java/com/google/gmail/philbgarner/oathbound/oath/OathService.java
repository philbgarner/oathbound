package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the Oath lifecycle state machine and its Ledger entries. Deliberately does not execute clause
 * effects (transfers, escrow releases) - that orchestration is added once the Condition engine is
 * fully wired in a later phase.
 */
public final class OathService {

    private static final Map<OathState, Set<OathState>> TRANSITIONS = Map.of(
            OathState.DRAFT, Set.of(OathState.PROPOSED, OathState.VOIDED),
            OathState.PROPOSED, Set.of(OathState.SEALED, OathState.VOIDED),
            OathState.SEALED, Set.of(OathState.ACTIVE),
            OathState.ACTIVE, Set.of(OathState.FULFILLED, OathState.BROKEN, OathState.VOIDED),
            OathState.FULFILLED, Set.of(),
            OathState.BROKEN, Set.of(),
            OathState.VOIDED, Set.of()
    );

    private final Ledger ledger;

    public OathService(Ledger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public Oath createDraft(List<PlayerRef> parties, boolean bloodOath) {
        Objects.requireNonNull(parties, "parties");
        if (parties.size() < 2) {
            throw new IllegalArgumentException("An oath requires at least two parties");
        }
        return new Oath(UUID.randomUUID(), parties, bloodOath);
    }

    /** An open oath has no named counterparty yet - anyone can fill the second slot via {@link #acceptOpen}. */
    public Oath createOpenDraft(PlayerRef creator, boolean bloodOath) {
        Objects.requireNonNull(creator, "creator");
        return Oath.openDraft(UUID.randomUUID(), creator, bloodOath);
    }

    /** Fills an open oath's second party slot and carries it straight through SEALED into ACTIVE. */
    public void acceptOpen(Oath oath, PlayerRef acceptor) {
        Objects.requireNonNull(oath, "oath");
        Objects.requireNonNull(acceptor, "acceptor");
        if (!oath.open()) {
            throw new IllegalStateException("Oath " + oath.id() + " is not an open contract");
        }
        if (oath.state() != OathState.PROPOSED) {
            throw new OathTransitionException(oath.id(), oath.state(), OathState.SEALED);
        }
        if (oath.parties().size() != 1) {
            throw new IllegalStateException("Oath " + oath.id() + " already has its second party filled");
        }
        if (oath.parties().contains(acceptor)) {
            throw new IllegalArgumentException("Cannot accept your own open contract");
        }
        oath.addParty(acceptor);
        transition(oath, OathState.SEALED, acceptor);
        transition(oath, OathState.ACTIVE, acceptor);
    }

    public void addClause(Oath oath, Clause clause) {
        requireState(oath, OathState.DRAFT);
        oath.addClause(clause);
    }

    public void addWitness(Oath oath, PlayerRef witness) {
        requireState(oath, OathState.DRAFT);
        oath.addWitness(witness);
    }

    public void propose(Oath oath, PlayerRef actor) {
        transition(oath, OathState.PROPOSED, actor);
    }

    public void seal(Oath oath, PlayerRef actor) {
        transition(oath, OathState.SEALED, actor);
    }

    public void activate(Oath oath, PlayerRef actor) {
        transition(oath, OathState.ACTIVE, actor);
    }

    public void fulfill(Oath oath, PlayerRef actor) {
        transition(oath, OathState.FULFILLED, actor);
    }

    public void breach(Oath oath, PlayerRef actor) {
        transition(oath, OathState.BROKEN, actor);
    }

    public void voidOath(Oath oath, PlayerRef actor) {
        transition(oath, OathState.VOIDED, actor);
    }

    private void requireState(Oath oath, OathState expected) {
        if (oath.state() != expected) {
            throw new OathTransitionException(oath.id(), oath.state(), expected);
        }
    }

    private void transition(Oath oath, OathState target, PlayerRef actor) {
        OathState current = oath.state();
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new OathTransitionException(oath.id(), current, target);
        }
        oath.setState(target);
        Instant now = Instant.now();
        if (target == OathState.SEALED) {
            oath.setSealedAt(now);
        }
        if (target == OathState.FULFILLED || target == OathState.BROKEN || target == OathState.VOIDED) {
            oath.setResolvedAt(now);
        }
        ledger.record(oath.id(), current, target, actor);
    }
}
