package com.google.gmail.philbgarner.oathbound.villager;

import org.bukkit.Material;

import java.util.Objects;

/** A single buy or sell entry in a role's fixed, admin-edited trade list - one unit of {@code material}
 * per {@code price} in the server's default currency. */
public record VillagerTradeOffer(Material material, long price) {
    public VillagerTradeOffer {
        Objects.requireNonNull(material, "material");
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive: " + price);
        }
    }
}
