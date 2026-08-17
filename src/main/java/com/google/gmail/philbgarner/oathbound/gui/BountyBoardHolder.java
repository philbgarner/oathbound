package com.google.gmail.philbgarner.oathbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;

/** Session state for the Bounty Board: active bounties fill the top rows (click your own to cancel it),
 * a bottom row holds the "Place New Bounty" and "Back" controls. */
public final class BountyBoardHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int PLACE_NEW_SLOT = 49;
    public static final int STANDING_CONTRACTS_SLOT = 47;
    public static final int BACK_SLOT = 45;
    public static final int LIST_SLOTS_END_EXCLUSIVE = 45;

    private Inventory inventory;
    private final Map<Integer, UUID> bountyIdBySlot;

    public BountyBoardHolder(Map<Integer, UUID> bountyIdBySlot) {
        this.bountyIdBySlot = Map.copyOf(bountyIdBySlot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID bountyIdAt(int slot) {
        return bountyIdBySlot.get(slot);
    }
}
