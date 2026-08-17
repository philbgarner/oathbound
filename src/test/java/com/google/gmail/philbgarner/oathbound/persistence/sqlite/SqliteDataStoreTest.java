package com.google.gmail.philbgarner.oathbound.persistence.sqlite;

import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarLocation;
import com.google.gmail.philbgarner.oathbound.altar.AltarVulnerabilityTier;
import com.google.gmail.philbgarner.oathbound.board.BoardLocation;
import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.bounty.Banishment;
import com.google.gmail.philbgarner.oathbound.bounty.Bounty;
import com.google.gmail.philbgarner.oathbound.bounty.BountyStatus;
import com.google.gmail.philbgarner.oathbound.bounty.BountyTarget;
import com.google.gmail.philbgarner.oathbound.bounty.PveContractProgress;
import com.google.gmail.philbgarner.oathbound.bounty.ReturnLocation;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.PlayerBalance;
import com.google.gmail.philbgarner.oathbound.group.EntityRef;
import com.google.gmail.philbgarner.oathbound.group.GroupPermission;
import com.google.gmail.philbgarner.oathbound.group.GroupTier;
import com.google.gmail.philbgarner.oathbound.group.Member;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroupRef;
import com.google.gmail.philbgarner.oathbound.group.Role;
import com.google.gmail.philbgarner.oathbound.honor.PlayerHonor;
import com.google.gmail.philbgarner.oathbound.notary.Notary;
import com.google.gmail.philbgarner.oathbound.notary.NotaryLocation;
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Condition;
import com.google.gmail.philbgarner.oathbound.oath.DeathRecord;
import com.google.gmail.philbgarner.oathbound.oath.EscrowClaim;
import com.google.gmail.philbgarner.oathbound.oath.Ledger;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.MobKillRecord;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import com.google.gmail.philbgarner.oathbound.oath.OathState;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;
import com.google.gmail.philbgarner.oathbound.persistence.DataStoreException;
import com.google.gmail.philbgarner.oathbound.protection.ProtectedLocation;
import com.google.gmail.philbgarner.oathbound.protection.Protection;
import com.google.gmail.philbgarner.oathbound.protection.ProtectionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the SQLite adapter directly (no Bukkit runtime needed) - stands in for the
 * "restart the server and confirm state round-trips" manual step from the verification plan,
 * since no live Minecraft client is available in this environment. */
final class SqliteDataStoreTest {

    @Test
    void oathGroupLedgerAndBalanceRoundTripAcrossAReopenedConnection(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef alice = new PlayerRef(UUID.randomUUID());
        PlayerRef bob = new PlayerRef(UUID.randomUUID());

        UUID oathId;
        UUID groupId;
        Currency coin = new Currency("coin");

        // First "server session": create and persist state, then close (simulating shutdown).
        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            ProtectionGroup group = new ProtectionGroup(UUID.randomUUID(), "The Guild", alice, GroupTier.COMPANY);
            group.addRole(new Role("Owner", 100, EnumSet.allOf(GroupPermission.class)));
            group.addMember(new Member(alice, "Owner"));
            groupId = group.id();
            first.saveGroup(group);

            Ledger ledger = new Ledger();
            OathService oathService = new OathService(ledger);
            ledger.addListener(entry -> {
                try {
                    first.appendLedgerEntry(entry);
                } catch (DataStoreException e) {
                    throw new RuntimeException(e);
                }
            });

            Oath oath = oathService.createDraft(List.of(alice, bob), true);
            oathId = oath.id();
            oathService.addClause(oath, new Clause.CustomFlagClause("uphold the treaty"));
            oathService.addClause(oath, new Clause.TransferClause(
                    bob, new ProtectionGroupRef(groupId),
                    new Condition.Compound(Condition.CompoundOperator.AND, List.of(
                            new Condition.TimeElapsed(Duration.ofDays(1)),
                            new Condition.PaymentReceived(50L, coin)
                    ))));
            oathService.propose(oath, alice);
            oathService.seal(oath, bob);
            first.saveOath(oath);

            first.saveBalance(new PlayerBalance(alice, coin, 250L));
        } finally {
            first.close();
        }

