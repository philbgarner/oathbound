package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticState;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public sealed interface Clause {

    /** Reassigns {@code subjectGroup}'s owner field to {@code target} once {@code condition} is met. */
    record TransferClause(EntityRef target, ProtectionGroupRef subjectGroup, Condition condition) implements Clause {
    }

    /** Fully fleshed out here; item/currency (de)serialization and release-payout execution land in the Escrow phase. */
    record EscrowClause(PlayerRef depositor, PlayerRef recipient, List<SerializedItemStack> items,
                         Map<Currency, Long> currency, List<ReleaseStep> releaseSchedule) implements Clause {
        public EscrowClause {
            items = List.copyOf(items);
            currency = Map.copyOf(currency);
            releaseSchedule = List.copyOf(releaseSchedule);
        }
    }

    /** Pure RP record - no mechanical effect. */
    record CustomFlagClause(String text) implements Clause {
    }

    /** Stub: fully wired in the Bounty/Kill Contracts phase (heat-scaling fees, banishment, head-return fulfillment). */
    record KillCountClause(EntityRef target, int quantity) implements Clause {
    }

    /** Resolves once {@code obligor} kills {@code quantity} of {@code mobTypeName} - same shape as
     * {@link KillCountClause}, resolving the condition <em>is</em> fulfilling it, no separate execute
     * step. {@code mobTypeName} is a plain {@code org.bukkit.entity.EntityType} name rather than the
     * enum itself, since this package is Bukkit-free by design. */
    record MobKillClause(PlayerRef obligor, String mobTypeName, int quantity) implements Clause {
    }

    /** Forces a diplomatic state change between two groups once {@code condition} is met - the diplomacy
     * counterpart to {@link TransferClause}'s ownership reassignment. {@code groupA}/{@code groupB} may
     * name a liege or a vassal interchangeably - execution always resolves to the root of each chain (see
     * {@code diplomacy.DiplomacyService}), since diplomatic authority belongs to the most senior group in
     * an ownership chain, not any vassal under it. */
    record DiplomacyClause(ProtectionGroupRef groupA, ProtectionGroupRef groupB, DiplomaticState newState,
                            Condition condition) implements Clause {
    }

    record ReleaseStep(double fraction, Condition condition) {
    }

    /** Reduces (or fully forgives) {@code target}'s active banishment sentence once {@code condition} is
     * met - the "release oath" the master plan describes: an ally negotiates with whoever holds authority
     * over the sentence via the normal Oath system, rather than the sentence being a pure fire-and-forget
     * timer. This is the Oath-side caller for the {@code Banishment.reduceSentence} seam that was left
     * deliberately unused until this clause existed to drive it - see that method's javadoc.
     *
     * <p>{@code condition} decides what the negotiation actually requires - {@code Immediate} for a
     * pre-agreed pardon, {@code ManualConfirm} for needing the placer's live approval, or a sibling
     * {@code EscrowClause}/{@code PaymentReceived} for a ransom-gated release - this clause doesn't
     * hardcode that, same as {@link TransferClause} and {@link DiplomacyClause} don't hardcode theirs.
     * {@code target} does not need to be a party to the oath - the ally negotiates on their behalf.
     *
     * @param reduction   ignored when {@code fullRelease} is true; otherwise the amount cut from the
     *                    remaining sentence (never past "already free" - see {@code reduceSentence}).
     * @param fullRelease when true, the entire remaining sentence is forgiven regardless of {@code reduction}.
     */
    record BanishmentReleaseClause(PlayerRef target, Duration reduction, boolean fullRelease, Condition condition)
            implements Clause {
    }
}
