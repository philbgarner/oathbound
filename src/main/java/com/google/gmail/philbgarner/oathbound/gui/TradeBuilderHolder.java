package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;

/** Session state for one player's open "post a trade contract" GUI. */
public final class TradeBuilderHolder implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int DEPOSIT_SLOTS_END_EXCLUSIVE = 18;
    public static final int POST_SLOT = 22;
    public static final int CANCEL_SLOT = 26;

    private final PlayerRef creator;
    private Inventory inventory;
    private boolean posted;

    public TradeBuilderHolder(PlayerRef creator) {
        this.creator = Objects.requireNonNull(creator, "creator");
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public PlayerRef creator() {
        return creator;
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
