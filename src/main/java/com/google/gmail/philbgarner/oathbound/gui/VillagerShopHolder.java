package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.villager.VillagerTradeOffer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/** Session state for a villager shop menu: which trade offer (if any) sits behind each clicked slot, and
 * whether it's a sell-to-player or buy-from-player offer. */
public final class VillagerShopHolder implements InventoryHolder {

    public static final int SIZE = 45;
    public static final int LABEL_SLOT = 13;
    public static final int CLOSE_SLOT = 40;

    private final Map<Integer, VillagerTradeOffer> sellSlots;
    private final Map<Integer, VillagerTradeOffer> buySlots;
    private Inventory inventory;

    VillagerShopHolder(Map<Integer, VillagerTradeOffer> sellSlots, Map<Integer, VillagerTradeOffer> buySlots) {
        this.sellSlots = Map.copyOf(sellSlots);
        this.buySlots = Map.copyOf(buySlots);
    }

    /** The offer the NPC sells to the player at this slot, or null. */
    public VillagerTradeOffer sellOfferAt(int slot) {
        return sellSlots.get(slot);
    }

    /** The offer the NPC buys from the player at this slot, or null. */
    public VillagerTradeOffer buyOfferAt(int slot) {
        return buySlots.get(slot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
