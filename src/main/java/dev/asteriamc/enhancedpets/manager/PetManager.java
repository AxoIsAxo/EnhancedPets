package dev.asteriamc.enhancedpets.manager;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.PetData;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

public class PetManager {
   private final Enhancedpets plugin;
   private final Map<UUID, PetData> petDataMap = new HashMap<>();
   private static final NamespacedKey PET_ID_KEY = new NamespacedKey("enhancedpets", "pet_id");

   public PetManager(Enhancedpets plugin) {
      this.plugin = plugin;
   }

   public PetData getPetData(UUID petUUID) {
      return this.petDataMap.get(petUUID);
   }

   public boolean isManagedPet(UUID uuid) {
      return this.petDataMap.containsKey(uuid);
   }

   public Collection<PetData> getAllPetData() {
      return this.petDataMap.values();
   }

   public PetData registerPet(Tameable pet) {
      if (!pet.isValid()) {
         this.plugin.getLogger().log(Level.WARNING, "Attempted to register an invalid pet entity: {0}", pet.getUniqueId());
         return null;
      } else {
         if (pet.getOwnerUniqueId() == null) {
            return null;
         }

         UUID petUUID = pet.getUniqueId();
         if (this.petDataMap.containsKey(petUUID)) {
            PetData existingData = this.petDataMap.get(petUUID);
            if (!Objects.equals(existingData.getOwnerUUID(), pet.getOwnerUniqueId())) {
               existingData.setOwnerUUID(pet.getOwnerUniqueId());
               this.savePetData();
            }
            return existingData;
         } else {
            // Check for stored pet ID
            int id = -1;
            if (pet.getPersistentDataContainer().has(PET_ID_KEY, PersistentDataType.INTEGER)) {
               id = pet.getPersistentDataContainer().get(PET_ID_KEY, PersistentDataType.INTEGER);
               pet.getPersistentDataContainer().remove(PET_ID_KEY);
            }
            String customName = pet.getCustomName();
            String finalName;
            if (customName != null && !customName.isEmpty() && !customName.equalsIgnoreCase(pet.getType().name().replace('_', ' '))) {
               finalName = ChatColor.stripColor(customName);
            } else if (id != -1) {
               finalName = this.formatEntityType(pet.getType()) + " #" + id;
            } else {
               finalName = this.assignNewDefaultName(pet.getType());
            }

            PetData data = new PetData(petUUID, pet.getOwnerUniqueId(), pet.getType(), finalName);

            this.petDataMap.put(petUUID, data);
            String ownerName = Bukkit.getOfflinePlayer(pet.getOwnerUniqueId()).getName();
            this.plugin.getLogger().info("Registered new pet: " + finalName + " (Owner: " + ownerName + ")");
            this.savePetData();
            return data;
         }
      }
   }

   public int scanAndRegisterPetsForOwner(Player owner) {
      int newPetsFound = 0;
      UUID ownerUUID = owner.getUniqueId();

      
      for (World world : Bukkit.getWorlds()) {
         for (Chunk chunk : world.getLoadedChunks()) {
            for (Entity entity : chunk.getEntities()) {
               
               if (entity instanceof Tameable pet) {
                  
                  if (pet.isTamed() && ownerUUID.equals(pet.getOwnerUniqueId()) && !isManagedPet(pet.getUniqueId())) {
                     registerPet(pet); 
                     newPetsFound++;
                  }
               }
            }
         }
      }
      return newPetsFound;
   }

   public void setGrowthPaused(UUID petUUID, boolean paused) {
      PetData data = getPetData(petUUID);
      if (data == null) return;

      data.setGrowthPaused(paused);
      updatePetData(data);

      Entity e = Bukkit.getEntity(petUUID);
      if (e instanceof Ageable a) {
         if (paused) {
            
            a.setAge(Integer.MIN_VALUE);   
         } else {
            a.setAge(-24000);                   
         }
      }
   }

   public void unregisterPet(UUID petUUID) {
      PetData data = this.petDataMap.get(petUUID);
      if (data != null) {
         data.setDead(true);
         this.updatePetData(data);
         this.plugin.getLogger().info("Marking pet as dead: " + data.getDisplayName() + " (UUID: " + petUUID + ")");
      }
   }

   // Actually remove pet data (used for freeing)
   public void freePetCompletely(UUID petUUID) {
      Entity entity = Bukkit.getEntity(petUUID);
      if (entity instanceof Tameable t && t.isTamed()) {
         t.setOwner(null);
         t.setTamed(false);
         // Store pet ID in persistent data
         PetData data = this.petDataMap.get(petUUID);
         if (data != null) {
            String name = data.getDisplayName();
            int id = -1;
            int idx = name.lastIndexOf('#');
            if (idx != -1) {
               try {
                  id = Integer.parseInt(name.substring(idx + 1).trim());
               } catch (NumberFormatException ignored) {}
            }
            if (id != -1) {
               t.getPersistentDataContainer().set(PET_ID_KEY, PersistentDataType.INTEGER, id);
            }
         }
      }
      PetData removedData = this.petDataMap.remove(petUUID);
      if (removedData != null) {
         this.plugin.getLogger().info("Completely removing pet: " + removedData.getDisplayName() + " (UUID: " + petUUID + ")");
         this.savePetData();
      }
   }

