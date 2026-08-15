package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.google.gmail.philbgarner.oathbound.gui.ProtectionLockGui;
import com.google.gmail.philbgarner.oathbound.protection.ProtectedLocation;
import com.google.gmail.philbgarner.oathbound.protection.Protection;
import com.google.gmail.philbgarner.oathbound.protection.ProtectionAccessService;
import com.google.gmail.philbgarner.oathbound.protection.ProtectionType;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Bisected;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Right-clicking a container or door while holding the configured lock tool opens the lock GUI
 * (see {@link ProtectionLockGui}); any other interaction with an already-locked block is gated by
 * {@link ProtectionAccessService}. Unprotected blocks are untouched - this plugin only ever denies
 * access to blocks it has explicitly locked.
 */
public final class ProtectionLockListener implements Listener {

    private final OathboundPlugin plugin;

    public ProtectionLockListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        ProtectionType type = classify(block);
        if (type == null) {
            return;
        }

        Player player = event.getPlayer();
        ProtectedLocation location = canonicalLocation(block);

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() == plugin.oathboundConfig().protectionLockToolMaterial()) {
            event.setCancelled(true);
            ProtectionLockGui.open(plugin, player, location, type);
            return;
        }

        Optional<Protection> protection = plugin.protectionCache().values().stream()
                .filter(candidate -> candidate.location().equals(location))
                .findFirst();
        if (protection.isEmpty()) {
            return;
        }

        PlayerRef playerRef = new PlayerRef(player.getUniqueId());
        boolean allowed = ProtectionAccessService.canAccess(protection.get(), playerRef,
                id -> Optional.ofNullable(plugin.groupCache().get(id)));
        if (!allowed) {
            event.setCancelled(true);
            String label = type == ProtectionType.CONTAINER ? "container" : "door";
            player.sendMessage("This " + label + " is locked.");
        }
    }

    private ProtectionType classify(Block block) {
        if (block.getState() instanceof Container) {
            return ProtectionType.CONTAINER;
        }
        if (Tag.DOORS.isTagged(block.getType()) || Tag.TRAPDOORS.isTagged(block.getType())
                || Tag.FENCE_GATES.isTagged(block.getType())) {
            return ProtectionType.DOOR;
        }
        return null;
    }

    /** Doors are two blocks tall - normalize to the bottom half so both halves share one binding. */
    private ProtectedLocation canonicalLocation(Block block) {
        Block canonical = block;
        if (block.getBlockData() instanceof Bisected bisected && bisected.getHalf() == Bisected.Half.TOP) {
            canonical = block.getRelative(BlockFace.DOWN);
        }
        return new ProtectedLocation(canonical.getWorld().getUID(), canonical.getX(), canonical.getY(), canonical.getZ());
    }
}
