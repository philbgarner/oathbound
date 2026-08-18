package com.google.gmail.philbgarner.oathbound.ceremony;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link CeremonyTemplateDefinition#hasRealStakes()} drives the item-glint and trigger-block particle
 * warning ({@code bukkit.CeremonyItems}, {@code OathboundPlugin}) - covering it directly keeps that
 * warning honest independent of whichever GUI/listener code happens to call it. */
final class CeremonyTemplateDefinitionTest {

    private CeremonyTemplateDefinition template(CeremonyClauseSpec... clauses) {
        return new CeremonyTemplateDefinition("id", "Display", "PAPER", "Item",
                List.of("Do you accept?"), 60, false, List.of(clauses));
    }

    @Test
    void customFlagOnlyHasNoRealStakes() {
        assertFalse(template(new CeremonyClauseSpec.CustomFlagSpec("be nice")).hasRealStakes());
    }

    @Test
    void noClausesAtAllHasNoRealStakes() {
        assertFalse(template().hasRealStakes());
    }

    @Test
    void transferClauseHasRealStakes() {
        assertTrue(template(new CeremonyClauseSpec.TransferSpec(5)).hasRealStakes());
    }

    @Test
    void tributeClauseHasRealStakes() {
        assertTrue(template(new CeremonyClauseSpec.TributeSpec("Alaric", "WHEAT", 64)).hasRealStakes());
    }

    @Test
    void mixOfFlagAndRealClauseHasRealStakes() {
        assertTrue(template(new CeremonyClauseSpec.CustomFlagSpec("be nice"),
                new CeremonyClauseSpec.MobKillSpec("RAVAGER", 1)).hasRealStakes());
    }
}
