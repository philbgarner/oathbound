package com.google.gmail.philbgarner.oathbound.honor;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.Ledger;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HonorCalculatorTest {
    private final OathService oathService = new OathService(new Ledger());
    private final HonorCalculator calculator = new HonorCalculator(10L, 20L, 2.0);

    @Test
    void gainAndLossMatchTheConfiguredBasesForABareNonBloodOath() {
        Oath oath = draft(false);
        assertEquals(10L, calculator.fulfillGain(oath));
        assertEquals(20L, calculator.breachLoss(oath));
    }

    @Test
    void bloodOathAmplifiesBothGainAndLoss() {
        Oath oath = draft(true);
        assertEquals(20L, calculator.fulfillGain(oath));
        assertEquals(40L, calculator.breachLoss(oath));
    }

    @Test
    void breachLossIsALargerSwingThanFulfillGainForTheSameOath() {
        Oath oath = draft(false);
        assertTrue(calculator.breachLoss(oath) > calculator.fulfillGain(oath));
    }

    private Oath draft(boolean blood) {
        return oathService.createDraft(
                List.of(new PlayerRef(UUID.randomUUID()), new PlayerRef(UUID.randomUUID())), blood);
    }
}
