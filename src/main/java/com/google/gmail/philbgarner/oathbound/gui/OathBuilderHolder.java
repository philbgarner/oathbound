package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/** Session state for the named-party oath builder: lists the draft's clauses so far, with buttons to
 * add a clause of each supported type and to propose or cancel the draft. */
public final class OathBuilderHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int CLAUSE_LIST_END_EXCLUSIVE = 36;
    public static final int ADD_TRANSFER_SLOT = 37;
    public static final int ADD_FLAG_SLOT = 39;
    public static final int ADD_KILLCOUNT_SLOT = 41;
    public static final int PROPOSE_SLOT = 49;
    public static final int CANCEL_SLOT = 53;

    private final UUID oathId;
    private final PlayerRef creator;
    private Inventory inventory;

    public OathBuilderHolder(UUID oathId, PlayerRef creator) {
        this.oathId = Objects.requireNonNull(oathId, "oathId");
        this.creator = Objects.requireNonNull(creator, "creator");
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

    public PlayerRef creator() {
        return creator;
    }
}
