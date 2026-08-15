package com.google.gmail.philbgarner.oathbound.oath;

/**
 * Opaque, Bukkit-free placeholder for an escrowed item stack. The Escrow phase adds an adapter that
 * (de)serializes this to/from a real {@code org.bukkit.inventory.ItemStack} via ConfigurationSerializable.
 */
public record SerializedItemStack(byte[] data) {
}
