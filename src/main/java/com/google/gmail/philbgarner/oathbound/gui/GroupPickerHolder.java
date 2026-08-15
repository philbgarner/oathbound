package com.google.gmail.philbgarner.oathbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Session state for the reusable "pick one of these groups" screen, used both to pick the subject
 * group of a transfer clause and to pick its new-owner group. */
public final class GroupPickerHolder implements InventoryHolder {

    public enum Purpose { SUBJECT_GROUP, OWNER_TARGET_GROUP }

    public static final int SIZE = 54;
    public static final int LIST_SLOTS_END_EXCLUSIVE = 45;
    public static final int PLAYER_INSTEAD_SLOT = 49;
    public static final int CANCEL_SLOT = 53;

    private final UUID oathId;
    private final Purpose purpose;
    private final UUID subjectGroupId;
    private final Map<Integer, UUID> groupIdBySlot;
    private Inventory inventory;

    public GroupPickerHolder(UUID oathId, Purpose purpose, UUID subjectGroupId, Map<Integer, UUID> groupIdBySlot) {
        this.oathId = Objects.requireNonNull(oathId, "oathId");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.subjectGroupId = subjectGroupId;
        this.groupIdBySlot = Map.copyOf(groupIdBySlot);
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

    public Purpose purpose() {
        return purpose;
    }

    /** The already-chosen subject group, set only once {@link #purpose()} is {@link Purpose#OWNER_TARGET_GROUP}. */
    public UUID subjectGroupId() {
        return subjectGroupId;
    }

    public UUID groupIdAt(int slot) {
        return groupIdBySlot.get(slot);
    }

    public static boolean isListSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < LIST_SLOTS_END_EXCLUSIVE;
    }
}
