package dev.asteriamc.enhancedpets.data;

import dev.asteriamc.enhancedpets.Enhancedpets;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.World;

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

    // Station Feature
    private Location stationLocation;
    private double stationRadius = 10.0;
    private Set<String> stationTargetTypes = new HashSet<>(); // e.g. "PLAYER", "MOB"

    // Target Feature
    private UUID explicitTargetUUID;

    // Storage Feature
    private boolean stored = false;

    private String displayColor = null;

    private String customIconMaterial = null;

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
                    Enhancedpets.getInstance().getLogger()
                            .warning("Unknown entity type for pet " + petUUID + ". Defaulting to HORSE.");
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

            String displayColor = (String) map.getOrDefault("displayColor", null);
            String customIconMaterial = (String) map.getOrDefault("customIconMaterial", null);

            Set<UUID> friendlies = new HashSet<>();
            Object friendliesObj = map.get("friendlyPlayers");
            if (friendliesObj instanceof List<?> list) {
                for (Object obj : list) {
                    if (obj instanceof String s) {
                        try {
                            friendlies.add(UUID.fromString(s));
                        } catch (IllegalArgumentException ex) {
                            if (Enhancedpets.getInstance() != null) {
                                Enhancedpets.getInstance().getLogger()
                                        .warning("Invalid friendly player UUID '" + s + "' for pet " + petUUID);
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
            data.setStored((boolean) map.getOrDefault("stored", false));
            data.setProtectedFromPlayers(protectedFromPlayers);
            data.setDisplayColor(displayColor);
            data.setCustomIconMaterial(customIconMaterial);
            if (metadata != null) {
                data.setMetadata(metadata);
            }

            // Station Deserialization
            if (map.containsKey("stationLocation")) {
                Map<String, Object> locMap = (Map<String, Object>) map.get("stationLocation");
                try {
                    String wName = (String) locMap.get("world");
                    double x = ((Number) locMap.get("x")).doubleValue();
                    double y = ((Number) locMap.get("y")).doubleValue();
                    double z = ((Number) locMap.get("z")).doubleValue();
                    World w = Bukkit.getWorld(wName);
                    if (w != null) {
                        data.setStationLocation(new Location(w, x, y, z));
                    }
                } catch (Exception ignored) {
                }
            }
            if (map.containsKey("stationRadius")) {
                data.setStationRadius(((Number) map.get("stationRadius")).doubleValue());
            }
            if (map.containsKey("stationTargetTypes")) {
                List<?> rawList = (List<?>) map.get("stationTargetTypes");
                Set<String> types = new HashSet<>();
                for (Object o : rawList) {
                    if (o instanceof String) {
                        types.add((String) o);
                    }
                }
                data.setStationTargetTypes(types);
            }

            // Target Deserialization
            if (map.containsKey("explicitTargetUUID")) {
                try {
                    data.setExplicitTargetUUID(UUID.fromString((String) map.get("explicitTargetUUID")));
                } catch (Exception ignored) {
                }
            }

            if (map.containsKey("aggressiveTargetTypes")) {
                data.setAggressiveTargetTypes(new HashSet<>((List<String>) map.get("aggressiveTargetTypes")));
            }

            return data;
        } catch (Exception var9) {
            if (Enhancedpets.getInstance() != null) {
                Enhancedpets.getInstance().getLogger()
                        .severe("Error deserializing pet data for UUID " + petUUID + ": " + var9.getMessage());
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
        map.put("stored", this.stored);
        map.put("protectedFromPlayers", this.protectedFromPlayers);
        if (this.metadata != null && !this.metadata.isEmpty()) {
            map.put("metadata", this.metadata);
        }

        if (this.displayColor != null) {
            map.put("displayColor", this.displayColor);
        }
        if (this.customIconMaterial != null) {
            map.put("customIconMaterial", this.customIconMaterial);
        }

        // Station Serialization
        if (this.stationLocation != null) {
            Map<String, Object> locMap = new HashMap<>();
            locMap.put("world", this.stationLocation.getWorld().getName());
            locMap.put("x", this.stationLocation.getX());
            locMap.put("y", this.stationLocation.getY());
            locMap.put("z", this.stationLocation.getZ());
            map.put("stationLocation", locMap);
        }
        map.put("stationRadius", this.stationRadius);
        map.put("stationTargetTypes", new ArrayList<>(this.stationTargetTypes));

        // Target Serialization
        if (this.explicitTargetUUID != null) {
            map.put("explicitTargetUUID", this.explicitTargetUUID.toString());
        }
        map.put("aggressiveTargetTypes", new ArrayList<>(this.aggressiveTargetTypes));

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

    public boolean isStored() {
        return stored;
    }

    public void setStored(boolean stored) {
        this.stored = stored;
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

    public String getDisplayColor() {
        return displayColor;
    }

    public void setDisplayColor(String displayColor) {
        this.displayColor = (displayColor != null && !displayColor.isEmpty()) ? displayColor : null;
    }

    public String getCustomIconMaterial() {
        return customIconMaterial;
    }

    public void setCustomIconMaterial(String customIconMaterial) {
        this.customIconMaterial = (customIconMaterial != null && !customIconMaterial.isEmpty()) ? customIconMaterial
                : null;
    }

    public Location getStationLocation() {
        return stationLocation;
    }

    public void setStationLocation(Location stationLocation) {
        this.stationLocation = stationLocation;
    }

    public double getStationRadius() {
        return stationRadius;
    }

    public void setStationRadius(double stationRadius) {
        this.stationRadius = stationRadius;
    }

    public Set<String> getStationTargetTypes() {
        return stationTargetTypes;
    }

    public void setStationTargetTypes(Set<String> stationTargetTypes) {
        this.stationTargetTypes = stationTargetTypes != null ? stationTargetTypes : new HashSet<>();
    }

    public UUID getExplicitTargetUUID() {
        return explicitTargetUUID;
    }

    public void setExplicitTargetUUID(UUID explicitTargetUUID) {
        this.explicitTargetUUID = explicitTargetUUID;
    }

    private Set<String> aggressiveTargetTypes = new HashSet<>(Arrays.asList("MOB", "ANIMAL", "PLAYER"));

    public Set<String> getAggressiveTargetTypes() {
        return aggressiveTargetTypes;
    }

    public void setAggressiveTargetTypes(Set<String> aggressiveTargetTypes) {
        this.aggressiveTargetTypes = aggressiveTargetTypes != null ? aggressiveTargetTypes : new HashSet<>();
    }
}
