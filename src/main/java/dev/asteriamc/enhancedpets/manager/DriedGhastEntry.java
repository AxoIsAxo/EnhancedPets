package dev.asteriamc.enhancedpets.manager;

import org.bukkit.Location;

import java.util.UUID;

public class DriedGhastEntry {
    final UUID placerUUID;
    final Location location;
    final long placedTimestamp;
    private Long waterloggedSince; // null = not currently waterlogged

    public DriedGhastEntry(UUID placerUUID, Location location, long placedTimestamp) {
        this.placerUUID = placerUUID;
        this.location = location;
        this.placedTimestamp = placedTimestamp;
        this.waterloggedSince = null;
    }

    public UUID getPlacerUUID() {
        return placerUUID;
    }

    public Location getLocation() {
        return location;
    }

    public long getTimestamp() {
        return placedTimestamp;
    }

    public Long getWaterloggedSince() {
        return waterloggedSince;
    }

    public void setWaterloggedSince(Long timestamp) {
        this.waterloggedSince = timestamp;
    }

    /**
     * Returns the total time (ms) this block has been waterlogged.
     * Returns 0 if never waterlogged.
     */
    public long getWaterloggedDuration() {
        if (waterloggedSince == null)
            return 0;
        return System.currentTimeMillis() - waterloggedSince;
    }
}
