package com.google.gmail.philbgarner.oathbound.contract;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Companion to an open {@code Oath}: holds the actual items each side is trading. Kept separate from
 * the generic Clause model on purpose - a two-way immediate swap where the second side's contents
 * aren't known until the contract is accepted doesn't fit the DRAFT-only, immutable-clause invariant
 * the rest of the Oath system relies on, and this is a narrowly-scoped feature, not a redesign of
 * Escrow.
 *
 * <p>{@code creator} always receives {@code fulfillerItems} and {@code fulfiller} always receives
 * {@code creatorItems}. Either side's delivery can be deferred (recipient offline at swap time) and
 * is tracked independently via the delivered flags so nothing is lost.
 */
public final class TradeOffer {
    private final UUID oathId;
    private final PlayerRef creator;
    private final List<SerializedItemStack> creatorItems;
    private PlayerRef fulfiller;
    private List<SerializedItemStack> fulfillerItems = List.of();
    private boolean completed;
    private boolean creatorItemsDelivered;
    private boolean fulfillerItemsDelivered;

    public TradeOffer(UUID oathId, PlayerRef creator, List<SerializedItemStack> creatorItems) {
        this.oathId = Objects.requireNonNull(oathId, "oathId");
        this.creator = Objects.requireNonNull(creator, "creator");
        this.creatorItems = List.copyOf(Objects.requireNonNull(creatorItems, "creatorItems"));
    }

    /** Rehydrates a persisted TradeOffer, including in-progress or completed state. */
    public static TradeOffer reconstruct(UUID oathId, PlayerRef creator, List<SerializedItemStack> creatorItems,
                                          PlayerRef fulfiller, List<SerializedItemStack> fulfillerItems,
                                          boolean completed, boolean creatorItemsDelivered,
                                          boolean fulfillerItemsDelivered) {
        TradeOffer offer = new TradeOffer(oathId, creator, creatorItems);
        offer.fulfiller = fulfiller;
        offer.fulfillerItems = List.copyOf(fulfillerItems);
        offer.completed = completed;
        offer.creatorItemsDelivered = creatorItemsDelivered;
        offer.fulfillerItemsDelivered = fulfillerItemsDelivered;
        return offer;
    }

    public UUID oathId() {
        return oathId;
    }

    public PlayerRef creator() {
        return creator;
    }

    public List<SerializedItemStack> creatorItems() {
        return creatorItems;
    }

    public PlayerRef fulfiller() {
        return fulfiller;
    }

    public List<SerializedItemStack> fulfillerItems() {
        return fulfillerItems;
    }

    public boolean completed() {
        return completed;
    }

    public boolean creatorItemsDelivered() {
        return creatorItemsDelivered;
    }

    public boolean fulfillerItemsDelivered() {
        return fulfillerItemsDelivered;
    }

    public void fulfill(PlayerRef fulfiller, List<SerializedItemStack> fulfillerItems) {
        if (this.fulfiller != null) {
            throw new IllegalStateException("Trade offer for oath " + oathId + " already has a fulfiller");
        }
        this.fulfiller = Objects.requireNonNull(fulfiller, "fulfiller");
        this.fulfillerItems = List.copyOf(Objects.requireNonNull(fulfillerItems, "fulfillerItems"));
        this.completed = true;
    }

    public void markCreatorItemsDelivered() {
        creatorItemsDelivered = true;
    }

    public void markFulfillerItemsDelivered() {
        fulfillerItemsDelivered = true;
    }

    /** True once both sides of the swap have actually reached an inventory. */
    public boolean fullyDelivered() {
        return creatorItemsDelivered && fulfillerItemsDelivered;
    }
}
