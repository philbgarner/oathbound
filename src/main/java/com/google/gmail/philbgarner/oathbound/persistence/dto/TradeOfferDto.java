package com.google.gmail.philbgarner.oathbound.persistence.dto;

import com.google.gmail.philbgarner.oathbound.contract.TradeOffer;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;

import java.util.List;
import java.util.UUID;

public record TradeOfferDto(UUID oathId, PlayerRef creator, List<SerializedItemStack> creatorItems,
                             PlayerRef fulfiller, List<SerializedItemStack> fulfillerItems, boolean completed,
                             boolean creatorItemsDelivered, boolean fulfillerItemsDelivered) {

    public static TradeOfferDto from(TradeOffer offer) {
        return new TradeOfferDto(offer.oathId(), offer.creator(), offer.creatorItems(), offer.fulfiller(),
                offer.fulfillerItems(), offer.completed(), offer.creatorItemsDelivered(),
                offer.fulfillerItemsDelivered());
    }

    public TradeOffer toDomain() {
        return TradeOffer.reconstruct(oathId, creator, creatorItems, fulfiller, fulfillerItems, completed,
                creatorItemsDelivered, fulfillerItemsDelivered);
    }
}