        // Second "server session": reopen the same file and confirm everything is still there.
        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            Optional<ProtectionGroup> loadedGroup = second.loadGroup(groupId);
            assertTrue(loadedGroup.isPresent());
            assertEquals("The Guild", loadedGroup.get().name());
            assertEquals(GroupTier.COMPANY, loadedGroup.get().tier());
            assertEquals((EntityRef) alice, loadedGroup.get().owner());
            assertTrue(loadedGroup.get().hasPermission(alice, GroupPermission.TRANSFER_OWNERSHIP));

            Optional<Oath> loadedOath = second.loadOath(oathId);
            assertTrue(loadedOath.isPresent());
            assertEquals(OathState.SEALED, loadedOath.get().state());
            assertTrue(loadedOath.get().bloodOath());
            assertEquals(2, loadedOath.get().clauses().size());
            assertEquals(List.of(alice, bob), loadedOath.get().parties());

            List<LedgerEntry> entries = second.loadLedgerEntries(oathId);
            assertEquals(2, entries.size());
            assertEquals(OathState.DRAFT, entries.get(0).fromState());
            assertEquals(OathState.PROPOSED, entries.get(0).toState());
            assertEquals(OathState.SEALED, entries.get(1).toState());

            List<PlayerBalance> balances = second.loadAllBalances();
            assertEquals(1, balances.size());
            assertEquals(250L, balances.get(0).amount());
            assertEquals(coin, balances.get(0).currency());
        } finally {
            second.close();
        }
    }

    @Test
    void activatedOathRoundTripsActivatedAtAndFulfilledClauseIndices(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef alice = new PlayerRef(UUID.randomUUID());
        PlayerRef bob = new PlayerRef(UUID.randomUUID());
        UUID oathId;

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            OathService oathService = new OathService(new Ledger());
            Oath oath = oathService.createDraft(List.of(alice, bob), false);
            oathId = oath.id();
            oathService.addClause(oath, new Clause.CustomFlagClause("keep the peace"));
            oathService.propose(oath, alice);
            oathService.seal(oath, bob);
            oathService.activate(oath, bob);
            oathService.markClauseFulfilled(oath, 0);
            first.saveOath(oath);
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            Optional<Oath> loaded = second.loadOath(oathId);
            assertTrue(loaded.isPresent());
            assertEquals(OathState.ACTIVE, loaded.get().state());
            assertTrue(loaded.get().activatedAt() != null);
            assertEquals(Set.of(0), loaded.get().fulfilledClauseIndices());
            assertTrue(loaded.get().isClauseFulfilled(0));
        } finally {
            second.close();
        }
    }

    @Test
    void oathWithEscrowedCurrencyRoundTripsTheCurrencyKeyedRewardMap(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef alice = new PlayerRef(UUID.randomUUID());
        PlayerRef bob = new PlayerRef(UUID.randomUUID());
        Currency coin = new Currency("coin");
        UUID oathId;

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            OathService oathService = new OathService(new Ledger());
            Oath oath = oathService.createDraft(List.of(alice, bob), false);
            oathId = oath.id();
            oathService.addClause(oath, new Clause.EscrowClause(alice, bob, List.of(), java.util.Map.of(coin, 42L),
                    List.of(new Clause.ReleaseStep(1.0, new Condition.Immediate()))));
            first.saveOath(oath);
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            Optional<Oath> loaded = second.loadOath(oathId);
            assertTrue(loaded.isPresent());
            Clause.EscrowClause escrow = (Clause.EscrowClause) loaded.get().clauses().get(0);
            assertEquals(42L, escrow.currency().get(coin));
        } finally {
            second.close();
        }
    }

    @Test
    void deathRecordsRoundTripAcrossAReopenedConnection(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef victim = new PlayerRef(UUID.randomUUID());
        Instant timestamp = Instant.now();
        UUID recordId = UUID.randomUUID();

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            first.appendDeathRecord(new DeathRecord(recordId, victim, null, timestamp));
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            List<DeathRecord> records = second.loadAllDeathRecords();
            assertEquals(1, records.size());
            assertEquals(recordId, records.get(0).id());
            assertEquals(victim, records.get(0).player());
            assertEquals(timestamp, records.get(0).timestamp());
        } finally {
            second.close();
        }
    }

    @Test
    void mobKillRecordsRoundTripAcrossAReopenedConnection(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef killer = new PlayerRef(UUID.randomUUID());
        Instant timestamp = Instant.now();
        UUID recordId = UUID.randomUUID();

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            first.appendMobKillRecord(new MobKillRecord(recordId, killer, "RAVAGER", timestamp));
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            List<MobKillRecord> records = second.loadAllMobKillRecords();
            assertEquals(1, records.size());
            assertEquals(recordId, records.get(0).id());
            assertEquals(killer, records.get(0).killer());
            assertEquals("RAVAGER", records.get(0).mobTypeName());
            assertEquals(timestamp, records.get(0).timestamp());
        } finally {
            second.close();
        }
    }

    @Test
    void altarRoundTripsAcrossAReopenedConnection(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        AltarLocation location = new AltarLocation(UUID.randomUUID(), 100, 65, -200);
        Instant consecratedAt = Instant.now().minus(Duration.ofDays(10));
        Instant sacrificeAt = consecratedAt.plus(Duration.ofDays(1));
        UUID altarId;

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            Altar altar = new Altar(UUID.randomUUID(), owner, location, consecratedAt);
            altar.applySacrifice(150L, sacrificeAt, Duration.ofMinutes(5), 150L);
            altar.setLastKnownTier(AltarVulnerabilityTier.DECAYING);
            altarId = altar.id();
            first.saveAltar(altar);
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            Optional<Altar> loaded = second.loadAltar(altarId);
            assertTrue(loaded.isPresent());
            Altar altar = loaded.get();
            assertEquals(owner, altar.owner());
            assertEquals(location, altar.location());
            assertEquals(consecratedAt, altar.consecratedAt());
            assertEquals(150L, altar.powerBaseline());
            assertEquals(sacrificeAt, altar.lastSacrificeAt());
            assertEquals(150L, altar.lastSacrificeValue());
            assertEquals(sacrificeAt.plus(Duration.ofMinutes(5)), altar.cooldownUntil());
            assertEquals(AltarVulnerabilityTier.DECAYING, altar.lastKnownTier());
            assertEquals(1, second.loadAllAltars().size());
        } finally {
            second.close();
        }
    }

    @Test
    void protectionRoundTripsAndDeletesAcrossAReopenedConnection(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        UUID groupId = UUID.randomUUID();
        ProtectedLocation location = new ProtectedLocation(UUID.randomUUID(), 10, 65, -30);
        UUID protectionId;

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            Protection protection = new Protection(UUID.randomUUID(), location, ProtectionType.DOOR,
                    new ProtectionGroupRef(groupId), Instant.now());
            protectionId = protection.id();
            first.saveProtection(protection);
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            List<Protection> loaded = second.loadAllProtections();
            assertEquals(1, loaded.size());
            assertEquals(location, loaded.get(0).location());
            assertEquals(ProtectionType.DOOR, loaded.get(0).type());
            assertEquals(new ProtectionGroupRef(groupId), loaded.get(0).group());

            second.deleteProtection(protectionId);
            assertTrue(second.loadAllProtections().isEmpty());
        } finally {
            second.close();
        }
    }

    @Test
    void honorRoundTripsAndUpdatesAcrossAReopenedConnection(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef player = new PlayerRef(UUID.randomUUID());

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            first.saveHonor(new PlayerHonor(player, -15L));
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            List<PlayerHonor> loaded = second.loadAllHonor();
            assertEquals(1, loaded.size());
            assertEquals(player, loaded.get(0).player());
            assertEquals(-15L, loaded.get(0).value());

            second.saveHonor(new PlayerHonor(player, 30L));
            List<PlayerHonor> updated = second.loadAllHonor();
            assertEquals(1, updated.size());
            assertEquals(30L, updated.get(0).value());
        } finally {
            second.close();
        }
    }

    @Test
    void notaryRoundTripsAndDeletesAcrossAReopenedConnection(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        UUID entityId = UUID.randomUUID();
        NotaryLocation location = new NotaryLocation(UUID.randomUUID(), 5, 70, -5);
        UUID notaryId;

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            Notary notary = new Notary(UUID.randomUUID(), entityId, owner, "The Judge", location, Instant.now());
            notaryId = notary.id();
            first.saveNotary(notary);
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            List<Notary> loaded = second.loadAllNotaries();
            assertEquals(1, loaded.size());
            assertEquals(entityId, loaded.get(0).entityId());
            assertEquals(owner, loaded.get(0).owner());
            assertEquals(location, loaded.get(0).location());
            assertEquals("The Judge", loaded.get(0).name());

            second.deleteNotary(notaryId);
            assertTrue(second.loadAllNotaries().isEmpty());
        } finally {
            second.close();
        }
    }

    @Test
    void oathBoardRoundTripsAndDeletesAcrossAReopenedConnectionIncludingCapitalScope(@TempDir Path tempDir)
            throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef installer = new PlayerRef(UUID.randomUUID());
        UUID groupId = UUID.randomUUID();
        BoardLocation location = new BoardLocation(UUID.randomUUID(), 12, 65, -8);
        UUID boardId;

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            OathBoard board = new OathBoard(UUID.randomUUID(), new ProtectionGroupRef(groupId), location,
                    installer, Instant.now());
            boardId = board.id();
            first.saveOathBoard(board);
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            List<OathBoard> loaded = second.loadAllOathBoards();
            assertEquals(1, loaded.size());
            assertEquals(new ProtectionGroupRef(groupId), loaded.get(0).regionalGroup());
            assertEquals(location, loaded.get(0).location());
            assertEquals(installer, loaded.get(0).installer());
            assertFalse(loaded.get(0).isCapital());

            OathBoard capital = new OathBoard(boardId, null, location, installer, Instant.now());
            second.saveOathBoard(capital);
            List<OathBoard> updated = second.loadAllOathBoards();
            assertEquals(1, updated.size());
            assertTrue(updated.get(0).isCapital());

            second.deleteOathBoard(boardId);
            assertTrue(second.loadAllOathBoards().isEmpty());
        } finally {
            second.close();
        }
    }

    @Test
    void tradeOfferRoundTripsAcrossAReopenedConnectionIncludingPartialFulfillment(@TempDir Path tempDir)
            throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef creator = new PlayerRef(UUID.randomUUID());
        PlayerRef fulfiller = new PlayerRef(UUID.randomUUID());
        UUID oathId = UUID.randomUUID();
        List<SerializedItemStack> creatorItems = List.of(new SerializedItemStack(new byte[] {1, 2, 3}));
        List<SerializedItemStack> fulfillerItems = List.of(new SerializedItemStack(new byte[] {4, 5, 6}));

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            TradeOffer offer = new TradeOffer(oathId, creator, creatorItems);
            first.saveTradeOffer(offer);

            offer.fulfill(fulfiller, fulfillerItems);
            offer.markCreatorItemsDelivered();
            first.saveTradeOffer(offer);
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            Optional<TradeOffer> loaded = second.loadTradeOffer(oathId);
            assertTrue(loaded.isPresent());
            assertEquals(creator, loaded.get().creator());
            assertEquals(fulfiller, loaded.get().fulfiller());
            assertEquals(1, loaded.get().creatorItems().size());
            assertEquals(1, loaded.get().fulfillerItems().size());
            assertTrue(loaded.get().completed());
            assertTrue(loaded.get().creatorItemsDelivered());
            assertTrue(!loaded.get().fulfillerItemsDelivered());
            assertEquals(1, second.loadAllTradeOffers().size());
        } finally {
            second.close();
        }
    }

    @Test
    void escrowClaimRoundTripsAndUpdatesAcrossReopenedConnections(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef depositor = new PlayerRef(UUID.randomUUID());
        PlayerRef recipient = new PlayerRef(UUID.randomUUID());
        UUID oathId = UUID.randomUUID();
        EscrowClaim claim = new EscrowClaim(UUID.randomUUID(), oathId, 0, depositor, recipient,
                List.of(new SerializedItemStack(new byte[] {9, 9})), Instant.now());

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            first.saveEscrowClaim(claim);
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            List<EscrowClaim> claims = second.loadAllEscrowClaims();
            assertEquals(1, claims.size());
            EscrowClaim loaded = claims.get(0);
            assertEquals(claim.id(), loaded.id());
            assertEquals(oathId, loaded.oathId());
            assertEquals(depositor, loaded.depositor());
            assertEquals(recipient, loaded.recipient());
            assertTrue(!loaded.claimed());

            loaded.claim();
            second.saveEscrowClaim(loaded);
        } finally {
            second.close();
        }

        SqliteDataStore third = new SqliteDataStore(dbFile);
        third.initialize();
        try {
            List<EscrowClaim> claims = third.loadAllEscrowClaims();
            assertEquals(1, claims.size());
            assertTrue(claims.get(0).claimed());
        } finally {
            third.close();
        }
    }

    @Test
    void bountyBanishmentPveProgressAndOptOutsRoundTripAcrossAReopenedConnection(@TempDir Path tempDir)
            throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef placer = new PlayerRef(UUID.randomUUID());
        PlayerRef victim = new PlayerRef(UUID.randomUUID());
        UUID groupId = UUID.randomUUID();
        Currency coin = new Currency("coin");
        Instant now = Instant.now();

        UUID bountyId;
        UUID banishmentId;
        UUID progressId;

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            Bounty bounty = new Bounty(UUID.randomUUID(), placer, new BountyTarget.Group(new ProtectionGroupRef(groupId)),
                    3, 2, java.util.Map.of(coin, 300L), 25L, now, BountyStatus.ACTIVE);
            bountyId = bounty.id();
            first.saveBounty(bounty);

            Banishment banishment = new Banishment(UUID.randomUUID(), victim, bountyId, now, now.plus(Duration.ofHours(5)),
                    new ReturnLocation(UUID.randomUUID(), 1.5, 64.0, -2.5, 90.0f, 0.0f), false);
            banishmentId = banishment.id();
            first.saveBanishment(banishment);

            PveContractProgress progress = new PveContractProgress(UUID.randomUUID(), placer, "spider-cull", 4, now, 1);
            progressId = progress.id();
            first.savePveContractProgress(progress);

            first.setBountyNotificationOptOut(placer.playerId(), true);
        } finally {
            first.close();
        }

        SqliteDataStore second = new SqliteDataStore(dbFile);
        second.initialize();
        try {
            List<Bounty> bounties = second.loadAllBounties();
            assertEquals(1, bounties.size());
            Bounty loadedBounty = bounties.get(0);
            assertEquals(bountyId, loadedBounty.id());
            assertEquals(placer, loadedBounty.placer());
            assertEquals(new BountyTarget.Group(new ProtectionGroupRef(groupId)), loadedBounty.target());
            assertEquals(3, loadedBounty.originalQuantity());
            assertEquals(2, loadedBounty.remainingQuantity());
            assertEquals(300L, loadedBounty.totalReward().get(coin));
            assertEquals(25L, loadedBounty.feePaid());
            assertEquals(BountyStatus.ACTIVE, loadedBounty.status());

            List<Banishment> banishments = second.loadAllBanishments();
            assertEquals(1, banishments.size());
            assertEquals(banishmentId, banishments.get(0).id());
            assertEquals(victim, banishments.get(0).player());
            assertEquals(bountyId, banishments.get(0).triggeringBountyId());
            assertFalse(banishments.get(0).released());

            List<PveContractProgress> progressList = second.loadAllPveContractProgress();
            assertEquals(1, progressList.size());
            assertEquals(progressId, progressList.get(0).id());
            assertEquals(4, progressList.get(0).killsSoFar());
            assertEquals(1, progressList.get(0).timesCompleted());

            Set<UUID> optOuts = second.loadBountyNotificationOptOuts();
            assertEquals(Set.of(placer.playerId()), optOuts);

            second.deleteBounty(bountyId);
            second.deleteBanishment(banishmentId);
            second.deletePveContractProgress(progressId);
            second.setBountyNotificationOptOut(placer.playerId(), false);
            assertTrue(second.loadAllBounties().isEmpty());
            assertTrue(second.loadAllBanishments().isEmpty());
            assertTrue(second.loadAllPveContractProgress().isEmpty());
            assertTrue(second.loadBountyNotificationOptOuts().isEmpty());
        } finally {
            second.close();
        }
    }
}
