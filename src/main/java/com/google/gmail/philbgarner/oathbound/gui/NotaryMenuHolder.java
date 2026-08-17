package com.google.gmail.philbgarner.oathbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Session state for the small Notary hub screen - no session data needed beyond the inventory itself,
 * since both its actions (start a new draft, review your pending oaths) are player-scoped, not tied to
 * which physical Notary was clicked. */
public final class NotaryMenuHolder implements InventoryHolder {

    public static final int SIZE = 9;
    public static final int NEW_OATH_SLOT = 2;
    public static final int BOUNTY_BOARD_SLOT = 3;
    public static final int PENDING_OATHS_SLOT = 4;
    public static final int CANCEL_SLOT = 6;

    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
