package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.protection.ProtectedLocation;
import com.google.gmail.philbgarner.oathbound.protection.ProtectionType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Session state for the "which group should this chest/door be locked to" screen, opened by
 * right-clicking a protectable block while holding the configured lock tool. */
public final class ProtectionLockHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int LIST_SLOTS_END_EXCLUSIVE = 45;
    public static final int UNLOCK_SLOT = 49;
    public static final int CANCEL_SLOT = 53;

    private final ProtectedLocation location;
    private final ProtectionType type;
    private final boolean unlockAvailable;
    private final Map<Integer, UUID> groupIdBySlot;
    private Inventory inventory;

    public ProtectionLockHolder(ProtectedLocation location, ProtectionType type, boolean unlockAvailable,
                                 Map<Integer, UUID> groupIdBySlot) {
        this.location = Objects.requireNonNull(location, "location");
        this.type = Objects.requireNonNull(type, "type");
        this.unlockAvailable = unlockAvailable;
        this.groupIdBySlot = Map.copyOf(groupIdBySlot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public ProtectedLocation location() {
        return location;
    }

    public ProtectionType type() {
        return type;
    }

    public boolean unlockAvailable() {
        return unlockAvailable;
    }

    public UUID groupIdAt(int slot) {
        return groupIdBySlot.get(slot);
    }

    public static boolean isListSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < LIST_SLOTS_END_EXCLUSIVE;
    }
}
