package com.google.gmail.philbgarner.oathbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Session state for the read-only Oath Board feed screen - just marks the inventory as belonging to
 * this screen so clicks/drags get cancelled; there's nothing to click through to. */
public final class OathBoardFeedHolder implements InventoryHolder {

    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
