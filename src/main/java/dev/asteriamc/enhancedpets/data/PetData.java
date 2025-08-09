package dev.asteriamc.enhancedpets.data;

import dev.asteriamc.enhancedpets.Enhancedpets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Tameable;

public class PetData {
   private final UUID petUUID;
   private UUID ownerUUID;
   private EntityType entityType;
   private String displayName;
   private BehaviorMode mode = BehaviorMode.NEUTRAL;
   private Set<UUID> friendlyPlayers = new HashSet<>();
   private boolean favorite = false;
   private boolean growthPaused = false;
   private boolean dead = false;
   private Map<String, Object> metadata = new HashMap<>(); 

   public PetData(UUID petUUID, UUID ownerUUID, EntityType entityType, String displayName) {
      this.petUUID = petUUID;
      this.ownerUUID = ownerUUID;
      this.entityType = entityType;
      this.displayName = displayName;
      this.friendlyPlayers = new HashSet<>();
      this.mode = BehaviorMode.NEUTRAL;
   }

   

   
   public Map<String, Object> getMetadata() {
      return this.metadata;
   }

   public void setMetadata(Map<String, Object> metadata) {
      this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
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
      map.put("dead", this.dead);
      if (this.metadata != null && !this.metadata.isEmpty()) { 
         map.put("metadata", this.metadata);
      }
      return map;
   }

   public static PetData deserialize(UUID petUUID, Map<String, Object> map) {
      try {
         UUID owner = UUID.fromString((String)map.get("owner"));
         EntityType type = EntityType.valueOf((String)map.getOrDefault("type", "UNKNOWN"));
         String name = (String)map.getOrDefault("displayName", "Unknown Pet");
         BehaviorMode mode = BehaviorMode.valueOf((String)map.getOrDefault("mode", "NEUTRAL"));
         boolean favorite = (boolean) map.getOrDefault("favorite", false);
         boolean growthPaused = (boolean) map.getOrDefault("growthPaused", false);
         boolean dead = (boolean) map.getOrDefault("dead", false);

         Map<String, Object> metadata = (Map<String, Object>) map.get("metadata"); 

         Set<UUID> friendlies = new HashSet<>();
         Object friendliesObj = map.get("friendlyPlayers");
         if (friendliesObj instanceof List) {
            ((List)friendliesObj).forEach(obj -> {
               if (obj instanceof String) {
                  try {
                     friendlies.add(UUID.fromString((String)obj));
                  } catch (IllegalArgumentException var4x) {
                     if (Enhancedpets.getInstance() != null) {
                        Enhancedpets.getInstance().getLogger().warning("Invalid friendly player UUID string '" + obj + "' for pet " + petUUID);
                     }
                  }
               }
            });
         }

         PetData data = new PetData(petUUID, owner, type, name);
         data.setMode(mode);
         data.setFriendlyPlayers(friendlies);
         data.setFavorite(favorite);
         data.setGrowthPaused(growthPaused);
         data.setDead(dead);
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

   
   public UUID getPetUUID() { return this.petUUID; }
   public UUID getOwnerUUID() { return this.ownerUUID; }
   public EntityType getEntityType() { return this.entityType; }
   public boolean isGrowthPaused() { return growthPaused; }
   public void setGrowthPaused(boolean growthPaused) { this.growthPaused = growthPaused; }
   public boolean isDead() { return dead; }
   public void setDead(boolean dead) { this.dead = dead; }
   public String getDisplayName() { return this.displayName; }
   public BehaviorMode getMode() { return this.mode; }
   public Set<UUID> getFriendlyPlayers() { return new HashSet<>(this.friendlyPlayers); }
   public void setOwnerUUID(UUID ownerUUID) { this.ownerUUID = ownerUUID; }
   public void setEntityType(EntityType entityType) { this.entityType = entityType; }
   public void setDisplayName(String displayName) { this.displayName = displayName; }
   public void setMode(BehaviorMode mode) { this.mode = mode; }
   public void setFriendlyPlayers(Set<UUID> friendlyPlayers) { this.friendlyPlayers = friendlyPlayers != null ? new HashSet<>(friendlyPlayers) : new HashSet<>(); }
   public void addFriendlyPlayer(UUID playerUUID) { this.friendlyPlayers.add(playerUUID); }
   public void removeFriendlyPlayer(UUID playerUUID) { this.friendlyPlayers.remove(playerUUID); }
   public boolean isFriendlyPlayer(UUID playerUUID) { return this.ownerUUID.equals(playerUUID) || this.friendlyPlayers.contains(playerUUID); }
   public boolean isFavorite() { return this.favorite; }
   public void setFavorite(boolean favorite) { this.favorite = favorite; }
}
