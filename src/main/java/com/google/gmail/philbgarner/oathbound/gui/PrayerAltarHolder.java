package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;

/** Session state for depositing enchanted items into the banishment prayer/intercession ritual - same
 * deposit-slot shape as {@link AltarSacrificeHolder}, but the value is spent reducing {@code target}'s
 * sentence instead of an altar's own Power. */
public final class PrayerAltarHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int DEPOSIT_SLOTS_END_EXCLUSIVE = 36;
    public static final int CONFIRM_SLOT = 49;
    public static final int CANCEL_SLOT = 53;

    private final PlayerRef target;
    private final PlayerRef supplicant;
    private Inventory inventory;
    private boolean posted;

    public PrayerAltarHolder(PlayerRef target, PlayerRef supplicant) {
        this.target = Objects.requireNonNull(target, "target");
        this.supplicant = Objects.requireNonNull(supplicant, "supplicant");
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public PlayerRef target() {
        return target;
    }

    public PlayerRef supplicant() {
        return supplicant;
    }

    public boolean isPosted() {
        return posted;
    }

    public void markPosted() {
        posted = true;
    }

    public static boolean isDepositSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < DEPOSIT_SLOTS_END_EXCLUSIVE;
    }
}
