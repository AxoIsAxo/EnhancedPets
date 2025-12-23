package dev.asteriamc.enhancedpets.listeners;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.manager.DriedGhastEntry;
import dev.asteriamc.enhancedpets.manager.DriedGhastTracker;
import dev.asteriamc.enhancedpets.manager.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public class DriedGhastListener implements Listener {
    private final Enhancedpets plugin;
    private final DriedGhastTracker tracker;
    private final PetManager petManager;

    public DriedGhastListener(Enhancedpets plugin) {
        this.plugin = plugin;
        this.tracker = plugin.getDriedGhastTracker();
        this.petManager = plugin.getPetManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType().name().equalsIgnoreCase("DRIED_GHAST")) {
            tracker.track(event.getBlock().getLocation(), event.getPlayer().getUniqueId());
            plugin.getLanguageManager().sendMessage(event.getPlayer(), "event.ghast_placed_tracking");
            plugin.debugLog("Tracking Dried Ghast placed by " + event.getPlayer().getName() + " at "
                    + event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType().name().equalsIgnoreCase("DRIED_GHAST")) {
            tracker.remove(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType().name().equalsIgnoreCase("DRIED_GHAST")) {
                tracker.remove(block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType().name().equalsIgnoreCase("DRIED_GHAST")) {
                tracker.remove(block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (block.getType().name().equalsIgnoreCase("DRIED_GHAST")) {
                tracker.remove(block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (block.getType().name().equalsIgnoreCase("DRIED_GHAST")) {
                tracker.remove(block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!event.getEntityType().name().equalsIgnoreCase("HAPPY_GHAST"))
            return;

        Entity entity = event.getEntity();
        plugin.debugLog("Happy Ghast spawn detected at " + event.getLocation());

        if (entity instanceof Ageable ageable && !ageable.isAdult()) {
            plugin.debugLog("Happy Ghast is a baby (Ghastling), checking for tracked blocks...");
            Location spawnLoc = event.getLocation();

            // Find nearby tracked Dried Ghast blocks
            List<DriedGhastEntry> candidates = tracker.findWithinRadius(spawnLoc, 4.0);
            plugin.debugLog("Found " + candidates.size() + " candidate tracked block(s) within 4 blocks");

            // FIX: We detect which block is NO LONGER a Dried Ghast - that's the one that
            // transformed.
            // Since we track who placed each block, we use that specific placer's UUID.
            // Only the transformed block will pass the "not DRIED_GHAST" check below.
            for (DriedGhastEntry entry : candidates) {
                // Verify transformation: The block at the tracked location should NO LONGER be
                // DRIED_GHAST
                Block block = entry.getLocation().getBlock();
                plugin.debugLog(
                        "Checking block at " + entry.getLocation() + " - Current type: " + block.getType().name());

                if (!block.getType().name().equalsIgnoreCase("DRIED_GHAST")) {
                    // It transformed!
                    plugin.debugLog("Block transformed! Registering pet for owner " + entry.getPlacerUUID());

                    String defaultName = petManager.assignNewDefaultName(event.getEntityType());
                    var registeredPet = petManager.registerNonTameablePet(entity, entry.getPlacerUUID(), defaultName);

                    if (registeredPet != null) {
                        plugin.debugLog(
                                "Successfully registered Happy Ghast as pet: " + registeredPet.getDisplayName());

                        Player owner = Bukkit.getPlayer(entry.getPlacerUUID());
                        if (owner != null && owner.isOnline()) {
                            plugin.getLanguageManager().sendMessage(owner, "event.ghastling_hatched");
                            owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        }
                    } else {
                        plugin.getLogger()
                                .warning("Failed to register Happy Ghast as pet for " + entry.getPlacerUUID());
                    }

                    tracker.remove(entry.getLocation());
                    plugin.debugLog("Happy Ghast hatched from tracked block at " + entry.getLocation() + " for owner "
                            + entry.getPlacerUUID());
                    return; // Only one pet per spawn
                }
            }
            plugin.debugLog("No matching transformed block found for this Ghastling spawn");
        } else {
            plugin.debugLog("Happy Ghast is adult, ignoring");
        }
    }
}
