package com.google.gmail.philbgarner.oathbound.honor;

import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Oath;

/** A rough stakes/severity scalar for an oath, used to scale Honor gain/loss - "a single large sacrifice
 * matters more than a trivial one," mirroring the same principle the Altar sacrifice system uses.
 * Item stakes have no scalar value system yet (same gap the Altar sacrifice ritual has), so this only
 * counts clause count and escrowed currency; a future valuation pass could fold item value in too. */
public final class OathSeverity {

    private OathSeverity() {
    }

    public static long of(Oath oath) {
        long severity = oath.clauses().size();
        for (Clause clause : oath.clauses()) {
            if (clause instanceof Clause.EscrowClause escrow) {
                severity += escrow.currency().values().stream().mapToLong(Long::longValue).sum();
            }
        }
        return Math.max(severity, 1L);
    }
}
