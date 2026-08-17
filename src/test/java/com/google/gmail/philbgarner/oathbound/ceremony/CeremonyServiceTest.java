package com.google.gmail.philbgarner.oathbound.ceremony;

import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Condition;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CeremonyServiceTest {

    private final PlayerRef king = new PlayerRef(UUID.randomUUID());
    private final PlayerRef vassal = new PlayerRef(UUID.randomUUID());
    private final PlayerRef otherRecipient = new PlayerRef(UUID.randomUUID());
    private final List<ProtectionGroup> groups = new ArrayList<>();
    private final CeremonyService service = new CeremonyService(() -> groups);

    private ProtectionGroup group(EntityRef owner) {
        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "Territory", owner, GroupTier.INDIVIDUAL);
        groups.add(group);
        return group;
    }

    @Test
    void resolveSubjectGroupReturnsTheSolePersonallyOwnedGroup() {
        group(king); // a decoy owned by someone else
        ProtectionGroup territory = group(vassal);

        ProtectionGroupRef resolved = service.resolveSubjectGroup(vassal);

        assertEquals(territory.id(), resolved.groupId());
    }

    @Test
    void resolveSubjectGroupThrowsWhenTargetOwnsNoGroup() {
        assertThrows(CeremonyValidationException.class, () -> service.resolveSubjectGroup(vassal));
    }

    @Test
    void resolveSubjectGroupThrowsWhenTargetOwnsMultipleGroups() {
        group(vassal);
        group(vassal);

        assertThrows(CeremonyValidationException.class, () -> service.resolveSubjectGroup(vassal));
    }

    @Test
    void resolveSubjectGroupIgnoresGroupsOwnedThroughAnotherGroup() {
        ProtectionGroup kingdom = group(king);
        // vassal's territory is nested under the kingdom, not personally owned - shouldn't match.
        group(new ProtectionGroupRef(kingdom.id()));

        assertThrows(CeremonyValidationException.class, () -> service.resolveSubjectGroup(vassal));
    }

    @Test
    void materializeBuildsATransferClauseGatedOnPvpDeathCount() {
        CeremonyTemplateDefinition template = template(new CeremonyClauseSpec.TransferSpec(5));
        ProtectionGroupRef liege = new ProtectionGroupRef(UUID.randomUUID());
        ProtectionGroupRef subject = new ProtectionGroupRef(UUID.randomUUID());

        List<Clause> clauses = service.materialize(template, vassal, liege, subject, Map.of(), name -> null);

        assertEquals(1, clauses.size());
        Clause.TransferClause transfer = (Clause.TransferClause) clauses.get(0);
        assertEquals(liege, transfer.target());
        assertEquals(subject, transfer.subjectGroup());
        assertEquals(new Condition.PvpDeathCount(vassal, 5), transfer.condition());
    }

    @Test
    void materializeBuildsAnEscrowClauseForATributeSpec() {
        CeremonyClauseSpec.TributeSpec tribute = new CeremonyClauseSpec.TributeSpec("Alaric", "WHEAT", 64);
        CeremonyTemplateDefinition template = template(tribute);
        SerializedItemStack item = new SerializedItemStack(new byte[] {1});

        List<Clause> clauses = service.materialize(template, vassal, new ProtectionGroupRef(UUID.randomUUID()), null,
                Map.of(tribute, item), name -> name.equals("Alaric") ? otherRecipient : null);

        assertEquals(1, clauses.size());
        Clause.EscrowClause escrow = (Clause.EscrowClause) clauses.get(0);
        assertEquals(vassal, escrow.depositor());
        assertEquals(otherRecipient, escrow.recipient());
        assertEquals(List.of(item), escrow.items());
        assertTrue(escrow.currency().isEmpty());
        assertEquals(1, escrow.releaseSchedule().size());
        assertEquals(new Condition.Immediate(), escrow.releaseSchedule().get(0).condition());
    }

    @Test
    void materializeThrowsWhenATributeItemWasNotPreResolved() {
        CeremonyClauseSpec.TributeSpec tribute = new CeremonyClauseSpec.TributeSpec("Alaric", "WHEAT", 64);
        CeremonyTemplateDefinition template = template(tribute);

        assertThrows(CeremonyValidationException.class, () -> service.materialize(template, vassal,
                new ProtectionGroupRef(UUID.randomUUID()), null, Map.of(), name -> otherRecipient));
    }

    @Test
    void materializeThrowsWhenATributeRecipientDoesNotResolve() {
        CeremonyClauseSpec.TributeSpec tribute = new CeremonyClauseSpec.TributeSpec("Alaric", "WHEAT", 64);
        CeremonyTemplateDefinition template = template(tribute);
        SerializedItemStack item = new SerializedItemStack(new byte[] {1});

        assertThrows(CeremonyValidationException.class, () -> service.materialize(template, vassal,
                new ProtectionGroupRef(UUID.randomUUID()), null, Map.of(tribute, item), name -> null));
    }

    @Test
    void materializeBuildsAMobKillClause() {
        CeremonyTemplateDefinition template = template(new CeremonyClauseSpec.MobKillSpec("RAVAGER", 1));

        List<Clause> clauses = service.materialize(template, vassal, new ProtectionGroupRef(UUID.randomUUID()), null,
                Map.of(), name -> null);

        assertEquals(1, clauses.size());
        assertEquals(new Clause.MobKillClause(vassal, "RAVAGER", 1), clauses.get(0));
    }

    @Test
    void materializeBuildsACustomFlagClause() {
        CeremonyTemplateDefinition template = template(new CeremonyClauseSpec.CustomFlagSpec("be honorable"));

        List<Clause> clauses = service.materialize(template, vassal, new ProtectionGroupRef(UUID.randomUUID()), null,
                Map.of(), name -> null);

        assertEquals(List.of(new Clause.CustomFlagClause("be honorable")), clauses);
    }

    private CeremonyTemplateDefinition template(CeremonyClauseSpec... clauses) {
        return new CeremonyTemplateDefinition("fealty", "Oath of Fealty", "NETHERITE_SWORD", "Scepter of Avalon",
                List.of("Do you agree?"), List.of("i do", "yes"), List.of("no"), 60, false, List.of(clauses));
    }
}
