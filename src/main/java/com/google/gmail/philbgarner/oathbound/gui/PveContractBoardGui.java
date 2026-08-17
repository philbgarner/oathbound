package com.google.gmail.philbgarner.oathbound.gui;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.bounty.PveContractDefinition;
import com.google.gmail.philbgarner.oathbound.bounty.PveContractProgress;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Chest GUI listing every admin-authored standing PvE contract from {@code pve-contracts} config -
 * click one to accept it (or see progress on one you've already accepted); contracts are repeatable, so
 * accepting again after a payout just starts the next batch. */
public final class PveContractBoardGui {
    private PveContractBoardGui() {
    }

    public static void open(OathboundPlugin plugin, Player viewer) {
        List<PveContractDefinition> definitions = plugin.oathboundConfig().pveContracts();
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId());

        Map<Integer, String> slotToContractId = new HashMap<>();
        Map<Integer, ItemStack> slotToIcon = new HashMap<>();

        int slot = 0;
        for (PveContractDefinition definition : definitions) {
            if (slot >= PveContractBoardHolder.LIST_SLOTS_END_EXCLUSIVE) {
                break;
            }
            slotToIcon.put(slot, buildIcon(plugin, definition, viewerRef));
            slotToContractId.put(slot, definition.id());
            slot++;
        }

        PveContractBoardHolder holder = new PveContractBoardHolder(slotToContractId);
        Inventory inventory = Bukkit.createInventory(holder, PveContractBoardHolder.SIZE, Component.text("Standing Contracts"));
        holder.setInventory(inventory);

        for (int i = 0; i < PveContractBoardHolder.SIZE; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        slotToIcon.forEach(inventory::setItem);
        inventory.setItem(PveContractBoardHolder.BACK_SLOT, GuiItems.button(Material.ARROW, "Back"));

        if (definitions.isEmpty()) {
            inventory.setItem(4, GuiItems.button(Material.BARRIER, "No standing contracts",
                    "The admin hasn't configured any", "pve-contracts yet."));
        }

        viewer.openInventory(inventory);
    }

    private static ItemStack buildIcon(OathboundPlugin plugin, PveContractDefinition definition, PlayerRef viewer) {
        Optional<PveContractProgress> progress = plugin.pveContractProgressCache().values().stream()
                .filter(p -> p.player().equals(viewer) && p.contractId().equals(definition.id()))
                .findFirst();

        List<String> lore = new ArrayList<>();
        lore.add("Kill " + definition.quantity() + "x " + definition.mobTypeName());
        lore.add("Reward: " + definition.reward());
        if (progress.isPresent()) {
            lore.add("Your progress: " + progress.get().killsSoFar() + "/" + definition.quantity());
            lore.add("Completed " + progress.get().timesCompleted() + " time(s)");
        } else {
            lore.add("Click to accept");
        }

        Material material = spawnEggFor(definition.mobTypeName());
        return GuiItems.button(material, definition.displayName(), lore.toArray(String[]::new));
    }

    private static Material spawnEggFor(String mobTypeName) {
        Material material = Material.matchMaterial(mobTypeName + "_SPAWN_EGG");
        return material != null ? material : Material.TARGET;
    }
}
