package dev.asteriamc.enhancedpets.listeners;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last shift-click time for each player so we can detect
 * a double-click within 250 ms.
 */
final class ShiftClickTracker {
    private static final long DOUBLE_CLICK_MS = 250L;
    private final ConcurrentHashMap<UUID, Long> lastClick = new ConcurrentHashMap<>();

    boolean isDoubleClick(UUID player) {
        long now = System.currentTimeMillis();
        Long prev = lastClick.put(player, now);
        return prev != null && (now - prev) <= DOUBLE_CLICK_MS;
    }

    void clear(UUID player) {
        lastClick.remove(player);
    }
}