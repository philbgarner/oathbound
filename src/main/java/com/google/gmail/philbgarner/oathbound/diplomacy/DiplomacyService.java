package com.google.gmail.philbgarner.oathbound.diplomacy;

import com.google.gmail.philbgarner.oathbound.group.OwnershipResolver;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks war/peace/alliance relations between the *root* groups of ownership chains - a vassal group has
 * no relations of its own, only its most senior liege does (see {@link OwnershipResolver#resolveRootGroup}).
 * Every public method resolves its {@code UUID} arguments to their root group before touching state, so
 * callers never need to remember to do it themselves: passing a Duchy's id or its Kingdom's id always
 * produces the same answer. Mirrors {@code honor.HonorService}'s in-memory cache/persist shape.
 */
public final class DiplomacyService {

    private record PairKey(UUID a, UUID b) {
        static PairKey of(UUID x, UUID y) {
            return x.compareTo(y) <= 0 ? new PairKey(x, y) : new PairKey(y, x);
        }
    }

    private final OwnershipResolver ownershipResolver;
    private final Map<PairKey, DiplomaticRelation> relations = new ConcurrentHashMap<>();

    public DiplomacyService(OwnershipResolver ownershipResolver) {
        this.ownershipResolver = Objects.requireNonNull(ownershipResolver, "ownershipResolver");
    }

    /** Resolves {@code groupId} to the topmost group in its ownership chain - the group whose diplomatic
     * relations this one actually shares. */
    public UUID rootOf(UUID groupId) {
        Objects.requireNonNull(groupId, "groupId");
        return ownershipResolver.resolveRootGroup(new ProtectionGroupRef(groupId)).groupId();
    }

    /** Defaults to {@link DiplomaticState#NEUTRAL} for a pair with no recorded relation. */
    public DiplomaticState currentState(UUID groupA, UUID groupB) {
        DiplomaticRelation relation = relations.get(PairKey.of(rootOf(groupA), rootOf(groupB)));
        return relation == null ? DiplomaticState.NEUTRAL : relation.state();
    }

    /** The single mutation path - used identically by a unilateral war declaration and by a sealed
     * treaty's {@code DiplomacyClause} execution. */
    public DiplomaticRelation setState(UUID groupA, UUID groupB, DiplomaticState newState, Instant now) {
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(now, "now");
        UUID rootA = rootOf(groupA);
        UUID rootB = rootOf(groupB);
        PairKey key = PairKey.of(rootA, rootB);
        DiplomaticRelation relation = new DiplomaticRelation(key.a(), key.b(), newState, now);
        relations.put(key, relation);
        return relation;
    }

    /** Used when rehydrating from persistence at startup. */
    public void loadExisting(DiplomaticRelation relation) {
        Objects.requireNonNull(relation, "relation");
        relations.put(PairKey.of(relation.groupA(), relation.groupB()), relation);
    }

    public List<DiplomaticRelation> allRelations() {
        return new ArrayList<>(relations.values());
    }
}
