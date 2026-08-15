package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.board.BoardLocation;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Session state for "which group should this Oath Board be bound to" - lists groups the actor is
 * currently a member of. */
public final class OathBoardGroupPickerHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int LIST_SLOTS_END_EXCLUSIVE = 45;
    public static final int CANCEL_SLOT = 53;

    private final BoardLocation location;
    private final Map<Integer, UUID> groupIdBySlot;
    private Inventory inventory;

    public OathBoardGroupPickerHolder(BoardLocation location, Map<Integer, UUID> groupIdBySlot) {
        this.location = Objects.requireNonNull(location, "location");
        this.groupIdBySlot = Map.copyOf(groupIdBySlot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public BoardLocation location() {
        return location;
    }

    public UUID groupIdAt(int slot) {
        return groupIdBySlot.get(slot);
    }

    public static boolean isListSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < LIST_SLOTS_END_EXCLUSIVE;
    }
}
