
package dev.asteriamc.enhancedpets.manager;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.PetData;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LlamaInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.command.CommandSender.Spigot;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import net.kyori.adventure.text.Component;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PetManager {
   private final Enhancedpets plugin;
   private final PetStorageManager storageManager;
   private final Map<UUID, PetData> petDataMap = new ConcurrentHashMap<>();
   private static final NamespacedKey PET_ID_KEY = new NamespacedKey("enhancedpets", "pet_id");

   public PetManager(Enhancedpets plugin) {
      this.plugin = plugin;
      this.storageManager = plugin.getStorageManager();
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
      if (!pet.isValid() || pet.getOwnerUniqueId() == null) {
         this.plugin.getLogger().log(Level.WARNING, "Attempted to register an invalid or ownerless pet: {0}", pet.getUniqueId());
         return null;
      }

      UUID petUUID = pet.getUniqueId();
      if (this.petDataMap.containsKey(petUUID)) {
         return this.petDataMap.get(petUUID);
      }

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
      return data;
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
      if (newPetsFound > 0) {
         
         List<PetData> petsToSave = getPetsOwnedBy(ownerUUID);
         storageManager.savePets(ownerUUID, petsToSave);
      }
      return newPetsFound;
   }

   public void captureMetadata(PetData data, LivingEntity petEntity) {
      if (data == null || petEntity == null) return;
      Map<String, Object> metadata = new HashMap<>();

      
      
      if (petEntity instanceof Ageable a) {
         metadata.put("age", a.getAge());
         metadata.put("isAdult", a.isAdult());
      }
      
      if (petEntity instanceof Sittable s) {
         metadata.put("isSitting", s.isSitting());
      }
      
      if (petEntity.getCustomName() != null) {
         metadata.put("customName", petEntity.getCustomName());
         metadata.put("customNameVisible", petEntity.isCustomNameVisible());
      }
      
      metadata.put("health", petEntity.getHealth());
      metadata.put("maxHealth", petEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());

      
      if (petEntity instanceof AbstractHorse ah) {
         metadata.put("jumpStrength", ah.getJumpStrength());
         metadata.put("movementSpeed", ah.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue());
         metadata.put("domestication", ah.getDomestication());
         metadata.put("maxDomestication", ah.getMaxDomestication());

         
         ItemStack[] inventory = ah.getInventory().getContents();
         List<Map<String, Object>> inventoryData = new ArrayList<>();
         for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] != null && !inventory[i].getType().isAir()) {
               Map<String, Object> itemData = new HashMap<>();
               itemData.put("slot", i);
               itemData.put("item", inventory[i].serialize());
               inventoryData.add(itemData);
            }
         }
         if (!inventoryData.isEmpty()) {
            metadata.put("inventory", inventoryData);
         }

         
         if (ah instanceof ChestedHorse ch) {
            metadata.put("hasChest", ch.isCarryingChest());
         }
      }

      
      
      if (petEntity instanceof Horse h) {
         metadata.put("horseColor", h.getColor().name());
         metadata.put("horseStyle", h.getStyle().name());
         
      }
      
      else if (petEntity instanceof Llama l) {
         metadata.put("llamaColor", l.getColor().name());
         metadata.put("llamaStrength", l.getStrength());
         
         LlamaInventory llamaInv = l.getInventory();
         ItemStack decor = llamaInv.getDecor();
         if (decor != null && !decor.getType().isAir()) {
            metadata.put("decoration", decor.serialize());
         }
      }
      
      else if (petEntity instanceof Wolf w) {
         metadata.put("collarColor", w.getCollarColor().name());
         
         try {
            String getVariantCmd = "data get entity " + w.getUniqueId() + " variant";
            CommandResult result = executeCommandAndGetResult(getVariantCmd);
            if (result.success && result.output != null && !result.output.trim().isEmpty()) {
               
               String output = result.output.trim();
               if (output.contains("minecraft:")) {
                  String variant = output.substring(output.indexOf("minecraft:"));
                  
                  variant = variant.split("\\s")[0].replace("\"", "");
                  metadata.put("wolfVariant", variant);
               }
            }
         } catch (Exception e) {
            plugin.getLogger().warning("Failed to capture wolf variant: " + e.getMessage());
         }
         metadata.put("isAngry", w.isAngry());
      }
      
      else if (petEntity instanceof Cat c) {
         
         try {
            String getCatTypeCmd = "data get entity " + c.getUniqueId() + " variant";
            CommandResult result = executeCommandAndGetResult(getCatTypeCmd);
            if (result.success && result.output != null && !result.output.trim().isEmpty()) {
               
               String output = result.output.trim();
               if (output.contains("minecraft:")) {
                  String variant = output.substring(output.indexOf("minecraft:"));
                  
                  variant = variant.split("\\s")[0].replace("\"", "");
                  metadata.put("catVariant", variant);
               }
            }
         } catch (Exception e) {
            plugin.getLogger().warning("Failed to capture cat variant: " + e.getMessage());
         }
         metadata.put("isLyingDown", c.isLyingDown());
      }
      
      else if (petEntity instanceof Parrot p) {
         metadata.put("parrotVariant", p.getVariant().name());
         
      }


      
      
      Collection<PotionEffect> effects = petEntity.getActivePotionEffects();
      if (!effects.isEmpty()) {
         List<Map<String, Object>> effectsData = new ArrayList<>();
         for (PotionEffect effect : effects) {
            Map<String, Object> effectData = new HashMap<>();
            effectData.put("type", effect.getType().getName());
            effectData.put("duration", effect.getDuration());
            effectData.put("amplifier", effect.getAmplifier());
            effectData.put("ambient", effect.isAmbient());
            effectData.put("particles", effect.hasParticles());
            effectData.put("icon", effect.hasIcon());
            effectsData.add(effectData);
         }
         metadata.put("potionEffects", effectsData);
      }

      
      if (petEntity instanceof Animals animal) {
         metadata.put("loveModeTicks", animal.getLoveModeTicks());
         metadata.put("breedCause", animal.getBreedCause() != null ? animal.getBreedCause().toString() : null);
      }

      data.setMetadata(metadata);
      plugin.getLogger().info("Captured comprehensive metadata for dead pet: " + data.getDisplayName());
   }


   
   public void applyMetadata(Entity newPetEntity, PetData petData) {
      if (newPetEntity == null || petData == null) return;
      Map<String, Object> metadata = petData.getMetadata();
      if (metadata == null || metadata.isEmpty()) return;

      
      
      if (newPetEntity instanceof Ageable a && metadata.containsKey("isAdult")) {
         if (!(boolean) metadata.get("isAdult")) {
            a.setAge((Integer) metadata.get("age"));
         }
      }
      
      if (metadata.containsKey("customName")) {
         newPetEntity.setCustomName((String) metadata.get("customName"));
         if (metadata.containsKey("customNameVisible")) {
            newPetEntity.setCustomNameVisible((Boolean) metadata.get("customNameVisible"));
         }
      }

      if (metadata.containsKey("maxHealth")) {
         if (newPetEntity instanceof LivingEntity le) {
            le.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue((Double) metadata.get("maxHealth"));
            le.setHealth((Double) metadata.get("maxHealth"));
         }
      }

      
      if (newPetEntity instanceof AbstractHorse ah) {
         if (metadata.containsKey("jumpStrength")) {
            ah.setJumpStrength((Double) metadata.get("jumpStrength"));
         }
         if (metadata.containsKey("movementSpeed")) {
            double speed = (Double) metadata.get("movementSpeed");
            if (speed > 0) {
               ah.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
            }
         }
         if (metadata.containsKey("domestication")) {
            ah.setDomestication((Integer) metadata.get("domestication"));
         }
         if (metadata.containsKey("maxDomestication")) {
            ah.setMaxDomestication((Integer) metadata.get("maxDomestication"));
         }

         
         if (metadata.containsKey("inventory")) {
            /*@SuppressWarnings("unchecked")
            List<Map<String, Object>> inventoryData = (List<Map<String, Object>>) metadata.get("inventory");
            for (Map<String, Object> itemData : inventoryData) {
               int slot = (Integer) itemData.get("slot");
               @SuppressWarnings("unchecked")
               Map<String, Object> itemMap = (Map<String, Object>) itemData.get("item");
               ItemStack item = ItemStack.deserialize(itemMap);
               ah.getInventory().setItem(slot, item);
            }*/
         }

         
         if (ah instanceof ChestedHorse ch && metadata.containsKey("hasChest")) {
            /*ch.setCarryingChest((Boolean) metadata.get("hasChest"));*/
         }
      }

      
      
      if (newPetEntity instanceof Horse h) {
         if (metadata.containsKey("horseColor")) {
            h.setColor(Horse.Color.valueOf((String) metadata.get("horseColor")));
         }
         if (metadata.containsKey("horseStyle")) {
            h.setStyle(Horse.Style.valueOf((String) metadata.get("horseStyle")));
         }
      }
      
      else if (newPetEntity instanceof Llama l) {
         if (metadata.containsKey("llamaColor")) {
            l.setColor(Llama.Color.valueOf((String) metadata.get("llamaColor")));
         }
         if (metadata.containsKey("llamaStrength")) {
            l.setStrength((Integer) metadata.get("llamaStrength"));
         }
         if (metadata.containsKey("decoration")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> decorMap = (Map<String, Object>) metadata.get("decoration");
            ItemStack decoration = ItemStack.deserialize(decorMap);
            l.getInventory().setDecor(decoration);
         }
      }
      
      else if (newPetEntity instanceof Wolf w) {
         if (metadata.containsKey("collarColor")) {
            w.setCollarColor(DyeColor.valueOf((String) metadata.get("collarColor")));
         }
         
         if (metadata.containsKey("wolfVariant")) {
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
               try {
                  String variantKey = (String) metadata.get("wolfVariant");
                  String command = "data merge entity " + w.getUniqueId() + " {variant:\"" + variantKey + "\"}";
                  boolean success = executeCommand(command);
                  if (!success) {
                     plugin.getLogger().warning("Failed to set wolf variant to: " + variantKey);
                  }
               } catch (Exception e) {
                  plugin.getLogger().warning("Error setting wolf variant: " + e.getMessage());
               }
            }, 1L);
         }
         if (metadata.containsKey("isAngry")) {
            w.setAngry((Boolean) metadata.get("isAngry"));
         }
      }
      
      else if (newPetEntity instanceof Cat c) {
         
         if (metadata.containsKey("catVariant")) {
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
               try {
                  String variantKey = (String) metadata.get("catVariant");
                  String command = "data merge entity " + c.getUniqueId() + " {variant:\"" + variantKey + "\"}";
                  boolean success = executeCommand(command);
                  if (!success) {
                     plugin.getLogger().warning("Failed to set cat variant to: " + variantKey);
                  }
               } catch (Exception e) {
                  plugin.getLogger().warning("Error setting cat variant: " + e.getMessage());
               }
            }, 1L);
         }
         if (metadata.containsKey("collarColor")) {
            c.setCollarColor(DyeColor.valueOf((String) metadata.get("collarColor")));
         }
         if (metadata.containsKey("isLyingDown")) {
            c.setLyingDown((Boolean) metadata.get("isLyingDown"));
         }
      }
      
      else if (newPetEntity instanceof Parrot p) {
         if (metadata.containsKey("parrotVariant")) {
            p.setVariant(Parrot.Variant.valueOf((String) metadata.get("parrotVariant")));
         }
      }


      
      
      if (metadata.containsKey("potionEffects") && newPetEntity instanceof LivingEntity le) {
         /*@SuppressWarnings("unchecked")
         List<Map<String, Object>> effectsData = (List<Map<String, Object>>) metadata.get("potionEffects");
         for (Map<String, Object> effectData : effectsData) {
            try {
               PotionEffectType type = PotionEffectType.getByName((String) effectData.get("type"));
               if (type != null) {
                  int duration = (Integer) effectData.get("duration");
                  int amplifier = (Integer) effectData.get("amplifier");
                  boolean ambient = (Boolean) effectData.get("ambient");
                  boolean particles = (Boolean) effectData.get("particles");
                  boolean icon = (Boolean) effectData.get("icon");

                  PotionEffect effect = new PotionEffect(type, duration, amplifier, ambient, particles, icon);
                  le.addPotionEffect(effect);
               }
            } catch (Exception e) {
               plugin.getLogger().warning("Failed to apply potion effect: " + e.getMessage());
            }
         }*/
      }

      
      if (newPetEntity instanceof Animals animal && metadata.containsKey("loveModeTicks")) {
         int loveTicks = (Integer) metadata.get("loveModeTicks");
         if (loveTicks > 0) {
            animal.setLoveModeTicks(loveTicks);
         }
      }

      
      if (newPetEntity instanceof Sittable s && metadata.containsKey("isSitting")) {
         s.setSitting((Boolean) metadata.get("isSitting"));
      }

      plugin.getLogger().info("Applied comprehensive metadata to revived pet: " + petData.getDisplayName());
   }

   
   private static class CommandResult {
      boolean success;
      String output;

      CommandResult(boolean success, String output) {
         this.success = success;
         this.output = output;
      }
   }

   
   private CommandResult executeCommandAndGetResult(String command) {
      try {
         
         CommandOutputCapture outputCapture = new CommandOutputCapture();
         boolean success = Bukkit.dispatchCommand(outputCapture, command);
         return new CommandResult(success, outputCapture.getOutput());
      } catch (Exception e) {
         return new CommandResult(false, null);
      }
   }

   
   private boolean executeCommand(String command) {
      try {
         return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
      } catch (Exception e) {
         return false;
      }
   }

   
   private static class CommandOutputCapture implements CommandSender {
      private StringBuilder output = new StringBuilder();

      @Override
      public void sendMessage(String message) {
         output.append(message).append("\n");
      }

      @Override
      public void sendMessage(String[] messages) {
         for (String message : messages) {
            sendMessage(message);
         }
      }

      public String getOutput() {
         return output.toString();
      }

      
      @Override
      public String getName() { return "VariantCapture"; }

      @Override
      public boolean isOp() { return true; }

      @Override
      public void setOp(boolean value) {}

      @Override
      public boolean hasPermission(String name) { return true; }

      @Override
      public boolean hasPermission(org.bukkit.permissions.Permission perm) { return true; }

      @Override
      public boolean isPermissionSet(String name) { return true; }

      @Override
      public boolean isPermissionSet(org.bukkit.permissions.Permission perm) { return true; }

      @Override
      public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin) {
         return null;
      }

      @Override
      public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, String name, boolean value) {
         return null;
      }

      @Override
      public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, int ticks) {
         return null;
      }

      @Override
      public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, String name, boolean value, int ticks) {
         return null;
      }

      @Override
      public void removeAttachment(org.bukkit.permissions.PermissionAttachment attachment) {}

      @Override
      public void recalculatePermissions() {}

      @Override
      public Set<org.bukkit.permissions.PermissionAttachmentInfo> getEffectivePermissions() {
         return new HashSet<>();
      }

      @Override
      public org.bukkit.Server getServer() { return Bukkit.getServer(); }

      @Override
      public void sendMessage(UUID sender, String message) { sendMessage(message); }

      @Override
      public void sendMessage(UUID sender, String[] messages) { sendMessage(messages); }

      @Override
      public CommandSender.Spigot spigot() { return new CommandSender.Spigot(); }

      @Override
      public net.kyori.adventure.text.Component name() {
         return net.kyori.adventure.text.Component.text("VariantCapture");
      }
   }

   
   public void revivePet(PetData oldData, LivingEntity newPetEntity) {
      if (oldData == null || newPetEntity == null) return;

      
      this.petDataMap.remove(oldData.getPetUUID());

      
      PetData newData = new PetData(newPetEntity.getUniqueId(), oldData.getOwnerUUID(), oldData.getEntityType(), oldData.getDisplayName());

      
      newData.setMode(oldData.getMode());
      newData.setFriendlyPlayers(oldData.getFriendlyPlayers());
      newData.setFavorite(oldData.isFavorite());
      newData.setGrowthPaused(oldData.isGrowthPaused());
      

      
      applyMetadata(newPetEntity, oldData);

      
      this.petDataMap.put(newData.getPetUUID(), newData);

      
      newPetEntity.setCustomName(ChatColor.translateAlternateColorCodes('&', newData.getDisplayName()));
      if(newPetEntity instanceof Tameable t) {
         Player owner = Bukkit.getPlayer(newData.getOwnerUUID());
         if (owner != null) {
            t.setOwner(owner);
            t.setTamed(true);
         }
      }
   }

   public void setGrowthPaused(UUID petUUID, boolean paused) {
      PetData data = getPetData(petUUID);
      if (data == null) return;
      data.setGrowthPaused(paused);
      Entity e = Bukkit.getEntity(petUUID);
      if (e instanceof Ageable a) {
         a.setAge(paused ? Integer.MIN_VALUE : -24000);
      }
   }

   public void unregisterPet(LivingEntity petEntity) {
      if (petEntity == null) return;
      UUID petUUID = petEntity.getUniqueId();
      PetData data = this.petDataMap.get(petUUID);
      if (data != null) {
         data.setDead(true);
         captureMetadata(data, petEntity); 
         this.plugin.getLogger().info("Marking pet as dead: " + data.getDisplayName() + " (UUID: " + petUUID + ")");
      }
   }

   
   public void unregisterPet(UUID petUUID) {
      PetData data = this.petDataMap.get(petUUID);
      if (data != null) {
         data.setDead(true);
         
         this.plugin.getLogger().info("Marking pet as dead: " + data.getDisplayName() + " (UUID: " + petUUID + ")");
      }
   }

   public void freePetCompletely(UUID petUUID) {
      PetData removedData = this.petDataMap.remove(petUUID); 

      if (removedData != null) {
         
         Bukkit.getScheduler().runTask(plugin, () -> {
            Entity entity = Bukkit.getEntity(petUUID);
            if (entity instanceof Tameable t && t.isTamed()) {
               t.setOwner(null);
               t.setTamed(false);
               String name = removedData.getDisplayName();
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
         });

         plugin.getLogger().info("Completely removing pet: " + removedData.getDisplayName() + " (UUID: " + petUUID + ")");

         
         
         unloadPetsForPlayer(removedData.getOwnerUUID());
         loadPetsForPlayer(removedData.getOwnerUUID()); 
         
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
      int id = this.storageManager.getNextPetId();
      String typeName = this.formatEntityType(type);
      this.storageManager.incrementAndSaveNextPetId();
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
      }
   }

   public void saveAllPetData(List<PetData> petDataList) {
      if (petDataList != null) {
         petDataList.forEach(this::updatePetData);
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

   public void saveAllCachedData() {
      Map<UUID, List<PetData>> petsByOwner = this.petDataMap.values().stream()
              .collect(Collectors.groupingBy(PetData::getOwnerUUID));

      petsByOwner.forEach(storageManager::savePets);
      plugin.getLogger().info("Saved all cached pet data for " + petsByOwner.size() + " players.");
   }

   public String assignNewDefaultName(PetData petData) {
      return assignNewDefaultName(petData.getEntityType());
   }

   public void loadPetsForPlayer(UUID ownerUUID) {
      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
         List<PetData> loadedPets = storageManager.loadPets(ownerUUID);
         Bukkit.getScheduler().runTask(plugin, () -> {
            loadedPets.forEach(pet -> petDataMap.put(pet.getPetUUID(), pet));
            plugin.getLogger().info("Loaded " + loadedPets.size() + " pets for " + ownerUUID);
         });
      });
   }

   public void unloadPetsForPlayer(UUID ownerUUID) {
      List<PetData> petsToSave = getPetsOwnedBy(ownerUUID);
      if (!petsToSave.isEmpty()) {
         storageManager.savePets(ownerUUID, petsToSave);
      }
      petsToSave.forEach(pet -> petDataMap.remove(pet.getPetUUID()));
      plugin.getLogger().info("Unloaded " + petsToSave.size() + " pets for " + ownerUUID);
   }

   public PetData registerNonTameablePet(Entity entity, UUID ownerUUID, String displayName) {
      if (entity == null || !entity.isValid() || ownerUUID == null) {
         this.plugin.getLogger().log(Level.WARNING, "Attempted to register an invalid non-tameable pet entity: {0}", entity != null ? entity.getUniqueId() : "null");
         return null;
      }
      UUID petUUID = entity.getUniqueId();
      if (this.petDataMap.containsKey(petUUID)) {
         return this.petDataMap.get(petUUID);
      } else {
         String finalName = displayName != null && !displayName.isEmpty() ? ChatColor.stripColor(displayName) : this.assignNewDefaultName(entity.getType());
         PetData data = new PetData(petUUID, ownerUUID, entity.getType(), finalName);
         this.petDataMap.put(petUUID, data);
         String ownerName = Bukkit.getOfflinePlayer(ownerUUID).getName();
         this.plugin.getLogger().info("Registered new non-tameable pet: " + finalName + " (Owner: " + ownerName + ")");
         return data;
      }
   }
}