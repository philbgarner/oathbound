package com.google.gmail.philbgarner.oathbound.honor;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Single global Honor score per player - no per-relationship tracking, per the design doc. Fully
 * native, mirrors {@link com.google.gmail.philbgarner.oathbound.economy.EconomyService}'s cache/persist
 * shape minus the multi-currency dimension (Honor has no currency, just one scalar per player). Unlike
 * currency, Honor is allowed to go negative - it's a reputation score, not a spendable balance. */
public final class HonorService {
    private final Map<PlayerRef, Long> honor = new ConcurrentHashMap<>();

    public long honor(PlayerRef player) {
        return honor.getOrDefault(player, 0L);
    }

    /** Applies a signed delta (positive = gain, negative = loss) and returns the resulting total. */
    public long adjust(PlayerRef player, long delta) {
        return honor.merge(player, delta, Long::sum);
    }

    public void loadHonor(PlayerHonor record) {
        honor.put(record.player(), record.value());
    }

    public List<PlayerHonor> allHonor() {
        List<PlayerHonor> result = new ArrayList<>();
        honor.forEach((player, value) -> result.add(new PlayerHonor(player, value)));
        return result;
    }
}
