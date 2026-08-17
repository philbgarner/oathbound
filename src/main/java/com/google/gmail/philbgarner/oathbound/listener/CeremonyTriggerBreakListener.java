package com.google.gmail.philbgarner.oathbound.listener;

import com.google.gmail.philbgarner.oathbound.OathboundPlugin;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTrigger;
import com.google.gmail.philbgarner.oathbound.ceremony.CeremonyTriggerLocation;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/** Breaking a bound plate/button unbinds it outright - mirrors {@code AltarDesecrationListener}'s
 * cache-scan-then-delete cleanup shape, kept as its own class since it's a different event type from
 * {@code CeremonyTriggerListener}'s bind/activate handling. */
public final class CeremonyTriggerBreakListener implements Listener {

    private final OathboundPlugin plugin;

    public CeremonyTriggerBreakListener(OathboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!Tag.PRESSURE_PLATES.isTagged(block.getType()) && !Tag.BUTTONS.isTagged(block.getType())) {
            return;
        }
        CeremonyTriggerLocation location = new CeremonyTriggerLocation(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        CeremonyTrigger trigger = findByLocation(location);
        if (trigger == null) {
            return;
        }

        plugin.ceremonyTriggerCache().remove(trigger.id());
        plugin.deleteCeremonyTriggerAsync(trigger.id());
        event.getPlayer().sendMessage("Ceremony trigger removed.");
    }

    private CeremonyTrigger findByLocation(CeremonyTriggerLocation location) {
        for (CeremonyTrigger trigger : plugin.ceremonyTriggerCache().values()) {
            if (trigger.location().equals(location)) {
                return trigger;
            }
        }
        return null;
    }
}
