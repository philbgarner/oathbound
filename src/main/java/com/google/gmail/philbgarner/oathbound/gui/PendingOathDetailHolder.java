package com.google.gmail.philbgarner.oathbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/** Session state for reviewing a single pending named-party oath: read-only clause list plus
 * Sign/Decline buttons. */
public final class PendingOathDetailHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int CLAUSE_LIST_END_EXCLUSIVE = 45;
    public static final int SIGN_SLOT = 48;
    public static final int DECLINE_SLOT = 50;

    private final UUID oathId;
    private Inventory inventory;

    public PendingOathDetailHolder(UUID oathId) {
        this.oathId = Objects.requireNonNull(oathId, "oathId");
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID oathId() {
        return oathId;
    }
}
