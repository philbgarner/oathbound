package com.google.gmail.philbgarner.oathbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/** Session state for one player's "fulfill this open contract" GUI. */
public final class TradeFulfillHolder implements InventoryHolder {

    public static final int SIZE = 45;
    public static final int DISPLAY_SLOTS_END_EXCLUSIVE = 18;
    public static final int DEPOSIT_SLOTS_START = 18;
    public static final int DEPOSIT_SLOTS_END_EXCLUSIVE = 36;
    public static final int CONFIRM_SLOT = 40;
    public static final int CANCEL_SLOT = 44;

    private final UUID oathId;
    private Inventory inventory;
    private boolean confirmed;

    public TradeFulfillHolder(UUID oathId) {
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

    public boolean isConfirmed() {
        return confirmed;
    }

    public void markConfirmed() {
        confirmed = true;
    }

    public static boolean isDisplaySlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < DISPLAY_SLOTS_END_EXCLUSIVE;
    }

    public static boolean isDepositSlot(int rawSlot) {
        return rawSlot >= DEPOSIT_SLOTS_START && rawSlot < DEPOSIT_SLOTS_END_EXCLUSIVE;
    }
}
