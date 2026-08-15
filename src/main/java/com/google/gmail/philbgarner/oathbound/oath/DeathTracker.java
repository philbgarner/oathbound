package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory, timestamped log of player deaths - the backend behind {@link Condition.DeathCount}.
 * Persistence hooks in as a {@link #addListener listener}, keeping this class free of any
 * storage/Bukkit dependency, mirroring {@link Ledger}.
 */
public final class DeathTracker {
    private final List<DeathRecord> records = new ArrayList<>();
    private final List<Consumer<DeathRecord>> listeners = new CopyOnWriteArrayList<>();

    public synchronized DeathRecord recordDeath(PlayerRef player) {
        DeathRecord record = new DeathRecord(UUID.randomUUID(), player, Instant.now());
        records.add(record);
        listeners.forEach(listener -> listener.accept(record));
        return record;
    }

    /** Used when rehydrating from persistence at startup - does not notify listeners. */
    public synchronized void loadExisting(DeathRecord record) {
        records.add(record);
    }

    /** Number of times {@code player} has died at or after {@code since}. */
    public synchronized int countSince(PlayerRef player, Instant since) {
        return (int) records.stream()
                .filter(record -> record.player().equals(player) && !record.timestamp().isBefore(since))
                .count();
    }

    public void addListener(Consumer<DeathRecord> listener) {
        listeners.add(listener);
    }
}
