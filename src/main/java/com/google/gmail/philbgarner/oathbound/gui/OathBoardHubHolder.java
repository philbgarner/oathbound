package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.board.BoardLocation;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;

/** Session state for the small Oath Board hub screen, opened by right-clicking an installed board
 * block: browse its feed, bind it to a group, or make it capital (unbound). */
public final class OathBoardHubHolder implements InventoryHolder {

    public static final int SIZE = 9;
    public static final int BROWSE_SLOT = 1;
    public static final int BIND_GROUP_SLOT = 3;
    public static final int MAKE_CAPITAL_SLOT = 5;
    public static final int CANCEL_SLOT = 7;

    private final BoardLocation location;
    private Inventory inventory;

    public OathBoardHubHolder(BoardLocation location) {
        this.location = Objects.requireNonNull(location, "location");
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
}
