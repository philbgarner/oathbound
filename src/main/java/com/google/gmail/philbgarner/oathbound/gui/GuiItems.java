package com.google.gmail.philbgarner.oathbound.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

final class GuiItems {
    private GuiItems() {
    }

    static ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plain(name));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore).map(GuiItems::plain).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    static ItemStack filler() {
        return button(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    static void setLore(ItemStack stack, List<String> lore) {
        ItemMeta meta = stack.getItemMeta();
        meta.lore(lore.stream().map(GuiItems::plain).toList());
        stack.setItemMeta(meta);
    }

    static void setDisplayName(ItemStack stack, String name) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plain(name));
        stack.setItemMeta(meta);
    }

    private static Component plain(String text) {
        return Component.text(text).decoration(TextDecoration.ITALIC, false);
    }
}
