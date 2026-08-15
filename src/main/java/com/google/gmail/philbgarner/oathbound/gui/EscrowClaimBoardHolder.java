package com.google.gmail.philbgarner.oathbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;

/** Session state for the board of claimable escrow item batches - maps each populated slot to the
 * {@code EscrowClaim} it represents, same shape as {@link TradeBoardHolder}. */
public final class EscrowClaimBoardHolder implements InventoryHolder {

    private Inventory inventory;
    private final Map<Integer, UUID> claimIdBySlot;

    public EscrowClaimBoardHolder(Map<Integer, UUID> claimIdBySlot) {
        this.claimIdBySlot = Map.copyOf(claimIdBySlot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID claimIdAt(int slot) {
        return claimIdBySlot.get(slot);
    }
}
