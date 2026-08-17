package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory, timestamped log of mob kills - the backend behind {@link Condition.MobKillCount}. Mirrors
 * {@link DeathTracker}: persistence hooks in as a {@link #addListener listener}, keeping this class free
 * of any storage/Bukkit dependency.
 */
public final class MobKillTracker {
    private final List<MobKillRecord> records = new ArrayList<>();
    private final List<Consumer<MobKillRecord>> listeners = new CopyOnWriteArrayList<>();

    public synchronized MobKillRecord recordKill(PlayerRef killer, String mobTypeName) {
        MobKillRecord record = new MobKillRecord(UUID.randomUUID(), killer, mobTypeName, Instant.now());
        records.add(record);
        listeners.forEach(listener -> listener.accept(record));
        return record;
    }

    /** Used when rehydrating from persistence at startup - does not notify listeners. */
    public synchronized void loadExisting(MobKillRecord record) {
        records.add(record);
    }

    /** Number of times {@code killer} has killed {@code mobTypeName} at or after {@code since}. */
    public synchronized int countSince(PlayerRef killer, String mobTypeName, Instant since) {
        return (int) records.stream()
                .filter(record -> record.killer().equals(killer) && record.mobTypeName().equals(mobTypeName)
                        && !record.timestamp().isBefore(since))
                .count();
    }

    public void addListener(Consumer<MobKillRecord> listener) {
        listeners.add(listener);
    }
}
