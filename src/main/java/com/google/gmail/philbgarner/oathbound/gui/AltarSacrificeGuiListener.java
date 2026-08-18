package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.altar.Altar;
import com.google.gmail.philbgarner.oathbound.altar.EnchantmentMaxLevelLookup;
import com.google.gmail.philbgarner.oathbound.altar.EnchantmentRarityLookup;
import com.google.gmail.philbgarner.oathbound.altar.EnchantmentRarityTier;
import com.google.gmail.philbgarner.oathbound.altar.SacrificeValuationService;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Click/drag/close glue for the altar sacrifice GUI. Enchantment extraction is the one place this
 * touches real Bukkit enchantment types, keeping {@link SacrificeValuationService} itself Bukkit-free. */
public final class AltarSacrificeGuiListener implements Listener {

    private static final EnchantmentMaxLevelLookup MAX_LEVELS = key -> {
        Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.fromString(key));
        return enchantment == null ? 1 : enchantment.getMaxLevel();
    };

    private final OathboundPlugin plugin;

    public AltarSacrificeGuiListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    /** Unlike {@link #MAX_LEVELS}, this needs config (the per-rarity multipliers), so it can't be a
     * static field - built per call, capturing {@code plugin}. Buckets {@code Enchantment.getWeight()}
     * via {@link EnchantmentRarityTier#of} rather than using the deprecated-for-removal
     * {@code Enchantment.getRarity()}/{@code EnchantmentRarity}. */
    private EnchantmentRarityLookup rarityLookup() {
        return key -> {
            Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.fromString(key));
            EnchantmentRarityTier tier = EnchantmentRarityTier.of(enchantment == null ? 10 : enchantment.getWeight());
            return plugin.oathboundConfig().altarRarityMultiplier(tier);
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof AltarSacrificeHolder sacrificeHolder)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = rawSlot >= 0 && rawSlot < topSize;
        if (!clickedTop) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        if (AltarSacrificeHolder.isDepositSlot(rawSlot)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (rawSlot == AltarSacrificeHolder.CONFIRM_SLOT) {
            confirmSacrifice(player, sacrificeHolder);
        } else if (rawSlot == AltarSacrificeHolder.CANCEL_SLOT) {
            player.sendMessage("Sacrifice cancelled.");
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof AltarSacrificeHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        boolean onlyDeposit = event.getRawSlots().stream().allMatch(AltarSacrificeHolder::isDepositSlot);
        if (!onlyDeposit) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof AltarSacrificeHolder holder && !holder.isPosted()) {
            returnDepositedItems(player, event.getInventory());
        }
    }

    private void confirmSacrifice(Player player, AltarSacrificeHolder holder) {
        Altar altar = plugin.altarCache().get(holder.altarId());
        if (altar == null) {
            player.sendMessage("This altar no longer exists.");
            player.closeInventory();
            return;
        }

        Inventory inventory = holder.getInventory();
        for (int slot = 0; slot < AltarSacrificeHolder.DEPOSIT_SLOTS_END_EXCLUSIVE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR && enchantmentsOf(stack).isEmpty()) {
                player.sendMessage("Remove non-enchanted items first - only enchanted items count as artifacts.");
                return;
            }
        }

        List<Map<String, Integer>> perItemEnchants = new ArrayList<>();
        for (int slot = 0; slot < AltarSacrificeHolder.DEPOSIT_SLOTS_END_EXCLUSIVE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                perItemEnchants.add(enchantmentsOf(stack));
            }
        }

        long sacrificeValue = SacrificeValuationService.valueOf(perItemEnchants, MAX_LEVELS, rarityLookup(),
                plugin.oathboundConfig().altarEnchantmentWeightScale(), plugin.oathboundConfig().altarRepeatEnchantmentDecay());
        if (sacrificeValue <= 0) {
            player.sendMessage("Deposit at least one enchanted item.");
            return;
        }

        for (int slot = 0; slot < AltarSacrificeHolder.DEPOSIT_SLOTS_END_EXCLUSIVE; slot++) {
            inventory.setItem(slot, null);
        }

        Instant now = Instant.now();
        int decayDays = plugin.oathboundConfig().altarDecayDays();
        long newBaseline = altar.currentPower(now, decayDays) + sacrificeValue;
        altar.applySacrifice(newBaseline, now, plugin.oathboundConfig().altarReconsecrationCooldown(), sacrificeValue);
        plugin.persistAltarAsync(altar);

        holder.markPosted();
        player.sendMessage("Sacrifice accepted - Altar Power is now " + newBaseline + ".");
        player.closeInventory();
    }

    private Map<String, Integer> enchantmentsOf(ItemStack stack) {
        Map<Enchantment, Integer> enchantments;
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            enchantments = storageMeta.getStoredEnchants();
        } else {
            enchantments = stack.getEnchantments();
        }
        Map<String, Integer> byKey = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            byKey.put(entry.getKey().getKey().toString(), entry.getValue());
        }
        return byKey;
    }

    private void returnDepositedItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < AltarSacrificeHolder.DEPOSIT_SLOTS_END_EXCLUSIVE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                inventory.setItem(slot, null);
                giveOrDrop(player, stack);
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(remaining -> player.getWorld().dropItemNaturally(player.getLocation(), remaining));
    }
}
