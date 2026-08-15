package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.economy.Currency;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/** Session state for depositing items (and, if set, a fixed amount decided before this screen opened -
 * see {@link OathBuilderListener}) into a new escrow clause. */
public final class EscrowDepositHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int DEPOSIT_SLOTS_END_EXCLUSIVE = 36;
    public static final int CONFIRM_SLOT = 49;
    public static final int CANCEL_SLOT = 53;

    private final UUID oathId;
    private final PlayerRef depositor;
    private final Currency currency;
    private final long currencyAmount;
    private Inventory inventory;
    private boolean posted;

    public EscrowDepositHolder(UUID oathId, PlayerRef depositor, Currency currency, long currencyAmount) {
        this.oathId = Objects.requireNonNull(oathId, "oathId");
        this.depositor = Objects.requireNonNull(depositor, "depositor");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.currencyAmount = currencyAmount;
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

    public PlayerRef depositor() {
        return depositor;
    }

    public Currency currency() {
        return currency;
    }

    public long currencyAmount() {
        return currencyAmount;
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