   public List<PetData> getPetsOwnedBy(UUID ownerUUID) {
      return this.petDataMap
              .values()
              .stream()
              .filter(data -> ownerUUID.equals(data.getOwnerUUID()))
              .collect(Collectors.toList());
   }

   public String assignNewDefaultName(EntityType type) {
      int id = this.plugin.getConfigManager().getNextPetId();
      String typeName = this.formatEntityType(type);
      this.plugin.getConfigManager().incrementAndSaveNextPetId();
      return typeName + " #" + id;
   }

   private String formatEntityType(EntityType type) {
      String name = type.name().toLowerCase().replace('_', ' ');
      String[] words = name.split(" ");
      StringBuilder formatted = new StringBuilder();
      for (String word : words) {
         if (!word.isEmpty()) {
            formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
         }
      }
      return formatted.toString().trim();
   }

   public void updatePetData(PetData data) {
      if (data != null && this.petDataMap.containsKey(data.getPetUUID())) {
         this.petDataMap.put(data.getPetUUID(), data);
         this.savePetData();
      }
   }

   public void saveAllPetData(List<PetData> petDataList) {
      if (petDataList != null) {
         petDataList.forEach(data -> {
            if(data != null && this.petDataMap.containsKey(data.getPetUUID())) {
               this.petDataMap.put(data.getPetUUID(), data);
            }
         });
         this.savePetData();
      }
   }

   public void freePet(UUID petUUID) {
      PetData data = this.getPetData(petUUID);
      if (data != null) {
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (Bukkit.getEntity(petUUID) instanceof Tameable pet && pet.isValid() && pet.isTamed()) {
               pet.setOwner(null);
               pet.setTamed(false);
            }
         });
         this.unregisterPet(petUUID);
      }
   }

   public synchronized void savePetData() {
      FileConfiguration config = this.plugin.getConfigManager().getConfig();
      if (config == null) {
         this.plugin.getLogger().severe("Config not loaded! Cannot save pet data.");
      } else {
         Map<String, Object> petsToSave = new HashMap<>();
         for (Entry<UUID, PetData> entry : this.petDataMap.entrySet()) {
            petsToSave.put(entry.getKey().toString(), entry.getValue().serialize());
         }
         config.set("pet-data", petsToSave);
         this.plugin.saveConfig();
      }
   }

   public String assignNewDefaultName(PetData petData) {
      return assignNewDefaultName(petData.getEntityType());
   }

   public synchronized void loadPetData() {
      this.petDataMap.clear();
      FileConfiguration config = this.plugin.getConfigManager().getConfig();
      if (config == null) {
         this.plugin.getLogger().severe("Config not loaded! Cannot load pet data.");
      } else {
         ConfigurationSection petSection = config.getConfigurationSection("pet-data");
         if (petSection == null) {
            this.plugin.getLogger().info("No 'pet-data' section found in config.yml. No pets loaded.");
         } else {
            int count = 0;
            int errors = 0;
            for (String key : petSection.getKeys(false)) {
               try {
                  UUID petUUID = UUID.fromString(key);
                  ConfigurationSection petMapDataSection = petSection.getConfigurationSection(key);
                  if (petMapDataSection != null) {
                     PetData data = PetData.deserialize(petUUID, petMapDataSection.getValues(false));
                     if (data != null) {
                        this.petDataMap.put(petUUID, data);
                        count++;
                     } else { errors++; }
                  } else { errors++; }
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Error loading pet data for key '" + key + "'. Skipping. Error: " + e.getMessage());
                  errors++;
               }
            }
            this.plugin.getLogger().info("Loaded data for " + count + " pets" + (errors > 0 ? " with " + errors + " errors." : "."));
         }
      }
   }

   // Register a non-Tameable pet (e.g., Happy Ghast)
   public PetData registerNonTameablePet(Entity entity, UUID ownerUUID, String displayName) {
      if (entity == null || !entity.isValid() || ownerUUID == null) {
         this.plugin.getLogger().log(Level.WARNING, "Attempted to register an invalid non-tameable pet entity: {0}", entity != null ? entity.getUniqueId() : "null");
         return null;
      }
      UUID petUUID = entity.getUniqueId();
      if (this.petDataMap.containsKey(petUUID)) {
         PetData existingData = this.petDataMap.get(petUUID);
         if (!Objects.equals(existingData.getOwnerUUID(), ownerUUID)) {
            existingData.setOwnerUUID(ownerUUID);
            this.savePetData();
         }
         return existingData;
      } else {
         String finalName = displayName != null && !displayName.isEmpty() ? ChatColor.stripColor(displayName) : this.assignNewDefaultName(entity.getType());
         PetData data = new PetData(petUUID, ownerUUID, entity.getType(), finalName);
         this.petDataMap.put(petUUID, data);
         String ownerName = Bukkit.getOfflinePlayer(ownerUUID).getName();
         this.plugin.getLogger().info("Registered new non-tameable pet: " + finalName + " (Owner: " + ownerName + ")");
         this.savePetData();
         return data;
      }
   }
}