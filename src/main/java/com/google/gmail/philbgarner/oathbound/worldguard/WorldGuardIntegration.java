package com.google.gmail.philbgarner.oathbound.worldguard;

import com.google.gmail.philbgarner.oathbound.group.PlayerRef;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Collection;

/**
 * Thin wrapper isolating every WorldGuard/WorldEdit API call to one place, so the rest of the plugin
 * never touches those types directly - WorldGuard is a soft dependency ({@code softdepend} in
 * {@code plugin.yml}, {@code compileOnly} in the build), and Oathbound must load and run fine with it
 * absent. {@link #isAvailable()} gates every other method; callers don't need to check it separately.
 */
public final class WorldGuardIntegration {
    private WorldGuardIntegration() {
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    /** Replaces {@code regionId}'s owner list in {@code world} with exactly {@code members} - a silent
     * no-op (not an error) if WorldGuard is absent, the world has no region manager yet, or the region
     * doesn't exist, since a stale or since-deleted link shouldn't be able to fail a
     * {@code TransferClause} execution over it. */
    public static void syncOwners(World world, String regionId, Collection<PlayerRef> members) {
        if (!isAvailable() || world == null) {
            return;
        }
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return;
        }
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            return;
        }
        DefaultDomain domain = new DefaultDomain();
        for (PlayerRef member : members) {
            domain.addPlayer(member.playerId());
        }
        region.setOwners(domain);
    }
}
