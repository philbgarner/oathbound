package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.bounty.BountyTarget;
import com.google.gmail.philbgarner.oathbound.economy.Currency;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.Objects;

/** Session state for the final "here's the fee, confirm?" step of bounty placement - a GUI-click
 * confirm rather than a third chat prompt, since chat-confirm chains get unwieldy. */
public final class BountyConfirmHolder implements InventoryHolder {

    public static final int SIZE = 9;
    public static final int CONFIRM_SLOT = 3;
    public static final int CANCEL_SLOT = 5;

    private Inventory inventory;
    private final BountyTarget target;
    private final int quantity;
    private final Map<Currency, Long> reward;

    public BountyConfirmHolder(BountyTarget target, int quantity, Map<Currency, Long> reward) {
        this.target = Objects.requireNonNull(target, "target");
        this.quantity = quantity;
        this.reward = Map.copyOf(reward);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public BountyTarget target() {
        return target;
    }

    public int quantity() {
        return quantity;
    }

    public Map<Currency, Long> reward() {
        return reward;
    }
}
