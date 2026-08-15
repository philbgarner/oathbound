package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;

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

    record ReleaseStep(double fraction, Condition condition) {
    }
}
