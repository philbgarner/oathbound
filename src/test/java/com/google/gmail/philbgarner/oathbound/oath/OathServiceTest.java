package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OathServiceTest {

    private final PlayerRef alice = new PlayerRef(UUID.randomUUID());
    private final PlayerRef bob = new PlayerRef(UUID.randomUUID());
    private Ledger ledger;
    private OathService service;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        service = new OathService(ledger);
    }

    @Test
    void happyPathReachesFulfilled() {
        Oath oath = service.createDraft(List.of(alice, bob), false);
        assertEquals(OathState.DRAFT, oath.state());

        service.addClause(oath, new Clause.CustomFlagClause("do the thing"));
        service.propose(oath, alice);
        assertEquals(OathState.PROPOSED, oath.state());

        service.seal(oath, bob);
        assertEquals(OathState.SEALED, oath.state());
        assertTrue(oath.sealedAt() != null);

        service.activate(oath, bob);
        assertEquals(OathState.ACTIVE, oath.state());
        assertTrue(oath.activatedAt() != null);

        service.fulfill(oath, alice);
        assertEquals(OathState.FULFILLED, oath.state());
        assertTrue(oath.resolvedAt() != null);
    }

    @Test
    void markClauseFulfilledRequiresActiveStateAndAValidIndex() {
        Oath oath = service.createDraft(List.of(alice, bob), false);
        service.addClause(oath, new Clause.CustomFlagClause("do the thing"));
        assertThrows(OathTransitionException.class, () -> service.markClauseFulfilled(oath, 0));

        service.propose(oath, alice);
        service.seal(oath, bob);
        service.activate(oath, bob);

        assertThrows(IndexOutOfBoundsException.class, () -> service.markClauseFulfilled(oath, 1));

        service.markClauseFulfilled(oath, 0);
        assertTrue(oath.isClauseFulfilled(0));
    }

    @Test
    void createDraftRequiresAtLeastTwoParties() {
        assertThrows(IllegalArgumentException.class, () -> service.createDraft(List.of(alice), false));
    }

    @Test
    void illegalTransitionIsRejected() {
        Oath oath = service.createDraft(List.of(alice, bob), false);
        assertThrows(OathTransitionException.class, () -> service.seal(oath, alice));
        assertThrows(OathTransitionException.class, () -> service.activate(oath, alice));
        assertThrows(OathTransitionException.class, () -> service.fulfill(oath, alice));
    }

    @Test
    void clausesAndWitnessesCanOnlyBeAddedInDraft() {
        Oath oath = service.createDraft(List.of(alice, bob), false);
        service.propose(oath, alice);
        assertThrows(OathTransitionException.class,
                () -> service.addClause(oath, new Clause.CustomFlagClause("too late")));
        assertThrows(OathTransitionException.class, () -> service.addWitness(oath, bob));
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        Oath oath = service.createDraft(List.of(alice, bob), false);
        service.propose(oath, alice);
        service.seal(oath, bob);
        service.activate(oath, bob);
        service.fulfill(oath, alice);

        assertThrows(OathTransitionException.class, () -> service.breach(oath, alice));
        assertThrows(OathTransitionException.class, () -> service.voidOath(oath, alice));
        assertThrows(OathTransitionException.class, () -> service.propose(oath, alice));
    }

    @Test
    void voidedReachableFromDraftProposedAndActiveButNotFromTerminalStates() {
        Oath fromDraft = service.createDraft(List.of(alice, bob), false);
        service.voidOath(fromDraft, alice);
        assertEquals(OathState.VOIDED, fromDraft.state());

        Oath fromProposed = service.createDraft(List.of(alice, bob), false);
        service.propose(fromProposed, alice);
        service.voidOath(fromProposed, alice);
        assertEquals(OathState.VOIDED, fromProposed.state());

        Oath fromActive = service.createDraft(List.of(alice, bob), false);
        service.propose(fromActive, alice);
        service.seal(fromActive, bob);
        service.activate(fromActive, bob);
        service.voidOath(fromActive, alice);
        assertEquals(OathState.VOIDED, fromActive.state());
    }

    @Test
    void everyTransitionIsRecordedInTheLedger() {
        Oath oath = service.createDraft(List.of(alice, bob), false);
        service.propose(oath, alice);
        service.seal(oath, bob);

        List<LedgerEntry> entries = ledger.entriesFor(oath.id());
        assertEquals(2, entries.size());
        assertEquals(OathState.DRAFT, entries.get(0).fromState());
        assertEquals(OathState.PROPOSED, entries.get(0).toState());
        assertEquals(OathState.PROPOSED, entries.get(1).fromState());
        assertEquals(OathState.SEALED, entries.get(1).toState());
    }

    @Test
    void openDraftStartsWithASingleParty() {
        Oath oath = service.createOpenDraft(alice, false);
        assertTrue(oath.open());
        assertEquals(List.of(alice), oath.parties());
        assertEquals(OathState.DRAFT, oath.state());
    }

    @Test
    void acceptOpenFillsSecondPartyAndCarriesStraightThroughToActive() {
        Oath oath = service.createOpenDraft(alice, false);
        service.propose(oath, alice);

        service.acceptOpen(oath, bob);

        assertEquals(List.of(alice, bob), oath.parties());
        assertEquals(OathState.ACTIVE, oath.state());
        assertTrue(oath.sealedAt() != null);

        List<LedgerEntry> entries = ledger.entriesFor(oath.id());
        assertEquals(3, entries.size());
        assertEquals(OathState.SEALED, entries.get(1).toState());
        assertEquals(OathState.ACTIVE, entries.get(2).toState());
    }

    @Test
    void cannotAcceptYourOwnOpenContract() {
        Oath oath = service.createOpenDraft(alice, false);
        service.propose(oath, alice);

        assertThrows(IllegalArgumentException.class, () -> service.acceptOpen(oath, alice));
    }

    @Test
    void cannotAcceptAnOpenContractThatIsNotProposedOrAlreadyAccepted() {
        Oath stillDraft = service.createOpenDraft(alice, false);
        assertThrows(OathTransitionException.class, () -> service.acceptOpen(stillDraft, bob));

        Oath alreadyAccepted = service.createOpenDraft(alice, false);
        service.propose(alreadyAccepted, alice);
        service.acceptOpen(alreadyAccepted, bob);
        PlayerRef carol = new PlayerRef(UUID.randomUUID());
        assertThrows(OathTransitionException.class, () -> service.acceptOpen(alreadyAccepted, carol));
    }

    @Test
    void acceptOpenRejectsANonOpenOath() {
        Oath oath = service.createDraft(List.of(alice, bob), false);
        service.propose(oath, alice);
        PlayerRef carol = new PlayerRef(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> service.acceptOpen(oath, carol));
    }
}
