package com.google.gmail.philbgarner.oathbound.persistence.sqlite;

import com.google.gmail.philbgarner.oathbound.board.OathBoard;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTrigger;
import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticRelation;
import com.google.gmail.philbgarner.oathbound.diplomacy.DiplomaticState;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.economy.PlayerBalance;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.group.ProtectionGroup;
import com.google.gmail.philbgarner.oathbound.honor.PlayerHonor;
import com.google.gmail.philbgarner.oathbound.oath.DeathRecord;
import com.google.gmail.philbgarner.oathbound.oath.EscrowClaim;
import com.google.gmail.philbgarner.oathbound.oath.LedgerEntry;
import com.google.gmail.philbgarner.oathbound.oath.MobKillRecord;
import com.google.gmail.philbgarner.oathbound.oath.Oath;
import com.google.gmail.philbgarner.oathbound.persistence.DataStore;
import com.google.gmail.philbgarner.oathbound.persistence.DataStoreException;
import com.google.gmail.philbgarner.oathbound.persistence.dto.EscrowClaimDto;
import com.google.gmail.philbgarner.oathbound.persistence.dto.GroupDto;
import com.google.gmail.philbgarner.oathbound.persistence.dto.OathDto;
import com.google.gmail.philbgarner.oathbound.persistence.dto.TradeOfferDto;
import com.google.gmail.philbgarner.oathbound.persistence.json.GsonFactory;
import com.google.gmail.philbgarner.oathbound.protection.Protection;
import com.google.gmail.philbgarner.oathbound.villager.VillagerNpc;
import com.google.gmail.philbgarner.oathbound.worldguard.GroupRegionLink;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqliteDataStore implements DataStore {

    private static final List<String> MIGRATIONS = List.of(
            "db/migrations/0001_init.sql",
            "db/migrations/0002_altars.sql",
            "db/migrations/0003_trade_offers.sql",
            "db/migrations/0004_death_records.sql",
            "db/migrations/0005_escrow_claims.sql",
            "db/migrations/0006_protections.sql",
            "db/migrations/0007_honor.sql",
            "db/migrations/0008_notaries.sql",
            "db/migrations/0009_oath_boards.sql",
            "db/migrations/0010_villager_npcs.sql",
            "db/migrations/0011_bounty.sql",
            "db/migrations/0012_mob_kill_records.sql",
            "db/migrations/0013_diplomacy.sql",
            "db/migrations/0014_ceremony_triggers.sql",
            "db/migrations/0015_group_region_links.sql"
    );

    private final Path databaseFile;
    private final Gson gson = GsonFactory.create();
    private Connection connection;

    public SqliteDataStore(Path databaseFile) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile");
    }

    @Override
    public synchronized void initialize() throws DataStoreException {
        try {
            Path parent = databaseFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
                try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        statement.execute("INSERT INTO schema_version(version) VALUES (0)");
                    }
                }
            }
            runMigrations();
        } catch (IOException | SQLException e) {
            throw new DataStoreException("Failed to initialize SQLite data store at " + databaseFile, e);
        }
    }

    private void runMigrations() throws SQLException, DataStoreException {
        int current = currentSchemaVersion();
        for (int i = 0; i < MIGRATIONS.size(); i++) {
            int migrationVersion = i + 1;
            if (migrationVersion <= current) {
                continue;
            }
            String script = readResource(MIGRATIONS.get(i));
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.split(";")) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        statement.execute(trimmed);
                    }
                }
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE schema_version SET version = ?")) {
                update.setInt(1, migrationVersion);
                update.executeUpdate();
            }
        }
    }

    private int currentSchemaVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String readResource(String path) throws DataStoreException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new DataStoreException("Missing migration resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataStoreException("Failed to read migration resource: " + path, e);
        }
    }

    @Override
    public synchronized void close() throws DataStoreException {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to close SQLite connection", e);
        }
    }

    @Override
    public synchronized void saveOath(Oath oath) throws DataStoreException {
        String json = gson.toJson(OathDto.from(oath));
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO oaths(id, data) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data")) {
            stmt.setString(1, oath.id().toString());
            stmt.setString(2, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save oath " + oath.id(), e);
        }
    }

    @Override
    public synchronized Optional<Oath> loadOath(UUID id) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT data FROM oaths WHERE id = ?")) {
            stmt.setString(1, id.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(gson.fromJson(rs.getString(1), OathDto.class).toDomain());
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load oath " + id, e);
        }
    }

    @Override
    public synchronized List<Oath> loadAllOaths() throws DataStoreException {
        List<Oath> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM oaths")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), OathDto.class).toDomain());
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all oaths", e);
        }
        return result;
    }

    @Override
    public synchronized void deleteOath(UUID id) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM oaths WHERE id = ?")) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to delete oath " + id, e);
        }
    }

    @Override
    public synchronized void saveGroup(ProtectionGroup group) throws DataStoreException {
        String json = gson.toJson(GroupDto.from(group));
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO protection_groups(id, data) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data")) {
            stmt.setString(1, group.id().toString());
            stmt.setString(2, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save group " + group.id(), e);
        }
    }

    @Override
    public synchronized Optional<ProtectionGroup> loadGroup(UUID id) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT data FROM protection_groups WHERE id = ?")) {
            stmt.setString(1, id.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(gson.fromJson(rs.getString(1), GroupDto.class).toDomain());
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load group " + id, e);
        }
    }

    @Override
    public synchronized List<ProtectionGroup> loadAllGroups() throws DataStoreException {
        List<ProtectionGroup> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM protection_groups")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), GroupDto.class).toDomain());
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all groups", e);
        }
        return result;
    }

    @Override
    public synchronized void deleteGroup(UUID id) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM protection_groups WHERE id = ?")) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to delete group " + id, e);
        }
    }

    @Override
    public synchronized void appendLedgerEntry(LedgerEntry entry) throws DataStoreException {
        String json = gson.toJson(entry);
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO ledger_entries(id, oath_id, timestamp, data) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, entry.id().toString());
            stmt.setString(2, entry.oathId().toString());
            stmt.setString(3, entry.timestamp().toString());
            stmt.setString(4, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to append ledger entry " + entry.id(), e);
        }
    }

    @Override
    public synchronized List<LedgerEntry> loadLedgerEntries(UUID oathId) throws DataStoreException {
        List<LedgerEntry> result = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT data FROM ledger_entries WHERE oath_id = ? ORDER BY timestamp ASC")) {
            stmt.setString(1, oathId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(gson.fromJson(rs.getString(1), LedgerEntry.class));
                }
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load ledger entries for oath " + oathId, e);
        }
        return result;
    }

    @Override
    public synchronized List<LedgerEntry> loadRecentLedgerEntries(int limit) throws DataStoreException {
        List<LedgerEntry> result = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT data FROM ledger_entries ORDER BY timestamp DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(gson.fromJson(rs.getString(1), LedgerEntry.class));
                }
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load recent ledger entries", e);
        }
        Collections.reverse(result);
        return result;
    }

    @Override
    public synchronized void saveBalance(PlayerBalance balance) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO player_balances(player_id, currency_id, amount) VALUES (?, ?, ?) "
                        + "ON CONFLICT(player_id, currency_id) DO UPDATE SET amount = excluded.amount")) {
            stmt.setString(1, balance.player().playerId().toString());
            stmt.setString(2, balance.currency().id());
            stmt.setLong(3, balance.amount());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save balance for " + balance.player().playerId(), e);
        }
    }

    @Override
    public synchronized List<PlayerBalance> loadAllBalances() throws DataStoreException {
        List<PlayerBalance> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT player_id, currency_id, amount FROM player_balances")) {
            while (rs.next()) {
                PlayerRef player = new PlayerRef(UUID.fromString(rs.getString(1)));
                Currency currency = new Currency(rs.getString(2));
                result.add(new PlayerBalance(player, currency, rs.getLong(3)));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all balances", e);
        }
        return result;
    }

    @Override
    public synchronized void saveHonor(PlayerHonor honor) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO player_honor(player_id, amount) VALUES (?, ?) "
                        + "ON CONFLICT(player_id) DO UPDATE SET amount = excluded.amount")) {
            stmt.setString(1, honor.player().playerId().toString());
            stmt.setLong(2, honor.value());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save honor for " + honor.player().playerId(), e);
        }
    }

    @Override
    public synchronized List<PlayerHonor> loadAllHonor() throws DataStoreException {
        List<PlayerHonor> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT player_id, amount FROM player_honor")) {
            while (rs.next()) {
                PlayerRef player = new PlayerRef(UUID.fromString(rs.getString(1)));
                result.add(new PlayerHonor(player, rs.getLong(2)));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all honor", e);
        }
        return result;
    }

    @Override
    public synchronized void saveDiplomaticRelation(DiplomaticRelation relation) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO diplomatic_relations(group_a_id, group_b_id, state, since) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(group_a_id, group_b_id) DO UPDATE SET state = excluded.state, since = excluded.since")) {
            stmt.setString(1, relation.groupA().toString());
            stmt.setString(2, relation.groupB().toString());
            stmt.setString(3, relation.state().name());
            stmt.setString(4, relation.since().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save diplomatic relation " + relation.groupA() + "/" + relation.groupB(), e);
        }
    }

    @Override
    public synchronized List<DiplomaticRelation> loadAllDiplomaticRelations() throws DataStoreException {
        List<DiplomaticRelation> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT group_a_id, group_b_id, state, since FROM diplomatic_relations")) {
            while (rs.next()) {
                result.add(new DiplomaticRelation(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                        DiplomaticState.valueOf(rs.getString(3)), Instant.parse(rs.getString(4))));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all diplomatic relations", e);
        }
        return result;
    }

    @Override
    public synchronized void saveTradeOffer(TradeOffer offer) throws DataStoreException {
        String json = gson.toJson(TradeOfferDto.from(offer));
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO trade_offers(oath_id, data) VALUES (?, ?) "
                        + "ON CONFLICT(oath_id) DO UPDATE SET data = excluded.data")) {
            stmt.setString(1, offer.oathId().toString());
            stmt.setString(2, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save trade offer " + offer.oathId(), e);
        }
    }

    @Override
    public synchronized Optional<TradeOffer> loadTradeOffer(UUID oathId) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT data FROM trade_offers WHERE oath_id = ?")) {
            stmt.setString(1, oathId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(gson.fromJson(rs.getString(1), TradeOfferDto.class).toDomain());
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load trade offer " + oathId, e);
        }
    }

    @Override
    public synchronized List<TradeOffer> loadAllTradeOffers() throws DataStoreException {
        List<TradeOffer> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM trade_offers")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), TradeOfferDto.class).toDomain());
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all trade offers", e);
        }
        return result;
    }

    @Override
    public synchronized void deleteTradeOffer(UUID oathId) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM trade_offers WHERE oath_id = ?")) {
            stmt.setString(1, oathId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to delete trade offer " + oathId, e);
        }
    }

    @Override
    public synchronized void appendDeathRecord(DeathRecord record) throws DataStoreException {
        String json = gson.toJson(record);
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO death_records(id, player_id, timestamp, data) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, record.id().toString());
            stmt.setString(2, record.player().playerId().toString());
            stmt.setString(3, record.timestamp().toString());
            stmt.setString(4, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to append death record " + record.id(), e);
        }
    }

    @Override
    public synchronized List<DeathRecord> loadAllDeathRecords() throws DataStoreException {
        List<DeathRecord> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM death_records")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), DeathRecord.class));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all death records", e);
        }
        return result;
    }

    @Override
    public synchronized void appendMobKillRecord(MobKillRecord record) throws DataStoreException {
        String json = gson.toJson(record);
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO mob_kill_records(id, killer_id, timestamp, data) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, record.id().toString());
            stmt.setString(2, record.killer().playerId().toString());
            stmt.setString(3, record.timestamp().toString());
            stmt.setString(4, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to append mob kill record " + record.id(), e);
        }
    }

    @Override
    public synchronized List<MobKillRecord> loadAllMobKillRecords() throws DataStoreException {
        List<MobKillRecord> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM mob_kill_records")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), MobKillRecord.class));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all mob kill records", e);
        }
        return result;
    }

    @Override
    public synchronized void saveEscrowClaim(EscrowClaim claim) throws DataStoreException {
        String json = gson.toJson(EscrowClaimDto.from(claim));
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO escrow_claims(id, oath_id, data) VALUES (?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET data = excluded.data")) {
            stmt.setString(1, claim.id().toString());
            stmt.setString(2, claim.oathId().toString());
            stmt.setString(3, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save escrow claim " + claim.id(), e);
        }
    }

    @Override
    public synchronized List<EscrowClaim> loadAllEscrowClaims() throws DataStoreException {
        List<EscrowClaim> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM escrow_claims")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), EscrowClaimDto.class).toDomain());
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all escrow claims", e);
        }
        return result;
    }

    @Override
    public synchronized void saveProtection(Protection protection) throws DataStoreException {
        String json = gson.toJson(protection);
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO protections(id, data) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data")) {
            stmt.setString(1, protection.id().toString());
            stmt.setString(2, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save protection " + protection.id(), e);
        }
    }

    @Override
    public synchronized List<Protection> loadAllProtections() throws DataStoreException {
        List<Protection> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM protections")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), Protection.class));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all protections", e);
        }
        return result;
    }

    @Override
    public synchronized void deleteProtection(UUID id) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM protections WHERE id = ?")) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to delete protection " + id, e);
        }
    }

    @Override
    public synchronized void saveOathBoard(OathBoard board) throws DataStoreException {
        String json = gson.toJson(board);
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO oath_boards(id, data) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data")) {
            stmt.setString(1, board.id().toString());
            stmt.setString(2, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save oath board " + board.id(), e);
        }
    }

    @Override
    public synchronized List<OathBoard> loadAllOathBoards() throws DataStoreException {
        List<OathBoard> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM oath_boards")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), OathBoard.class));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all oath boards", e);
        }
        return result;
    }

    @Override
    public synchronized void deleteOathBoard(UUID id) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM oath_boards WHERE id = ?")) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to delete oath board " + id, e);
        }
    }

    @Override
    public synchronized void saveCeremonyTrigger(CeremonyTrigger trigger) throws DataStoreException {
        String json = gson.toJson(trigger);
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO ceremony_triggers(id, data) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data")) {
            stmt.setString(1, trigger.id().toString());
            stmt.setString(2, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save ceremony trigger " + trigger.id(), e);
        }
    }

    @Override
    public synchronized List<CeremonyTrigger> loadAllCeremonyTriggers() throws DataStoreException {
        List<CeremonyTrigger> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM ceremony_triggers")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), CeremonyTrigger.class));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all ceremony triggers", e);
        }
        return result;
    }

    @Override
    public synchronized void deleteCeremonyTrigger(UUID id) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM ceremony_triggers WHERE id = ?")) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to delete ceremony trigger " + id, e);
        }
    }

    @Override
    public synchronized void saveVillagerNpc(VillagerNpc npc) throws DataStoreException {
        String json = gson.toJson(npc);
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO villager_npcs(id, data) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data")) {
            stmt.setString(1, npc.id().toString());
            stmt.setString(2, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save villager npc " + npc.id(), e);
        }
    }

    @Override
    public synchronized List<VillagerNpc> loadAllVillagerNpcs() throws DataStoreException {
        List<VillagerNpc> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM villager_npcs")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), VillagerNpc.class));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all villager npcs", e);
        }
        return result;
    }

    @Override
    public synchronized void deleteVillagerNpc(UUID id) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM villager_npcs WHERE id = ?")) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to delete villager npc " + id, e);
        }
    }

    @Override
    public synchronized void saveGroupRegionLink(GroupRegionLink link) throws DataStoreException {
        String json = gson.toJson(link);
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO group_region_links(id, data) VALUES (?, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data")) {
            stmt.setString(1, link.id().toString());
            stmt.setString(2, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to save group region link " + link.id(), e);
        }
    }

    @Override
    public synchronized List<GroupRegionLink> loadAllGroupRegionLinks() throws DataStoreException {
        List<GroupRegionLink> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT data FROM group_region_links")) {
            while (rs.next()) {
                result.add(gson.fromJson(rs.getString(1), GroupRegionLink.class));
            }
        } catch (SQLException e) {
            throw new DataStoreException("Failed to load all group region links", e);
        }
        return result;
    }

    @Override
    public synchronized void deleteGroupRegionLink(UUID id) throws DataStoreException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM group_region_links WHERE id = ?")) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataStoreException("Failed to delete group region link " + id, e);
        }
    }
}
