package com.google.gmail.philbgarner.oathbound.bukkit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** Builds and reads the tagged {@code PLAYER_HEAD} a bounty victim force-drops on a qualifying kill.
 * The head looks like a normal player head (native owner metadata, no custom item needed) but carries a
 * hidden persistent-data tag naming the specific {@link com.google.gmail.philbgarner.oathbound.bounty.Bounty}
 * it proves - resolving a turn-in back to exactly that bounty (not just "any bounty on this victim")
 * handles a victim having multiple simultaneous bounties, and survives the bounty being
 * cancelled/fulfilled by someone else between drop and turn-in (the turn-in flow re-validates the tagged
 * bounty is still active before paying out). */
public final class BountyHeadItems {
    private static final String KEY_NAME = "bounty_id";

    private BountyHeadItems() {
    }

    public static ItemStack build(Plugin plugin, OfflinePlayer victim, UUID bountyId) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(victim);
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, bountyId.toString());
        head.setItemMeta(meta);
        return head;
    }

    public static Optional<UUID> readBountyId(Plugin plugin, ItemStack stack) {
        if (stack == null || stack.getType() != Material.PLAYER_HEAD || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }
}
