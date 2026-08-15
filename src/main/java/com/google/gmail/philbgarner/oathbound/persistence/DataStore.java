package com.google.gmail.philbgarner.oathbound.persistence;

import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.economy.PlayerBalance;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.Oath;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter-pattern seam for storage. SQLite is the only implementation for Phase 1; a future YAML/flat-file
 * adapter implements this same contract. All methods are safe to call off the main thread.
 */
public interface DataStore {
    void initialize() throws DataStoreException;

    void close() throws DataStoreException;

    void saveOath(Oath oath) throws DataStoreException;

    Optional<Oath> loadOath(UUID id) throws DataStoreException;

    List<Oath> loadAllOaths() throws DataStoreException;

    void deleteOath(UUID id) throws DataStoreException;

    void saveGroup(ProtectionGroup group) throws DataStoreException;

    Optional<ProtectionGroup> loadGroup(UUID id) throws DataStoreException;

    List<ProtectionGroup> loadAllGroups() throws DataStoreException;

    void deleteGroup(UUID id) throws DataStoreException;

    void appendLedgerEntry(LedgerEntry entry) throws DataStoreException;

    List<LedgerEntry> loadLedgerEntries(UUID oathId) throws DataStoreException;

    List<LedgerEntry> loadRecentLedgerEntries(int limit) throws DataStoreException;

    void saveBalance(PlayerBalance balance) throws DataStoreException;

    List<PlayerBalance> loadAllBalances() throws DataStoreException;

    void saveAltar(Altar altar) throws DataStoreException;

    Optional<Altar> loadAltar(UUID id) throws DataStoreException;

    List<Altar> loadAllAltars() throws DataStoreException;

    void deleteAltar(UUID id) throws DataStoreException;

    void saveTradeOffer(TradeOffer offer) throws DataStoreException;

    Optional<TradeOffer> loadTradeOffer(UUID oathId) throws DataStoreException;

    List<TradeOffer> loadAllTradeOffers() throws DataStoreException;

    void deleteTradeOffer(UUID oathId) throws DataStoreException;
}
