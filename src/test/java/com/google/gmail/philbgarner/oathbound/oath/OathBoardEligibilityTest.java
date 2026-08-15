package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OathBoardEligibilityTest {

    private final OathService oathService = new OathService(new Ledger());

    private Oath draftWithWitness(boolean witnessed) {
        PlayerRef creator = new PlayerRef(UUID.randomUUID());
        PlayerRef counterparty = new PlayerRef(UUID.randomUUID());
        Oath oath = oathService.createDraft(List.of(creator, counterparty), false);
        if (witnessed) {
            oathService.addWitness(oath, new PlayerRef(UUID.randomUUID()));
        }
        return oath;
    }

    @Test
    void unwitnessedOathsNeverPostRegardlessOfState() {
        Oath oath = draftWithWitness(false);
        assertFalse(OathBoardEligibility.isPostable(oath, OathState.SEALED));
        assertFalse(OathBoardEligibility.isPostable(oath, OathState.FULFILLED));
        assertFalse(OathBoardEligibility.isPostable(oath, OathState.BROKEN));
    }

    @Test
    void witnessedOathsPostOnSealedFulfilledAndBroken() {
        Oath oath = draftWithWitness(true);
        assertTrue(OathBoardEligibility.isPostable(oath, OathState.SEALED));
        assertTrue(OathBoardEligibility.isPostable(oath, OathState.FULFILLED));
        assertTrue(OathBoardEligibility.isPostable(oath, OathState.BROKEN));
    }

    @Test
    void witnessedOathsDoNotPostOnProposedActivatedOrVoided() {
        Oath oath = draftWithWitness(true);
        assertFalse(OathBoardEligibility.isPostable(oath, OathState.PROPOSED));
        assertFalse(OathBoardEligibility.isPostable(oath, OathState.ACTIVE));
        assertFalse(OathBoardEligibility.isPostable(oath, OathState.VOIDED));
    }

    @Test
    void regionallyRelevantWhenEitherPartyIsAGroupMember() {
        PlayerRef creator = new PlayerRef(UUID.randomUUID());
        PlayerRef counterparty = new PlayerRef(UUID.randomUUID());
        Oath oath = oathService.createDraft(List.of(creator, counterparty), false);

        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "Town", creator, GroupTier.TOWN);
        group.addMember(new Member(counterparty, "Resident"));

        assertTrue(OathBoardEligibility.isRegionallyRelevant(oath, group));
    }

    @Test
    void notRegionallyRelevantWhenNeitherPartyIsAGroupMember() {
        PlayerRef creator = new PlayerRef(UUID.randomUUID());
        PlayerRef counterparty = new PlayerRef(UUID.randomUUID());
        Oath oath = oathService.createDraft(List.of(creator, counterparty), false);

        ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "Town", new PlayerRef(UUID.randomUUID()),
                GroupTier.TOWN);

        assertFalse(OathBoardEligibility.isRegionallyRelevant(oath, group));
    }
}
