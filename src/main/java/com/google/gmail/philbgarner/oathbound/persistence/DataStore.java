package com.google.gmail.philbgarner.oathbound.persistence;

import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTrigger;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticRelation;
import com.google.gmail.philbgarner.oathbound.economy.PlayerBalance;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.honor.PlayerHonor;
import com.google.gmail.philbgarner.oathbound.oath.DeathRecord;
import com.google.gmail.philbgarner.oathbound.oath.EscrowClaim;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.MobKillRecord;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.protection.Protection;
import com.google.gmail.philbgarner.oathbound.villager.VillagerNpc;
import com.google.gmail.philbgarner.oathbound.worldguard.GroupRegionLink;

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

    void saveHonor(PlayerHonor honor) throws DataStoreException;

    List<PlayerHonor> loadAllHonor() throws DataStoreException;

    void saveDiplomaticRelation(DiplomaticRelation relation) throws DataStoreException;

    List<DiplomaticRelation> loadAllDiplomaticRelations() throws DataStoreException;

    void saveTradeOffer(TradeOffer offer) throws DataStoreException;

    Optional<TradeOffer> loadTradeOffer(UUID oathId) throws DataStoreException;

    List<TradeOffer> loadAllTradeOffers() throws DataStoreException;

    void deleteTradeOffer(UUID oathId) throws DataStoreException;

    void appendDeathRecord(DeathRecord record) throws DataStoreException;

    List<DeathRecord> loadAllDeathRecords() throws DataStoreException;

    void appendMobKillRecord(MobKillRecord record) throws DataStoreException;

    List<MobKillRecord> loadAllMobKillRecords() throws DataStoreException;

    void saveEscrowClaim(EscrowClaim claim) throws DataStoreException;

    List<EscrowClaim> loadAllEscrowClaims() throws DataStoreException;

    void saveProtection(Protection protection) throws DataStoreException;

    List<Protection> loadAllProtections() throws DataStoreException;

    void deleteProtection(UUID id) throws DataStoreException;

    void saveOathBoard(OathBoard board) throws DataStoreException;

    List<OathBoard> loadAllOathBoards() throws DataStoreException;

    void deleteOathBoard(UUID id) throws DataStoreException;

    void saveCeremonyTrigger(CeremonyTrigger trigger) throws DataStoreException;

    List<CeremonyTrigger> loadAllCeremonyTriggers() throws DataStoreException;

    void deleteCeremonyTrigger(UUID id) throws DataStoreException;

    void saveVillagerNpc(VillagerNpc npc) throws DataStoreException;

    List<VillagerNpc> loadAllVillagerNpcs() throws DataStoreException;

    void deleteVillagerNpc(UUID id) throws DataStoreException;

    void saveGroupRegionLink(GroupRegionLink link) throws DataStoreException;

    List<GroupRegionLink> loadAllGroupRegionLinks() throws DataStoreException;

    void deleteGroupRegionLink(UUID id) throws DataStoreException;
}
