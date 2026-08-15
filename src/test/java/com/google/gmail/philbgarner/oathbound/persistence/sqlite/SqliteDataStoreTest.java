package com.google.gmail.philbgarner.oathbound.persistence.sqlite;

import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.AltarLocation;
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
import com.google.gmail.philbgarner.oathbound.oath.Clause;
import com.google.gmail.philbgarner.oathbound.oath.Condition;
import com.google.gmail.philbgarner.oathbound.oath.Ledger;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.oath.OathService;
import com.google.gmail.philbgarner.oathbound.oath.OathState;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;
import com.google.gmail.philbgarner.oathbound.persistence.DataStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void altarRoundTripsAcrossAReopenedConnection(@TempDir Path tempDir) throws DataStoreException {
        Path dbFile = tempDir.resolve("oathbound.db");
        PlayerRef owner = new PlayerRef(UUID.randomUUID());
        AltarLocation location = new AltarLocation(UUID.randomUUID(), 100, 65, -200);
        UUID altarId;

        SqliteDataStore first = new SqliteDataStore(dbFile);
        first.initialize();
        try {
            Altar altar = new Altar(UUID.randomUUID(), owner, location, 0L, java.time.Instant.now());
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
            assertEquals(owner, loaded.get().owner());
            assertEquals(location, loaded.get().location());
            assertEquals(0L, loaded.get().power());
            assertEquals(1, second.loadAllAltars().size());
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
}
