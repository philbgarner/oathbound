package com.google.gmail.philbgarner.oathbound.oath;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Append-only log of every Oath state transition. The single source both the Public Oath Board and
 * any history command should query. Persistence hooks in as a {@link #addListener listener}, keeping
 * this class free of any storage/Bukkit dependency.
 */
public final class Ledger {
    private final List<LedgerEntry> entries = new ArrayList<>();
    private final List<Consumer<LedgerEntry>> listeners = new CopyOnWriteArrayList<>();

    public synchronized LedgerEntry record(UUID oathId, OathState fromState, OathState toState, PlayerRef actor) {
        LedgerEntry entry = new LedgerEntry(UUID.randomUUID(), oathId, fromState, toState, Instant.now(), actor);
        entries.add(entry);
        listeners.forEach(listener -> listener.accept(entry));
        return entry;
    }

    /** Used when rehydrating from persistence at startup - does not notify listeners. */
    public synchronized void loadExisting(LedgerEntry entry) {
        entries.add(entry);
    }

    public synchronized List<LedgerEntry> entriesFor(UUID oathId) {
        return entries.stream().filter(e -> e.oathId().equals(oathId)).toList();
    }

    public synchronized List<LedgerEntry> recent(int limit) {
        int from = Math.max(0, entries.size() - limit);
        return List.copyOf(entries.subList(from, entries.size()));
    }

    public void addListener(Consumer<LedgerEntry> listener) {
        listeners.add(listener);
    }
}
