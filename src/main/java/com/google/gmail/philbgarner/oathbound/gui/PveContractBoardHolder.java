package com.google.gmail.philbgarner.oathbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/** Session state for the standing PvE contract board - maps each populated slot to the
 * {@code PveContractDefinition.id()} it represents (definitions themselves live in config, not a cache
 * keyed by UUID, so this is a String map rather than the UUID shape every other board GUI uses). */
public final class PveContractBoardHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int BACK_SLOT = 49;
    public static final int LIST_SLOTS_END_EXCLUSIVE = 45;

    private Inventory inventory;
    private final Map<Integer, String> contractIdBySlot;

    public PveContractBoardHolder(Map<Integer, String> contractIdBySlot) {
        this.contractIdBySlot = Map.copyOf(contractIdBySlot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String contractIdAt(int slot) {
        return contractIdBySlot.get(slot);
    }
}
