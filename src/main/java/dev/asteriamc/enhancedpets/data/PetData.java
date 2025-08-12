package dev.asteriamc.enhancedpets.data;

import dev.asteriamc.enhancedpets.Enhancedpets;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.stream.Collectors;

public class PetData {
    private final UUID petUUID;
    private UUID ownerUUID;
    private EntityType entityType;
    private String displayName;
    private BehaviorMode mode = BehaviorMode.NEUTRAL;
    private Set<UUID> friendlyPlayers = new HashSet<>();
    private boolean favorite = false;
    private boolean growthPaused = false;
    private int pausedAgeTicks = 0;
    private boolean dead = false;
    private Map<String, Object> metadata = new HashMap<>();
    private boolean protectedFromPlayers = false;

    public PetData(UUID petUUID, UUID ownerUUID, EntityType entityType, String displayName) {
        this.petUUID = petUUID;
        this.ownerUUID = ownerUUID;
        this.entityType = entityType;
        this.displayName = displayName;
        this.friendlyPlayers = new HashSet<>();
        this.mode = BehaviorMode.NEUTRAL;
    }

    public static PetData deserialize(UUID petUUID, Map<String, Object> map) {
        try {
            UUID owner = UUID.fromString((String) map.get("owner"));

            EntityType type;
            try {
                String typeName = (String) map.getOrDefault("type", "UNKNOWN");
                type = EntityType.valueOf(typeName);
            } catch (Exception ex) {
                type = EntityType.HORSE;
                if (Enhancedpets.getInstance() != null) {
                    Enhancedpets.getInstance().getLogger().warning("Unknown entity type for pet " + petUUID + ". Defaulting to HORSE.");
                }
            }

            String name = (String) map.getOrDefault("displayName", "Unknown Pet");
            BehaviorMode mode = BehaviorMode.valueOf((String) map.getOrDefault("mode", "NEUTRAL"));
            boolean favorite = (boolean) map.getOrDefault("favorite", false);
            boolean growthPaused = (boolean) map.getOrDefault("growthPaused", false);
            int pausedAgeTicks = ((Number) map.getOrDefault("pausedAgeTicks", 0)).intValue();
            boolean dead = (boolean) map.getOrDefault("dead", false);
            boolean protectedFromPlayers = (boolean) map.getOrDefault("protectedFromPlayers", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");

            Set<UUID> friendlies = new HashSet<>();
            Object friendliesObj = map.get("friendlyPlayers");
            if (friendliesObj instanceof List<?> list) {
                for (Object obj : list) {
                    if (obj instanceof String s) {
                        try {
                            friendlies.add(UUID.fromString(s));
                        } catch (IllegalArgumentException ex) {
                            if (Enhancedpets.getInstance() != null) {
                                Enhancedpets.getInstance().getLogger().warning("Invalid friendly player UUID '" + s + "' for pet " + petUUID);
                            }
                        }
                    }
                }
            }

            PetData data = new PetData(petUUID, owner, type, name);
            data.setMode(mode);
            data.setFriendlyPlayers(friendlies);
            data.setFavorite(favorite);
            data.setGrowthPaused(growthPaused);
            data.setPausedAgeTicks(pausedAgeTicks);
            data.setDead(dead);
            data.setProtectedFromPlayers(protectedFromPlayers);
            if (metadata != null) {
                data.setMetadata(metadata);
            }
            return data;
        } catch (Exception var9) {
            if (Enhancedpets.getInstance() != null) {
                Enhancedpets.getInstance().getLogger().severe("Error deserializing pet data for UUID " + petUUID + ": " + var9.getMessage());
            }
            var9.printStackTrace();
            return null;
        }
    }

    public Map<String, Object> getMetadata() {
        return this.metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public boolean isProtectedFromPlayers() {
        return this.protectedFromPlayers;
    }

    public void setProtectedFromPlayers(boolean protectedFromPlayers) {
        this.protectedFromPlayers = protectedFromPlayers;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("owner", this.ownerUUID.toString());
        map.put("type", this.entityType.name());
        map.put("displayName", this.displayName);
        map.put("mode", this.mode.name());
        map.put("friendlyPlayers", this.friendlyPlayers.stream().map(UUID::toString).collect(Collectors.toList()));
        map.put("favorite", this.favorite);
        map.put("growthPaused", this.growthPaused);
        map.put("pausedAgeTicks", this.pausedAgeTicks);
        map.put("dead", this.dead);
        map.put("protectedFromPlayers", this.protectedFromPlayers);
        if (this.metadata != null && !this.metadata.isEmpty()) {
            map.put("metadata", this.metadata);
        }
        return map;
    }

    public UUID getPetUUID() {
        return this.petUUID;
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public EntityType getEntityType() {
        return this.entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public boolean isGrowthPaused() {
        return growthPaused;
    }

    public void setGrowthPaused(boolean growthPaused) {
        this.growthPaused = growthPaused;
    }

    public boolean isDead() {
        return dead;
    }

    public void setDead(boolean dead) {
        this.dead = dead;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public BehaviorMode getMode() {
        return this.mode;
    }

    public void setMode(BehaviorMode mode) {
        this.mode = mode;
    }

    public Set<UUID> getFriendlyPlayers() {
        return new HashSet<>(this.friendlyPlayers);
    }

    public void setFriendlyPlayers(Set<UUID> friendlyPlayers) {
        this.friendlyPlayers = friendlyPlayers != null ? new HashSet<>(friendlyPlayers) : new HashSet<>();
    }

    public void addFriendlyPlayer(UUID playerUUID) {
        this.friendlyPlayers.add(playerUUID);
    }

    public void removeFriendlyPlayer(UUID playerUUID) {
        this.friendlyPlayers.remove(playerUUID);
    }

    public boolean isFriendlyPlayer(UUID playerUUID) {
        return this.ownerUUID.equals(playerUUID) || this.friendlyPlayers.contains(playerUUID);
    }

    public boolean isFavorite() {
        return this.favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public int getPausedAgeTicks() {
        return this.pausedAgeTicks;
    }


    public void setPausedAgeTicks(int pausedAgeTicks) {
        this.pausedAgeTicks = pausedAgeTicks;
    }
}