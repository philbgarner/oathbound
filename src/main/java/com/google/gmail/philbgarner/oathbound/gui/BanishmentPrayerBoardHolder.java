package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/** Session state for the Altar of Intercession board: every currently-serving banishment fills the top
 * rows (click one to open {@link PrayerAltarGui} for that target), a bottom row holds "Close". */
public final class BanishmentPrayerBoardHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int LIST_SLOTS_END_EXCLUSIVE = 45;
    public static final int CLOSE_SLOT = 49;

    private Inventory inventory;
    private final Map<Integer, PlayerRef> targetBySlot;

    public BanishmentPrayerBoardHolder(Map<Integer, PlayerRef> targetBySlot) {
        this.targetBySlot = Map.copyOf(targetBySlot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public PlayerRef targetAt(int slot) {
        return targetBySlot.get(slot);
    }
}
