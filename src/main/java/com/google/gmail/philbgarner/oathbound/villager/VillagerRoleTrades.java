package com.google.gmail.philbgarner.oathbound.villager;

import java.util.List;
import java.util.Objects;

/** A role's fixed shop stock: what its NPCs sell to players, and what they buy from players. */
public record VillagerRoleTrades(List<VillagerTradeOffer> sells, List<VillagerTradeOffer> buys) {
    public VillagerRoleTrades {
        Objects.requireNonNull(sells, "sells");
        Objects.requireNonNull(buys, "buys");
        sells = List.copyOf(sells);
        buys = List.copyOf(buys);
    }
}
