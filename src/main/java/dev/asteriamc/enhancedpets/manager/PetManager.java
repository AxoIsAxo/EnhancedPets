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
import org.bukkit.entity.Wolf;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Locale;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PetManager {
   private final Enhancedpets plugin;
   private final PetStorageManager storageManager;
   private final Map<UUID, PetData> petDataMap = new ConcurrentHashMap<>();
   private static final NamespacedKey PET_ID_KEY = new NamespacedKey("enhancedpets", "pet_id");
   private final Map<UUID, BukkitTask> pendingOwnerSaves = new ConcurrentHashMap<>();



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
      queueOwnerSave(data.getOwnerUUID());
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
      this.saveAllCachedData();
      return newPetsFound;
   }

   private static Integer asInt(Object o) {
      return (o instanceof Number) ? ((Number) o).intValue() : null;
   }
   private static Double asDouble(Object o) {
      return (o instanceof Number) ? ((Number) o).doubleValue() : null;
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

         String wolfVariant = WolfVariantUtil.getVariantName(w); // 1.21+ only
         if (wolfVariant != null) {
            metadata.put("wolfVariant", wolfVariant); // e.g. "ASHEN"
         }

         metadata.put("isAngry", w.isAngry());
      }
      else if (petEntity instanceof Cat c) {
         // Use API (stable 1.14+)
         metadata.put("catVariant", c.getCatType().name()); // e.g. "ALL_BLACK"
         metadata.put("isLyingDown", c.isLyingDown());
         metadata.put("collarColor", c.getCollarColor().name());
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
      plugin.getLogger().info("Captured metadata for dead pet: " + data.getDisplayName());
   }



   public void applyMetadata(Entity newPetEntity, PetData petData) {
      if (newPetEntity == null || petData == null) return;
      Map<String, Object> metadata = petData.getMetadata();
      if (metadata == null || metadata.isEmpty()) return;

      if (newPetEntity instanceof Ageable a && metadata.containsKey("isAdult")) {
         if (!(boolean) metadata.get("isAdult")) {
            a.setAge(((Number) metadata.get("age")).intValue());
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
            le.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(((Number) metadata.get("maxHealth")).doubleValue());
            le.setHealth(((Number) metadata.get("maxHealth")).doubleValue());
         }
      }

      if (newPetEntity instanceof AbstractHorse ah) {
         if (metadata.containsKey("jumpStrength")) {
            ah.setJumpStrength(((Number) metadata.get("jumpStrength")).doubleValue());
         }
         if (metadata.containsKey("movementSpeed")) {
            double speed = ((Number) metadata.get("movementSpeed")).doubleValue();
            if (speed > 0) {
               ah.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
            }
         }
         if (metadata.containsKey("domestication")) {
            ah.setDomestication(((Number) metadata.get("domestication")).intValue());
         }
         if (metadata.containsKey("maxDomestication")) {
            ah.setMaxDomestication(((Number) metadata.get("maxDomestication")).intValue());
         }


         if (metadata.containsKey("inventory")) {
            /*@SuppressWarnings("unchecked")
            List<Map<String, Object>> inventoryData = (List<Map<String, Object>>) metadata.get("inventory");
            for (Map<String, Object> itemData : inventoryData) {
               int slot = ((Number) itemData.get("slot")).intValue();
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
            l.setStrength(((Number) metadata.get("llamaStrength")).intValue());
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
            String stored = String.valueOf(metadata.get("wolfVariant"));
            // Do NOT set immediately — see timing fix in part B below.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
               boolean ok = WolfVariantUtil.setVariant(w, stored);
               if (!ok) {
                  plugin.getLogger().fine("Wolf variant not applied: " + stored);
               }
            }, 2L);
         }

         if (metadata.containsKey("isAngry")) {
            w.setAngry((Boolean) metadata.get("isAngry"));
         }
      }
      else if (newPetEntity instanceof Cat c) {
         if (metadata.containsKey("catVariant")) {
            String raw = String.valueOf(metadata.get("catVariant"));
            String norm = raw;
            int colon = norm.indexOf(':');
            if (colon != -1) norm = norm.substring(colon + 1);
            norm = norm.trim().toUpperCase(java.util.Locale.ROOT);
            try {
               c.setCatType(Cat.Type.valueOf(norm));
            } catch (IllegalArgumentException ex) {
               plugin.getLogger().warning("Unknown cat type: " + raw);
            }
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
                  int duration = ((Number) effectData.get("duration")).intValue();
                  int amplifier = ((Number) effectData.get("amplifier")).intValue();
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
         int loveTicks = ((Number) metadata.get("loveModeTicks")).intValue();
         if (loveTicks > 0) {
            animal.setLoveModeTicks(loveTicks);
         }
      }

      if (newPetEntity instanceof Sittable s && metadata.containsKey("isSitting")) {
         s.setSitting((Boolean) metadata.get("isSitting"));
      }

      plugin.getLogger().info("Applied metadata to revived pet: " + petData.getDisplayName());
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
      queueOwnerSave(newData.getOwnerUUID());
   }

   public void setGrowthPaused(UUID petId, boolean isPaused) {
      PetData petData = getPetData(petId);
      if (petData == null) return;
      petData.setGrowthPaused(isPaused);
      Entity entity = Bukkit.getEntity(petId);
      if (entity instanceof Ageable ageable) {
         if (isPaused) {
            petData.setPausedAgeTicks(ageable.getAge());
            ageable.setAge(Integer.MIN_VALUE);
         } else {
            ageable.setAge(petData.getPausedAgeTicks());
            petData.setPausedAgeTicks(-24000);
         }
      }
      queueOwnerSave(petData.getOwnerUUID());
   }

   public void resetPetAge(UUID petId) {
      PetData petData = getPetData(petId);
      if (petData == null || petData.isDead()) return; // <-- FIX: Don't run on dead pets

      Entity entity = Bukkit.getEntity(petId);
      if (!(entity instanceof Ageable ageable) || ageable.isAdult()) return;

      // Set age to default grow-up time
      ageable.setAge(-24000);
      petData.setPausedAgeTicks(-24000);

      queueOwnerSave(petData.getOwnerUUID());
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
      queueOwnerSave(data.getOwnerUUID());
   }


   public void unregisterPet(UUID petUUID) {
      PetData data = this.petDataMap.get(petUUID);
      if (data != null) {
         data.setDead(true);

         this.plugin.getLogger().info("Marking pet as dead: " + data.getDisplayName() + " (UUID: " + petUUID + ")");
      }
      queueOwnerSave(data.getOwnerUUID());
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

   public void queueOwnerSave(UUID ownerUUID) {
      if (ownerUUID == null) return;

      // Cancel any pending save for this owner and reschedule
      BukkitTask existing = pendingOwnerSaves.remove(ownerUUID);
      if (existing != null) existing.cancel();

      pendingOwnerSaves.put(ownerUUID, Bukkit.getScheduler().runTaskLaterAsynchronously(
              plugin,
              () -> {
                 pendingOwnerSaves.remove(ownerUUID);
                 List<PetData> pets = getPetsOwnedBy(ownerUUID);
                 storageManager.savePets(ownerUUID, pets);
              },
              40L // ~2 seconds debounce
      ));
   }

   public void updatePetData(PetData data) {
      if (data != null && this.petDataMap.containsKey(data.getPetUUID())) {
         this.petDataMap.put(data.getPetUUID(), data);
         queueOwnerSave(data.getOwnerUUID());
      }
   }

   public void saveAllPetData(List<PetData> petDataList) {
      if (petDataList != null) {
         petDataList.forEach(this::updatePetData); // queueOwnerSave is called in updatePetData
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
      queueOwnerSave(data.getOwnerUUID());
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

         queueOwnerSave(ownerUUID);
         return data;
      }


   }

   private static final class WolfVariantUtil {
      private static final Method GET_VARIANT;
      private static final Method SET_VARIANT;
      private static final Class<?> VARIANT_CLASS;
      private static final Object WOLF_VARIANT_REGISTRY; // This will be a Registry<Wolf.Variant> on 1.21+
      private static final Method REGISTRY_GET_METHOD;

      static {
         Method get = null, set = null;
         Class<?> variantClass = null;
         Object registry = null;
         Method registryGet = null;

         try {
            // Available on 1.21+ (and some 1.20.x snapshots)
            Class<Wolf> wolfClass = Wolf.class;
            get = wolfClass.getMethod("getVariant");
            variantClass = get.getReturnType(); // This is org.bukkit.entity.Wolf$Variant
            set = wolfClass.getMethod("setVariant", variantClass);

            // Get the server's registry for Wolf.Variant
            Method getRegistry = Bukkit.class.getMethod("getRegistry", Class.class);
            registry = getRegistry.invoke(null, variantClass);

            // Get the 'get' method from the Registry interface
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            registryGet = registryClass.getMethod("get", NamespacedKey.class);

         } catch (Exception ignored) {
            // This will fail gracefully on older MC versions where variants don't exist
         }
         GET_VARIANT = get;
         SET_VARIANT = set;
         VARIANT_CLASS = variantClass;
         WOLF_VARIANT_REGISTRY = registry;
         REGISTRY_GET_METHOD = registryGet;
      }

      static String getVariantName(Wolf wolf) {
         if (GET_VARIANT == null) return null;
         try {
            // wolf.getVariant()
            Object variant = GET_VARIANT.invoke(wolf);
            if (variant == null) return null;

            // variant.getKey().toString()
            Method getKey = variant.getClass().getMethod("getKey");
            Object namespacedKey = getKey.invoke(variant);

            return namespacedKey.toString(); // e.g., "minecraft:ashen"
         } catch (Exception e) {
            return null;
         }
      }

      static boolean setVariant(Wolf wolf, String nameOrKey) {
         if (SET_VARIANT == null || WOLF_VARIANT_REGISTRY == null || nameOrKey == null) return false;
         try {
            // Create a NamespacedKey from the saved string
            NamespacedKey key = NamespacedKey.fromString(nameOrKey.toLowerCase(Locale.ROOT));
            if (key == null) return false;

            // Use the registry to get the Variant object from the key
            // Object variant = Registry.WOLF_VARIANT.get(key);
            Object variantToSet = REGISTRY_GET_METHOD.invoke(WOLF_VARIANT_REGISTRY, key);
            if (variantToSet == null) return false;

            // wolf.setVariant(variant)
            SET_VARIANT.invoke(wolf, variantToSet);
            return true;
         } catch (Exception e) {
            return false;
         }
      }
   }


}