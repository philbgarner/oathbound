package com.google.gmail.philbgarner.oathbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;

/** Session state for the board of named-party oaths proposed to the viewer - maps each populated slot
 * to the oath it represents, same shape as {@link TradeBoardHolder}. */
public final class PendingOathBoardHolder implements InventoryHolder {

    private Inventory inventory;
    private final Map<Integer, UUID> oathIdBySlot;

    public PendingOathBoardHolder(Map<Integer, UUID> oathIdBySlot) {
        this.oathIdBySlot = Map.copyOf(oathIdBySlot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID oathIdAt(int slot) {
        return oathIdBySlot.get(slot);
    }
}
