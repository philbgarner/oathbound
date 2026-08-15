package com.google.gmail.philbgarner.oathbound.honor;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Ledger;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class OathSeverityTest {
    private final OathService oathService = new OathService(new Ledger());

    @Test
    void bareOathHasAFloorSeverityOfOne() {
        assertEquals(1L, OathSeverity.of(draft()));
    }

    @Test
    void severityCountsOneUnitPerClause() {
        Oath oath = draft();
        oathService.addClause(oath, new Clause.CustomFlagClause("first"));
        oathService.addClause(oath, new Clause.CustomFlagClause("second"));
        assertEquals(2L, OathSeverity.of(oath));
    }

    @Test
    void severityAddsEscrowedCurrencyOnTopOfClauseCount() {
        Oath oath = draft();
        Currency coin = new Currency("coin");
        oathService.addClause(oath, new Clause.EscrowClause(
                new PlayerRef(UUID.randomUUID()), new PlayerRef(UUID.randomUUID()),
                List.of(), Map.of(coin, 50L), List.of()));
        assertEquals(51L, OathSeverity.of(oath));
    }

    private Oath draft() {
        return oathService.createDraft(
                List.of(new PlayerRef(UUID.randomUUID()), new PlayerRef(UUID.randomUUID())), false);
    }
}
