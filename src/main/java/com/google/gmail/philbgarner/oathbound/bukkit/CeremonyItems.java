package com.google.gmail.philbgarner.oathbound.bukkit;

import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTemplateDefinition;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Builds and reads the tagged ceremony item a liege right-clicks a prospective party with to start a
 * Ceremony Designer exchange. Carries two persistent-data values, not one - the template id (which
 * ceremony this is) and the liege {@code ProtectionGroup} this specific item is bound to (which
 * territory reverts on breach) - so one admin-authored template can be reused by any liege for any of
 * their own groups, the same "generic item + one bound-in runtime parameter" pattern
 * {@link BountyHeadItems} already uses for a bounty id. */
public final class CeremonyItems {
    private static final String TEMPLATE_KEY_NAME = "ceremony_template_id";
    private static final String LIEGE_GROUP_KEY_NAME = "ceremony_liege_group_id";

    private CeremonyItems() {
    }

    public static ItemStack build(Plugin plugin, CeremonyTemplateDefinition template, UUID liegeGroupId) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(liegeGroupId, "liegeGroupId");
        Material material = Material.matchMaterial(template.itemMaterialName());
        if (material == null) {
            throw new IllegalArgumentException("Unknown ceremony item material: " + template.itemMaterialName());
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(template.itemDisplayName());
        meta.getPersistentDataContainer().set(templateKey(plugin), PersistentDataType.STRING, template.id());
        meta.getPersistentDataContainer().set(liegeGroupKey(plugin), PersistentDataType.STRING, liegeGroupId.toString());
        item.setItemMeta(meta);
        return item;
    }

    public static Optional<String> readTemplateId(Plugin plugin, ItemStack stack) {
        return readString(plugin, stack, templateKey(plugin));
    }

    public static Optional<UUID> readLiegeGroupId(Plugin plugin, ItemStack stack) {
        return readString(plugin, stack, liegeGroupKey(plugin)).flatMap(raw -> {
            try {
                return Optional.of(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    private static Optional<String> readString(Plugin plugin, ItemStack stack, NamespacedKey key) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    private static NamespacedKey templateKey(Plugin plugin) {
        return new NamespacedKey(plugin, TEMPLATE_KEY_NAME);
    }

    private static NamespacedKey liegeGroupKey(Plugin plugin) {
        return new NamespacedKey(plugin, LIEGE_GROUP_KEY_NAME);
    }
}
