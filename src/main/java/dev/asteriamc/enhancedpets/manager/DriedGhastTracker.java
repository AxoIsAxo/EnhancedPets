package dev.asteriamc.enhancedpets.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.asteriamc.enhancedpets.Enhancedpets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DriedGhastTracker {
    private final Enhancedpets plugin;
    private final Map<String, DriedGhastEntry> trackedBlocks = new ConcurrentHashMap<>();
    private final File storageFile;
    private final Gson gson;
    private BukkitTask waterlogCheckTask;

    public DriedGhastTracker(Enhancedpets plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "dried_ghasts.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
        startWaterlogCheckTask();
    }

    private String key(World world, int x, int y, int z) {
        return world.getName() + ":" + x + ":" + y + ":" + z;
    }

    private String key(Location loc) {
        return key(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public void track(Location loc, UUID placerUUID) {
        String k = key(loc);
        trackedBlocks.put(k, new DriedGhastEntry(placerUUID, loc, System.currentTimeMillis()));
        saveAsync();
    }

    public void remove(Location loc) {
        trackedBlocks.remove(key(loc));
        saveAsync();
    }

    public DriedGhastEntry getExact(World world, int x, int y, int z) {
        return trackedBlocks.get(key(world, x, y, z));
    }

    public List<DriedGhastEntry> getEntriesForPlayer(UUID playerUUID) {
        return trackedBlocks.values().stream()
                .filter(entry -> entry.getPlacerUUID().equals(playerUUID))
                .collect(Collectors.toList());
    }

    public List<DriedGhastEntry> findWithinRadius(Location center, double radius) {
        List<DriedGhastEntry> found = new ArrayList<>();
        double rSq = radius * radius;
        for (DriedGhastEntry entry : trackedBlocks.values()) {
            if (entry.getLocation().getWorld().equals(center.getWorld())) {
                if (entry.getLocation().distanceSquared(center) <= rSq) {
                    found.add(entry);
                }
            }
        }
        return found;
    }

    /**
     * Start a periodic task to check waterlog status of all tracked blocks.
     * Runs every 5 seconds.
     */
    private void startWaterlogCheckTask() {
        waterlogCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean changed = false;
            for (DriedGhastEntry entry : trackedBlocks.values()) {
                try {
                    org.bukkit.block.Block block = entry.getLocation().getBlock();
                    if (!block.getType().name().equalsIgnoreCase("DRIED_GHAST")) {
                        continue; // Block gone, will be cleaned up by spawn event
                    }

                    boolean isWaterlogged = false;
                    if (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged waterloggedData) {
                        isWaterlogged = waterloggedData.isWaterlogged();
                    }

                    if (isWaterlogged && entry.getWaterloggedSince() == null) {
                        // Just became waterlogged
                        entry.setWaterloggedSince(System.currentTimeMillis());
                        changed = true;
                    } else if (!isWaterlogged && entry.getWaterloggedSince() != null) {
                        // Lost waterlogging - reset timer
                        entry.setWaterloggedSince(null);
                        changed = true;
                    }
                } catch (Exception e) {
                    // Block in unloaded chunk, ignore
                }
            }
            if (changed) {
                saveAsync();
            }
        }, 100L, 100L); // Every 5 seconds (100 ticks)
    }

    public void stopTasks() {
        if (waterlogCheckTask != null && !waterlogCheckTask.isCancelled()) {
            waterlogCheckTask.cancel();
        }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public void save() {
        List<Map<String, Object>> serializedList = new ArrayList<>();
        for (DriedGhastEntry entry : trackedBlocks.values()) {
            Map<String, Object> map = new HashMap<>();
            map.put("placer", entry.getPlacerUUID().toString());
            map.put("world", entry.getLocation().getWorld().getName());
            map.put("x", entry.getLocation().getBlockX());
            map.put("y", entry.getLocation().getBlockY());
            map.put("z", entry.getLocation().getBlockZ());
            map.put("timestamp", entry.getTimestamp());
            if (entry.getWaterloggedSince() != null) {
                map.put("waterloggedSince", entry.getWaterloggedSince());
            }
            serializedList.add(map);
        }

        try (FileWriter writer = new FileWriter(storageFile)) {
            gson.toJson(serializedList, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save dried ghasts: " + e.getMessage());
        }
    }

    public void load() {
        if (!storageFile.exists())
            return;

        try (FileReader reader = new FileReader(storageFile)) {
            Type listType = new TypeToken<List<Map<String, Object>>>() {
            }.getType();
            List<Map<String, Object>> data = gson.fromJson(reader, listType);

            if (data == null)
                return;

            for (Map<String, Object> map : data) {
                try {
                    UUID placer = UUID.fromString((String) map.get("placer"));
                    String wName = (String) map.get("world");
                    int x = ((Number) map.get("x")).intValue();
                    int y = ((Number) map.get("y")).intValue();
                    int z = ((Number) map.get("z")).intValue();
                    long timestamp = ((Number) map.get("timestamp")).longValue();

                    World w = Bukkit.getWorld(wName);
                    if (w != null) {
                        Location loc = new Location(w, x, y, z);
                        DriedGhastEntry entry = new DriedGhastEntry(placer, loc, timestamp);

                        // Restore waterloggedSince if present
                        if (map.containsKey("waterloggedSince")) {
                            entry.setWaterloggedSince(((Number) map.get("waterloggedSince")).longValue());
                        }

                        trackedBlocks.put(key(loc), entry);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid dried ghast entry in storage: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load dried ghasts: " + e.getMessage());
        }
    }
}
