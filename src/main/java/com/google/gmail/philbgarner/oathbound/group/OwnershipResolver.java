package com.google.gmail.philbgarner.oathbound.group;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Live-walks ProtectionGroup owner chains with a fixed depth cutoff, and enforces cycle prevention
 * at write-time (never at read-time, per design) via {@link #reassignOwner}.
 */
public final class OwnershipResolver {

    @FunctionalInterface
    public interface GroupLookup {
        Optional<ProtectionGroup> findById(UUID groupId);
    }

    public sealed interface Resolution permits Resolved, Unresolved {
    }

    public record Resolved(EntityRef terminalOwner, int hops) implements Resolution {
    }

    public record Unresolved(String reason) implements Resolution {
    }

    private final GroupLookup lookup;
    private final int depthCutoff;

    public OwnershipResolver(GroupLookup lookup, int depthCutoff) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        if (depthCutoff < 1) {
            throw new IllegalArgumentException("depthCutoff must be >= 1");
        }
        this.depthCutoff = depthCutoff;
    }

    public Resolution resolveTerminalOwner(EntityRef start) {
        Objects.requireNonNull(start, "start");
        EntityRef current = start;
        int hops = 0;
        while (current instanceof ProtectionGroupRef groupRef) {
            if (hops >= depthCutoff) {
                return new Unresolved("Depth cutoff (" + depthCutoff + ") exceeded resolving owner chain starting at " + start);
            }
            Optional<ProtectionGroup> group = lookup.findById(groupRef.groupId());
            if (group.isEmpty()) {
                return new Unresolved("Group " + groupRef.groupId() + " not found while resolving owner chain");
            }
            current = group.get().owner();
            hops++;
        }
        return new Resolved(current, hops);
    }

    /**
     * Walks {@code start}'s owner chain and returns the topmost {@link ProtectionGroup} in it - the last
     * group before the chain's owner stops being another group (a player, or the chain can't be resolved
     * further). This is what "a vassal's diplomatic relations are its liege's relations, all the way up"
     * resolves through (see the {@code diplomacy} package). Fail-safe like {@link #resolveTerminalOwner}:
     * an unresolvable group or a chain that hits the depth cutoff just returns wherever it stopped, rather
     * than silently escalating authority through a broken or cyclic chain.
     */
    public ProtectionGroupRef resolveRootGroup(ProtectionGroupRef start) {
        Objects.requireNonNull(start, "start");
        ProtectionGroupRef current = start;
        int hops = 0;
        while (hops < depthCutoff) {
            Optional<ProtectionGroup> group = lookup.findById(current.groupId());
            if (group.isEmpty()) {
                return current;
            }
            if (!(group.get().owner() instanceof ProtectionGroupRef parentRef)) {
                return current;
            }
            current = parentRef;
            hops++;
        }
        return current;
    }

    /**
     * True if assigning {@code proposedOwner} as {@code groupBeingAssigned}'s owner would make the group
     * its own ancestor. Also true (fail-safe reject, not a silent allow) if the chain can't be proven
     * cycle-free within the depth cutoff.
     */
    public boolean wouldCreateCycle(ProtectionGroupRef groupBeingAssigned, EntityRef proposedOwner) {
        Objects.requireNonNull(groupBeingAssigned, "groupBeingAssigned");
        Objects.requireNonNull(proposedOwner, "proposedOwner");
        EntityRef current = proposedOwner;
        int hops = 0;
        while (current instanceof ProtectionGroupRef groupRef) {
            if (groupRef.groupId().equals(groupBeingAssigned.groupId())) {
                return true;
            }
            if (hops >= depthCutoff) {
                return true;
            }
            Optional<ProtectionGroup> group = lookup.findById(groupRef.groupId());
            if (group.isEmpty()) {
                return false;
            }
            current = group.get().owner();
            hops++;
        }
        return false;
    }

    public void reassignOwner(ProtectionGroup group, EntityRef newOwner) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(newOwner, "newOwner");
        if (wouldCreateCycle(new ProtectionGroupRef(group.id()), newOwner)) {
            throw new IllegalArgumentException(
                    "Ownership assignment would make group " + group.id() + " its own ancestor: " + newOwner);
        }
        group.setOwner(newOwner);
    }
}
