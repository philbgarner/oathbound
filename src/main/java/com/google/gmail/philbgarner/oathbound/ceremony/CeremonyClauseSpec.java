package com.google.gmail.philbgarner.oathbound.ceremony;

import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticState;

import java.util.Objects;

/** The template shape of one clause in a {@link CeremonyTemplateDefinition}, with placeholders (a
 * recipient's player name, a mob type name) instead of resolved refs - materialized into a real
 * {@code com.google.gmail.philbgarner.oathbound.oath.Clause} by {@link CeremonyService} once the
 * initiator/target/groups are known at accept-time. */
public sealed interface CeremonyClauseSpec {

    /** Territory escheats to the liege group once the target has died to another player
     * {@code pvpDeathCount} times - the subject/target groups are resolved at accept-time, not
     * configured here (see {@link CeremonyService#resolveSubjectGroup}). */
    record TransferSpec(int pvpDeathCount) implements CeremonyClauseSpec {
        public TransferSpec {
            if (pvpDeathCount <= 0) {
                throw new IllegalArgumentException("pvpDeathCount must be positive");
            }
        }
    }

    /** A one-time tribute of {@code quantity} of {@code materialName}, paid by the target to a named
     * existing player, released immediately once the oath is sealed. */
    record TributeSpec(String recipientPlayerName, String materialName, int quantity) implements CeremonyClauseSpec {
        public TributeSpec {
            Objects.requireNonNull(recipientPlayerName, "recipientPlayerName");
            Objects.requireNonNull(materialName, "materialName");
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }

    /** The target must kill {@code quantity} of {@code mobTypeName}. */
    record MobKillSpec(String mobTypeName, int quantity) implements CeremonyClauseSpec {
        public MobKillSpec {
            Objects.requireNonNull(mobTypeName, "mobTypeName");
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }

    /** Pure RP clause, no mechanical effect - same as {@code Clause.CustomFlagClause}. */
    record CustomFlagSpec(String text) implements CeremonyClauseSpec {
        public CustomFlagSpec {
            Objects.requireNonNull(text, "text");
        }
    }

    /** Sets the diplomatic relation between the liege group and the target's own personally-owned
     * territory group to {@code newState} the moment the oath activates - the same "target's territory"
     * resolved for {@link TransferSpec} (see {@link CeremonyService#resolveSubjectGroup}), reused here
     * since a ceremony has no separate way to name a second group. Both sides resolve to the root of
     * their ownership chain at execution time (see {@code diplomacy.DiplomacyService}), so this reads
     * naturally even when the target's own group is a minor vassal - the relation lands on whichever
     * liege actually holds diplomatic authority. {@link DiplomaticState#NEUTRAL} isn't a valid target
     * here, same restriction as the unilateral/treaty paths - there's nothing to "declare" back to
     * neutral. */
    record DiplomacySpec(DiplomaticState newState) implements CeremonyClauseSpec {
        public DiplomacySpec {
            Objects.requireNonNull(newState, "newState");
            if (newState == DiplomaticState.NEUTRAL) {
                throw new IllegalArgumentException("DiplomacySpec cannot target NEUTRAL");
            }
        }
    }
}
