package com.google.gmail.philbgarner.oathbound.bounty;

import com.google.gmail.philbgarner.oathbound.economy.EconomyService;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Accepts, tracks, and pays out standing PvE kill contracts. The simplest piece of the whole feature -
 * definitions are static admin config (not persisted, see {@link PveContractDefinition}), targeting is
 * native Bukkit kill-attribution (an {@code EntityDeathEvent}'s killer, unlike bounty head-return which
 * deliberately avoids attribution), and there's no heat, fee, or banishment involved at all.
 */
public final class PveContractService {

    private final EconomyService economyService;

    public PveContractService(EconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
    }

    /** Returns the player's existing progress against this contract, or starts a fresh one. */
    public PveContractProgress accept(PlayerRef player, PveContractDefinition definition, Instant now,
                                       Collection<PveContractProgress> existing) {
        return existing.stream()
                .filter(progress -> progress.player().equals(player) && progress.contractId().equals(definition.id()))
                .findFirst()
                .orElseGet(() -> new PveContractProgress(UUID.randomUUID(), player, definition.id(), 0, now, 0));
    }

    /** Records one qualifying kill; pays out and resets progress (contracts are repeatable) the moment
     * the required quantity is reached. Returns true if this kill just completed and paid out a batch. */
    public boolean recordKill(PveContractProgress progress, PveContractDefinition definition) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(definition, "definition");
        progress.recordKill();
        if (!progress.isComplete(definition.quantity())) {
            return false;
        }
        definition.reward().forEach((currency, amount) -> {
            if (amount > 0) {
                economyService.deposit(progress.player(), currency, amount);
            }
        });
        progress.resetForRepeat();
        return true;
    }
}
