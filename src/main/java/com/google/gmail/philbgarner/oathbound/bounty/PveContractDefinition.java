package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.economy.Currency;

import java.util.Map;
import java.util.Objects;

/** Admin-authored, standing PvE kill contract declared in {@code config.yml} - not persisted, since it's
 * static configuration rather than runtime player data. Only per-player progress against one is real
 * state, see {@link PveContractProgress}.
 *
 * <p>{@code mobTypeName} is a plain {@code org.bukkit.entity.EntityType} enum name rather than the enum
 * itself - the whole {@code bounty} package is Bukkit-free by design (mirrors {@code BountyTarget}
 * deliberately not carrying a mob variant, for the identical reason: paper-api is {@code compileOnly},
 * not present at all on the test compile/runtime classpath, so any Bukkit type reachable from a
 * constructor here would make this class - and anything that references it, like
 * {@link PveContractService} - impossible to unit test). Bukkit-glue code resolves the real
 * {@code EntityType} via {@code EntityType.valueOf(mobTypeName())} at the point of use. */
public record PveContractDefinition(String id, String displayName, String mobTypeName, int quantity,
                                     Map<Currency, Long> reward) {
    public PveContractDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(mobTypeName, "mobTypeName");
        Objects.requireNonNull(reward, "reward");
        reward = Map.copyOf(reward);
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
