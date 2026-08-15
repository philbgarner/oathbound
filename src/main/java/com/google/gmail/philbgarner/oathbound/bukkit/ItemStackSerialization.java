package com.google.gmail.philbgarner.oathbound.bukkit;

import com.google.gmail.philbgarner.oathbound.oath.SerializedItemStack;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;

/** Bridges the Bukkit-free {@link SerializedItemStack} placeholder to real Bukkit items, via Paper's
 * native {@code ConfigurationSerializable} support - no custom NBT encoding needed. */
public final class ItemStackSerialization {
    private static final String KEY = "item";

    private ItemStackSerialization() {
    }

    public static SerializedItemStack serialize(ItemStack stack) {
        YamlConfiguration config = new YamlConfiguration();
        config.set(KEY, stack);
        return new SerializedItemStack(config.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    public static ItemStack deserialize(SerializedItemStack serialized) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(new String(serialized.data(), StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException("Corrupt serialized item stack", e);
        }
        return config.getItemStack(KEY);
    }
}
