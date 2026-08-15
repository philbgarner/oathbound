package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/** Session state for depositing enchanted items into an altar's sacrifice ritual. */
public final class AltarSacrificeHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int DEPOSIT_SLOTS_END_EXCLUSIVE = 36;
    public static final int CONFIRM_SLOT = 49;
    public static final int CANCEL_SLOT = 53;

    private final UUID altarId;
    private final PlayerRef depositor;
    private Inventory inventory;
    private boolean posted;

    public AltarSacrificeHolder(UUID altarId, PlayerRef depositor) {
        this.altarId = Objects.requireNonNull(altarId, "altarId");
        this.depositor = Objects.requireNonNull(depositor, "depositor");
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID altarId() {
        return altarId;
    }

    public PlayerRef depositor() {
        return depositor;
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
