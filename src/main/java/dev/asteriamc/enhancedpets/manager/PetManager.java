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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PetManager {
    private static final NamespacedKey PET_ID_KEY = new NamespacedKey("enhancedpets", "pet_id");
    private final Enhancedpets plugin;
    private final PetStorageManager storageManager;
    private final Map<UUID, PetData> petDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingOwnerSaves = new ConcurrentHashMap<>();

    private final java.util.Set<UUID> loadedOwners = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // FIX: Track owners currently loading to prevent race conditions
    private final java.util.Set<UUID> loadingOwners = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public PetManager(Enhancedpets plugin) {
        this.plugin = plugin;
        this.storageManager = plugin.getStorageManager();
    }

    private static Integer asInt(Object o) {
        return (o instanceof Number) ? ((Number) o).intValue() : null;
    }

    private static Double asDouble(Object o) {
        return (o instanceof Number) ? ((Number) o).doubleValue() : null;
    }

    private void cancelPendingOwnerSave(UUID ownerUUID) {
        if (ownerUUID == null)
            return;
        BukkitTask existing = pendingOwnerSaves.remove(ownerUUID);
        if (existing != null && !existing.isCancelled()) {
            existing.cancel();
        }
    }

    public void cancelAllPendingOwnerSaves() {
        for (BukkitTask task : pendingOwnerSaves.values()) {
            if (task != null && !task.isCancelled())
                task.cancel();
        }
        pendingOwnerSaves.clear();
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
            this.plugin.getLogger().log(Level.WARNING, "Attempted to register an invalid or ownerless pet: {0}",
                    pet.getUniqueId());
            return null;
        }

        UUID petUUID = pet.getUniqueId();
        UUID ownerUUID = pet.getOwnerUniqueId();

        if (this.petDataMap.containsKey(petUUID)) {
            return this.petDataMap.get(petUUID);
        }

        // 1. Ghost Busting: Check specifically for "Dead" pets that match this live one
        // (Name & Type)
        // This handles cases where UUIDs changed (e.g. old versions) but it's logically
        // the same pet.
        List<PetData> ownerPets = getPetsOwnedBy(ownerUUID);
        String liveName = null;
        if (pet.getCustomName() != null) {
            liveName = ChatColor.stripColor(pet.getCustomName());
        }
        for (PetData ex : ownerPets) {
            // Must be dead to be a candidate for resurrection
            if (!ex.isDead())
                continue;
            // Must be same type
            if (ex.getEntityType() != pet.getType())
                continue;

            // Name Matching
            String exName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', ex.getDisplayName()));
            boolean match = false;

            if (liveName != null) {
                // If live pet has a custom name, strict match
                if (liveName.equalsIgnoreCase(exName)) {
                    match = true;
                }
            } else {
                // If live pet has NO name, we rely only on the fact that the dead pet
                // also had a default-looking name? Or we skip to avoid false positives.
                // However, users migrating from old versions often have unnamed pets.
                // We'll skip for now to be safe against overwriting named dead pets with
                // generic ones.
                // UNLESS the dead pet name is also generic.
                // But parsing "Wolf #4" is hard.
                continue;
            }

            if (match) {
                plugin.getLogger()
                        .info("Found ghost pet record '" + ex.getDisplayName() + "' for live entity. Re-linking...");
                revivePet(ex, pet); // Updates UUID, clears Dead flag, copies data
                return getPetData(pet.getUniqueId());
            }
        }

        if (!isOwnerLoaded(ownerUUID)) {
            plugin.getLogger().fine(
                    "Registering a pet for an offline owner (" + ownerUUID + "). Loading data asynchronously...");

            // Create placeholder entry immediately to prevent duplicate registrations
            final UUID finalPetUUID = petUUID;
            final EntityType finalType = pet.getType();
            final String placeholderName = this.formatEntityType(pet.getType())
                    + plugin.getLanguageManager().getString("pet.loading_suffix");

            // Check if we already have this pet cached (from previous async load)
            if (this.petDataMap.containsKey(petUUID)) {
                return this.petDataMap.get(petUUID);
            }

            // Create temporary placeholder data
            PetData placeholder = new PetData(petUUID, ownerUUID, pet.getType(), placeholderName);
            this.petDataMap.put(petUUID, placeholder);

            // CRITICAL: Capture metadata NOW while the entity is still loaded
            // The async load might complete after the chunk unloads, losing variant data
            captureMetadata(placeholder, pet);

            // Load the real data asynchronously
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<PetData> offlinePets = storageManager.loadPets(ownerUUID);
                Optional<PetData> existingData = offlinePets.stream()
                        .filter(p -> p.getPetUUID().equals(finalPetUUID))
                        .findFirst();

                // Apply the loaded data on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (existingData.isPresent()) {
                        plugin.debugLog(
                                "Async: Loaded existing persistent data for offline owner's pet " + finalPetUUID);
                        PetData realData = existingData.get();
                        this.petDataMap.put(finalPetUUID, realData);
                    } else {
                        // No existing data - keep the placeholder but give it a proper name
                        // Metadata was already captured above, so we just update the name
                        PetData current = this.petDataMap.get(finalPetUUID);
                        if (current != null && current.getDisplayName()
                                .contains(plugin.getLanguageManager().getString("pet.loading_suffix"))) {
                            current.setDisplayName(assignNewDefaultName(finalType));
                            plugin.debugLog("Async: Created new pet data for offline owner's pet " + finalPetUUID);
                            queueOwnerSave(ownerUUID);
                        }
                    }
                });
            });

            return placeholder;
        }

        int id = -1;
        if (pet.getPersistentDataContainer().has(PET_ID_KEY, PersistentDataType.INTEGER)) {
            id = pet.getPersistentDataContainer().get(PET_ID_KEY, PersistentDataType.INTEGER);
            pet.getPersistentDataContainer().remove(PET_ID_KEY);
        }
        String customName = pet.getCustomName();
        String finalName;
        if (customName != null && !customName.isEmpty()
                && !customName.equalsIgnoreCase(pet.getType().name().replace('_', ' '))) {
            finalName = ChatColor.stripColor(customName);
        } else if (id != -1) {
            finalName = plugin.getLanguageManager().getStringReplacements("pet.default_name_format", "type",
                    this.formatEntityType(pet.getType()), "id", String.valueOf(id));
        } else {
            finalName = this.assignNewDefaultName(pet.getType());
        }

        PetData data = new PetData(petUUID, ownerUUID, pet.getType(), finalName);
        this.petDataMap.put(petUUID, data);

        // CRITICAL: Capture metadata immediately on registration to preserve variant
        // data
        // (wolf variant, cat type, horse color, etc.) This ensures newborn pets have
        // their
        // inherited traits saved right away, not just when they die or are stored.
        captureMetadata(data, pet);

        String ownerName = Bukkit.getOfflinePlayer(ownerUUID).getName();
        this.plugin.debugLog("Registered new pet: " + finalName + " (Owner: " + ownerName + ")");
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
                        if (pet.isTamed() && ownerUUID.equals(pet.getOwnerUniqueId())) {
                            // Case 1: Already managed, but might be marked dead erroneously
                            if (isManagedPet(pet.getUniqueId())) {
                                PetData existing = getPetData(pet.getUniqueId());
                                if (existing != null && existing.isDead()) {
                                    existing.setDead(false);
                                    plugin.debugLog("Corrected state for " + existing.getDisplayName()
                                            + ": marked DEAD but found ALIVE.");
                                    queueOwnerSave(ownerUUID);
                                }
                                continue;
                            }

                            // Case 2: Not managed. Register it.
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

    public void captureMetadata(PetData data, LivingEntity petEntity) {
        if (data == null || petEntity == null)
            return;
        Map<String, Object> metadata = new HashMap<>();

        if (petEntity instanceof Ageable a) {
            if (data.isGrowthPaused() && !a.isAdult()) {
                metadata.put("age", data.getPausedAgeTicks());
            } else {
                metadata.put("age", a.getAge());
            }
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

            metadata.put("maxDomestication", ah.getMaxDomestication());

            // Inventory is now handled by Base64 Full State Capture
            // We only keep simple visual traits here for the GUI icons.

            if (ah instanceof ChestedHorse ch && ch.isCarryingChest()) {
                metadata.put("hasChest", true);
            }
        }

        if (petEntity instanceof Horse h) {
            metadata.put("horseColor", h.getColor().name());
            metadata.put("horseStyle", h.getStyle().name());
        } else if (petEntity instanceof Llama l) {
            metadata.put("llamaColor", l.getColor().name());
            metadata.put("llamaStrength", l.getStrength());

            LlamaInventory llamaInv = l.getInventory();
            ItemStack decor = llamaInv.getDecor();
            if (decor != null && !decor.getType().isAir()) {
                metadata.put("decoration", decor.serialize());
            }
        } else if (petEntity instanceof Wolf w) {
            metadata.put("collarColor", w.getCollarColor().name());

            String wolfVariant = WolfVariantUtil.getVariantName(w);
            if (wolfVariant != null) {
                metadata.put("wolfVariant", wolfVariant);
            }

            metadata.put("isAngry", w.isAngry());
        } else if (petEntity instanceof Cat c) {

            metadata.put("catVariant", c.getCatType().name());
            metadata.put("isLyingDown", c.isLyingDown());
            metadata.put("collarColor", c.getCollarColor().name());
        } else if (petEntity instanceof Parrot p) {
            metadata.put("parrotVariant", p.getVariant().name());
        } else if (petEntity instanceof Axolotl ax) {
            metadata.put("axolotlVariant", ax.getVariant().name());
        } else if (petEntity instanceof Rabbit r) {
            metadata.put("rabbitType", r.getRabbitType().name());
        } else if (petEntity instanceof Sheep s) {
            if (s.getColor() != null)
                metadata.put("sheepColor", s.getColor().name());
        } else if (petEntity instanceof MushroomCow m) {
            metadata.put("moocowVariant", m.getVariant().name());
        } else if (petEntity instanceof Panda p) {
            metadata.put("pandaMainGene", p.getMainGene().name());
            metadata.put("pandaHiddenGene", p.getHiddenGene().name());
        } else if (petEntity instanceof Fox f) {
            metadata.put("foxType", f.getFoxType().name());
            metadata.put("isCrouching", f.isCrouching());
            metadata.put("isSleeping", f.isSleeping());
        } else if (petEntity instanceof TropicalFish t) {
            metadata.put("tropPattern", t.getPattern().name());
            metadata.put("tropBodyColor", t.getBodyColor().name());
            metadata.put("tropPatternColor", t.getPatternColor().name());
        }

        // Potion Effects are now handled by Base64 Full State Capture

        if (petEntity instanceof Animals animal) {
            metadata.put("loveModeTicks", animal.getLoveModeTicks());
            metadata.put("breedCause", animal.getBreedCause() != null ? animal.getBreedCause().toString() : null);
        }

        data.setMetadata(metadata);

        // NEW: Full State Capture
        String fullState = captureFullEntityState(petEntity);
        if (fullState != null) {
            data.setBase64EntityState(fullState);
        }

        plugin.debugLog("Captured metadata for dead pet: " + data.getDisplayName());
    }

    public void applyMetadata(Entity newPetEntity, PetData petData) {
        if (newPetEntity == null || petData == null)
            return;

        // NEW: Try Base64 Application first
        if (petData.getBase64EntityState() != null && newPetEntity instanceof LivingEntity le) {
            boolean success = applyFullEntityState(le, petData.getBase64EntityState());
            if (success) {
                // We still might want to ensure Key metadata (like custom name) matches PetData
                // wrapper if different
                // But Base64 should hold the truth.
                plugin.debugLog("Applied Full Base64 State to " + petData.getDisplayName());
                return;
            }
        }

        // FALLBACK: Legacy Metadata Application
        Map<String, Object> metadata = petData.getMetadata();
        if (metadata == null || metadata.isEmpty())
            return;

        if (newPetEntity instanceof Ageable a && metadata.containsKey("isAdult")) {
            Object isAdultObj = metadata.get("isAdult");
            if (isAdultObj instanceof Boolean b && !b) {
                Integer age = asInt(metadata.get("age"));
                if (age != null)
                    a.setAge(age);
            }
        }

        if (metadata.containsKey("customName")) {
            newPetEntity.setCustomName((String) metadata.get("customName"));
            if (metadata.containsKey("customNameVisible")) {
                Object v = metadata.get("customNameVisible");
                if (v instanceof Boolean bv)
                    newPetEntity.setCustomNameVisible(bv);
            }
        }

        if (metadata.containsKey("maxHealth") && newPetEntity instanceof LivingEntity le) {
            Double max = asDouble(metadata.get("maxHealth"));
            if (max != null && max > 0) {
                if (le.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                    le.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(max);
                }
                le.setHealth(Math.min(max, le.getHealth()));
            }
        }

        if (newPetEntity instanceof AbstractHorse ah) {
            Double jump = asDouble(metadata.get("jumpStrength"));
            if (jump != null)
                ah.setJumpStrength(jump);

            Double speed = asDouble(metadata.get("movementSpeed"));
            if (speed != null && speed > 0 && ah.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                ah.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
            }

            Integer dom = asInt(metadata.get("domestication"));
            if (dom != null)
                ah.setDomestication(dom);

            Integer maxDom = asInt(metadata.get("maxDomestication"));
            if (maxDom != null)
                ah.setMaxDomestication(maxDom);

            // Chest restoration for Donkey/Mule/Llama
            if (ah instanceof ChestedHorse ch && metadata.containsKey("hasChest")) {
                Object v = metadata.get("hasChest");
                if (v instanceof Boolean bv)
                    ch.setCarryingChest(bv);
            }

            // Inventory restoration
            if (metadata.containsKey("inventory")) {
                Object invObj = metadata.get("inventory");
                if (invObj instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) o;
                            Integer slot = asInt(map.get("slot"));
                            if (slot != null) {
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> itemMap = (Map<String, Object>) map.get("item");
                                    ItemStack item = ItemStack.deserialize(itemMap);
                                    ah.getInventory().setItem(slot, item);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }
            }
        }

        if (newPetEntity instanceof Horse h) {
            if (metadata.containsKey("horseColor")) {
                try {
                    h.setColor(Horse.Color.valueOf((String) metadata.get("horseColor")));
                } catch (Exception ignored) {
                }
            }
            if (metadata.containsKey("horseStyle")) {
                try {
                    h.setStyle(Horse.Style.valueOf((String) metadata.get("horseStyle")));
                } catch (Exception ignored) {
                }
            }
        } else if (newPetEntity instanceof Llama l) {
            if (metadata.containsKey("llamaColor")) {
                try {
                    l.setColor(Llama.Color.valueOf((String) metadata.get("llamaColor")));
                } catch (Exception ignored) {
                }
            }
            Integer strength = asInt(metadata.get("llamaStrength"));
            if (strength != null)
                l.setStrength(strength);

            if (metadata.containsKey("decoration")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> decorMap = (Map<String, Object>) metadata.get("decoration");
                    ItemStack decoration = ItemStack.deserialize(decorMap);
                    l.getInventory().setDecor(decoration);
                } catch (Exception ignored) {
                }
            }
        } else if (newPetEntity instanceof Wolf w) {
            if (metadata.containsKey("collarColor")) {
                try {
                    w.setCollarColor(DyeColor.valueOf((String) metadata.get("collarColor")));
                } catch (Exception ignored) {
                }
            }

            if (metadata.containsKey("wolfVariant")) {
                String stored = String.valueOf(metadata.get("wolfVariant"));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    boolean ok = WolfVariantUtil.setVariant(w, stored);
                    if (!ok) {
                        plugin.getLogger().fine("Wolf variant not applied: " + stored);
                    }
                }, 2L);
            }

            if (metadata.containsKey("isAngry")) {
                Object v = metadata.get("isAngry");
                if (v instanceof Boolean bv)
                    w.setAngry(bv);
            }
        } else if (newPetEntity instanceof Cat c) {
            if (metadata.containsKey("catVariant")) {
                String raw = String.valueOf(metadata.get("catVariant"));
                String norm = raw;
                int colon = norm.indexOf(':');
                if (colon != -1)
                    norm = norm.substring(colon + 1);
                norm = norm.trim().toUpperCase(java.util.Locale.ROOT);
                try {
                    c.setCatType(Cat.Type.valueOf(norm));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Unknown cat type: " + raw);
                }
            }
            if (metadata.containsKey("collarColor")) {
                try {
                    c.setCollarColor(DyeColor.valueOf((String) metadata.get("collarColor")));
                } catch (Exception ignored) {
                }
            }
            if (metadata.containsKey("isLyingDown")) {
                Object v = metadata.get("isLyingDown");
                if (v instanceof Boolean bv)
                    c.setLyingDown(bv);
            }
        } else if (newPetEntity instanceof Parrot p) {
            if (metadata.containsKey("parrotVariant")) {
                try {
                    p.setVariant(Parrot.Variant.valueOf((String) metadata.get("parrotVariant")));
                } catch (Exception ignored) {
                }
            }
        } else if (newPetEntity instanceof Axolotl ax) {
            if (metadata.containsKey("axolotlVariant")) {
                try {
                    ax.setVariant(Axolotl.Variant.valueOf((String) metadata.get("axolotlVariant")));
                } catch (Exception ignored) {
                }
            }
        } else if (newPetEntity instanceof Rabbit r) {
            if (metadata.containsKey("rabbitType")) {
                try {
                    r.setRabbitType(Rabbit.Type.valueOf((String) metadata.get("rabbitType")));
                } catch (Exception ignored) {
                }
            }
        } else if (newPetEntity instanceof Sheep sh) {
            if (metadata.containsKey("sheepColor")) {
                try {
                    sh.setColor(DyeColor.valueOf((String) metadata.get("sheepColor")));
                } catch (Exception ignored) {
                }
            }
        } else if (newPetEntity instanceof MushroomCow m) {
            if (metadata.containsKey("moocowVariant")) {
                try {
                    m.setVariant(MushroomCow.Variant.valueOf((String) metadata.get("moocowVariant")));
                } catch (Exception ignored) {
                }
            }
        } else if (newPetEntity instanceof Panda xp) {
            if (metadata.containsKey("pandaMainGene")) {
                try {
                    xp.setMainGene(Panda.Gene.valueOf((String) metadata.get("pandaMainGene")));
                } catch (Exception ignored) {
                }
            }
            if (metadata.containsKey("pandaHiddenGene")) {
                try {
                    xp.setHiddenGene(Panda.Gene.valueOf((String) metadata.get("pandaHiddenGene")));
                } catch (Exception ignored) {
                }
            }
        } else if (newPetEntity instanceof Fox fx) {
            if (metadata.containsKey("foxType")) {
                try {
                    fx.setFoxType(Fox.Type.valueOf((String) metadata.get("foxType")));
                } catch (Exception ignored) {
                }
            }
            if (metadata.containsKey("isCrouching")) {
                Object v = metadata.get("isCrouching");
                if (v instanceof Boolean bv)
                    fx.setCrouching(bv);
            }
            if (metadata.containsKey("isSleeping")) {
                Object v = metadata.get("isSleeping");
                if (v instanceof Boolean bv)
                    fx.setSleeping(bv);
            }
        } else if (newPetEntity instanceof TropicalFish t) {
            if (metadata.containsKey("tropPattern")) {
                try {
                    t.setPattern(TropicalFish.Pattern.valueOf((String) metadata.get("tropPattern")));
                } catch (Exception ignored) {
                }
            }
            if (metadata.containsKey("tropBodyColor")) {
                try {
                    t.setBodyColor(DyeColor.valueOf((String) metadata.get("tropBodyColor")));
                } catch (Exception ignored) {
                }
            }
            if (metadata.containsKey("tropPatternColor")) {
                try {
                    t.setPatternColor(DyeColor.valueOf((String) metadata.get("tropPatternColor")));
                } catch (Exception ignored) {
                }
            }
        }

        if (newPetEntity instanceof Animals animal && metadata.containsKey("loveModeTicks")) {
            Integer loveTicks = asInt(metadata.get("loveModeTicks"));
            if (loveTicks != null && loveTicks > 0) {
                animal.setLoveModeTicks(loveTicks);
            }
        }

        if (newPetEntity instanceof Sittable s && metadata.containsKey("isSitting")) {
            Object v = metadata.get("isSitting");
            if (v instanceof Boolean bv)
                s.setSitting(bv);
        }

        plugin.debugLog("Applied comprehensive metadata to revived pet: " + petData.getDisplayName());
    }

    /*
     * if (metadata.containsKey("inventory")) {
     * removed stuff that may not ever be used. kept to shits and giggles
     * 
     * @SuppressWarnings("unchecked")
     * List<Map<String, Object>> inventoryData = (List<Map<String, Object>>)
     * metadata.get("inventory");
     * for (Map<String, Object> itemData : inventoryData) {
     * int slot = ((Number) itemData.get("slot")).intValue();
     * 
     * @SuppressWarnings("unchecked")
     * Map<String, Object> itemMap = (Map<String, Object>) itemData.get("item");
     * ItemStack item = ItemStack.deserialize(itemMap);
     * ah.getInventory().setItem(slot, item);
     * }
     * }
     * 
     * 
     * if (ah instanceof ChestedHorse ch && metadata.containsKey("hasChest")) {
     * ch.setCarryingChest((Boolean) metadata.get("hasChest"));
     * }
     * if (metadata.containsKey("potionEffects") && newPetEntity instanceof
     * LivingEntity le) {
     * 
     * @SuppressWarnings("unchecked")
     * List<Map<String, Object>> effectsData = (List<Map<String, Object>>)
     * metadata.get("potionEffects");
     * for (Map<String, Object> effectData : effectsData) {
     * try {
     * PotionEffectType type = PotionEffectType.getByName((String)
     * effectData.get("type"));
     * if (type != null) {
     * int duration = ((Number) effectData.get("duration")).intValue();
     * int amplifier = ((Number) effectData.get("amplifier")).intValue();
     * boolean ambient = (Boolean) effectData.get("ambient");
     * boolean particles = (Boolean) effectData.get("particles");
     * boolean icon = (Boolean) effectData.get("icon");
     * 
     * PotionEffect effect = new PotionEffect(type, duration, amplifier, ambient,
     * particles, icon);
     * le.addPotionEffect(effect);
     * }
     * } catch (Exception e) {
     * plugin.getLogger().warning("Failed to apply potion effect: " +
     * e.getMessage());
     * }
     * }
     * }
     */

    public PetData updatePetId(PetData oldData, UUID newId) {
        if (oldData == null || newId == null)
            return null;

        // Remove old mapping
        this.petDataMap.remove(oldData.getPetUUID());

        // Create new data with new UUID
        PetData newData = new PetData(newId, oldData.getOwnerUUID(), oldData.getEntityType(),
                oldData.getDisplayName());

        // Copy all properties
        newData.setMode(oldData.getMode());
        newData.setFriendlyPlayers(oldData.getFriendlyPlayers());
        newData.setFavorite(oldData.isFavorite());
        newData.setGrowthPaused(oldData.isGrowthPaused());
        newData.setPausedAgeTicks(oldData.getPausedAgeTicks());
        newData.setProtectedFromPlayers(oldData.isProtectedFromPlayers());
        newData.setDisplayColor(oldData.getDisplayColor());
        newData.setCustomIconMaterial(oldData.getCustomIconMaterial());
        newData.setMetadata(oldData.getMetadata());

        // Copy Station Data
        newData.setStationLocation(oldData.getStationLocation());
        newData.setStationRadius(oldData.getStationRadius());
        newData.setStationTargetTypes(oldData.getStationTargetTypes());

        // Copy Target Data
        newData.setExplicitTargetUUID(oldData.getExplicitTargetUUID());
        newData.setAggressiveTargetTypes(new HashSet<>(oldData.getAggressiveTargetTypes()));

        // Copy Storage Status (though usually this clears on withdraw, but we copy
        // state faithfully here)
        newData.setStored(oldData.isStored());
        newData.setDead(false); // Revived pets are alive by definition

        // Register new mapping
        this.petDataMap.put(newData.getPetUUID(), newData);

        // Queue save
        queueOwnerSave(newData.getOwnerUUID());

        return newData;
    }

    public void revivePet(PetData oldData, LivingEntity newPetEntity) {
        if (oldData == null || newPetEntity == null)
            return;

        PetData newData = updatePetId(oldData, newPetEntity.getUniqueId());

        applyMetadata(newPetEntity, newData);

        newPetEntity.setCustomName(ChatColor.translateAlternateColorCodes('&', newData.getDisplayName()));
        if (newPetEntity instanceof Tameable t) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(newData.getOwnerUUID());
            t.setOwner(owner);
            t.setTamed(true);
        }

        // Visuals
        newPetEntity.getWorld().spawnParticle(Particle.TOTEM, newPetEntity.getLocation().add(0, 1, 0), 20, 0.5, 0.5,
                0.5, 0.1);
        newPetEntity.getWorld().playSound(newPetEntity.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        // Save is queued in updatePetId
    }

    public void setGrowthPaused(UUID petId, boolean isPaused) {
        PetData petData = getPetData(petId);
        if (petData == null)
            return;

        Entity entity = Bukkit.getEntity(petId);
        if (entity instanceof Ageable ageable && !ageable.isAdult()) {
            petData.setGrowthPaused(isPaused);

            if (isPaused) {
                petData.setPausedAgeTicks(ageable.getAge());
                ageable.setAge(Integer.MIN_VALUE);
            } else {
                int savedAge = petData.getPausedAgeTicks();
                if (savedAge >= 0) {
                    savedAge = -24000;
                }
                ageable.setAge(savedAge);
                petData.setPausedAgeTicks(0);
            }
            queueOwnerSave(petData.getOwnerUUID());
        }
    }

    public void resetPetAge(UUID petId) {
        PetData petData = getPetData(petId);
        if (petData == null || petData.isDead())
            return;

        Entity entity = Bukkit.getEntity(petId);
        if (!(entity instanceof Ageable ageable) || ageable.isAdult())
            return;

        ageable.setAge(-24000);
        petData.setPausedAgeTicks(-24000);

        queueOwnerSave(petData.getOwnerUUID());
    }

    public void unregisterPet(LivingEntity petEntity) {
        if (petEntity == null)
            return;
        UUID petUUID = petEntity.getUniqueId();
        PetData data = this.petDataMap.get(petUUID);
        if (data != null) {
            data.setDead(true);
            captureMetadata(data, petEntity);
            this.plugin.debugLog("Marking pet as dead: " + data.getDisplayName() + " (UUID: " + petUUID + ")");
            queueOwnerSave(data.getOwnerUUID());
        }
    }

    public void unregisterPet(UUID petUUID) {
        PetData data = this.petDataMap.get(petUUID);
        if (data != null) {
            data.setDead(true);
            this.plugin.debugLog("Marking pet as dead: " + data.getDisplayName() + " (UUID: " + petUUID + ")");
            queueOwnerSave(data.getOwnerUUID());
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
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (id != -1) {
                        t.getPersistentDataContainer().set(PET_ID_KEY, PersistentDataType.INTEGER, id);
                    }
                }

            });

            plugin.debugLog("Completely removing pet: " + removedData.getDisplayName() + " (UUID: " + petUUID + ")");

            queueOwnerSave(removedData.getOwnerUUID());
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
        return plugin.getLanguageManager().getStringReplacements("pet.default_name_format", "type", typeName, "id",
                String.valueOf(id));
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

    public boolean isOwnerLoaded(UUID ownerUUID) {
        return loadedOwners.contains(ownerUUID);
    }

    /**
     * Checks if the owner's pet data is currently being loaded from disk.
     * Use this in conjunction with isOwnerLoaded to prevent race conditions.
     * 
     * @return true if the owner's data is in the process of loading
     */
    public boolean isOwnerLoading(UUID ownerUUID) {
        return loadingOwners.contains(ownerUUID);
    }

    private void mergeSaveOwner(UUID ownerUUID) {

        List<PetData> current = getPetsOwnedBy(ownerUUID);

        if (current.isEmpty()) {
            plugin.getLogger().fine("mergeSaveOwner: no in-memory pets for offline owner " + ownerUUID
                    + " — skipping to avoid clobbering.");
            return;
        }

        List<PetData> existing = storageManager.loadPets(ownerUUID);
        java.util.Map<UUID, PetData> merged = new java.util.LinkedHashMap<>();

        for (PetData p : existing)
            merged.put(p.getPetUUID(), p);

        for (PetData p : current)
            merged.put(p.getPetUUID(), p);

        storageManager.savePets(ownerUUID, new java.util.ArrayList<>(merged.values()));
        plugin.getLogger().fine("mergeSaveOwner: merged (" + current.size() + " in-memory) into (" + existing.size()
                + " existing) for owner " + ownerUUID);
    }

    public void queueOwnerSave(UUID ownerUUID) {
        if (ownerUUID == null)
            return;

        if (!plugin.isEnabled()) {
            List<PetData> pets = getPetsOwnedBy(ownerUUID);
            storageManager.savePets(ownerUUID, pets);
            return;
        }

        BukkitTask existing = pendingOwnerSaves.remove(ownerUUID);
        if (existing != null)
            existing.cancel();

        pendingOwnerSaves.put(ownerUUID, Bukkit.getScheduler().runTaskLaterAsynchronously(
                plugin,
                () -> {
                    pendingOwnerSaves.remove(ownerUUID);
                    if (isOwnerLoaded(ownerUUID)) {

                        List<PetData> pets = getPetsOwnedBy(ownerUUID);
                        storageManager.savePets(ownerUUID, pets);
                    } else {

                        mergeSaveOwner(ownerUUID);
                    }
                },
                40L));
    }

    public void updatePetData(PetData data) {
        if (data != null && this.petDataMap.containsKey(data.getPetUUID())) {
            this.petDataMap.put(data.getPetUUID(), data);
            queueOwnerSave(data.getOwnerUUID());
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
            queueOwnerSave(data.getOwnerUUID());
        }
    }

    public void saveAllCachedDataImmediate() {
        Map<UUID, List<PetData>> petsByOwner = this.petDataMap.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(PetData::getOwnerUUID));

        petsByOwner.forEach((owner, list) -> {
            if (isOwnerLoaded(owner)) {
                storageManager.savePets(owner, list);
            } else {

                List<PetData> existing = storageManager.loadPets(owner);
                java.util.Map<UUID, PetData> merged = new java.util.LinkedHashMap<>();

                for (PetData p : existing)
                    merged.put(p.getPetUUID(), p);

                for (PetData p : list)
                    merged.put(p.getPetUUID(), p);
                storageManager.savePets(owner, new java.util.ArrayList<>(merged.values()));
            }
        });
        plugin.debugLog("Saved all cached pet data (merge-safe) for " + petsByOwner.size() + " players (immediate).");
    }

    public void saveAllCachedData() {
        if (!plugin.isEnabled()) {
            saveAllCachedDataImmediate();
            return;
        }
        java.util.Set<UUID> owners = petDataMap.values().stream()
                .map(PetData::getOwnerUUID)
                .collect(java.util.stream.Collectors.toSet());
        owners.forEach(this::queueOwnerSave);
    }

    public String assignNewDefaultName(PetData petData) {
        return assignNewDefaultName(petData.getEntityType());
    }

    public void loadPetsForPlayer(UUID ownerUUID) {
        cancelPendingOwnerSave(ownerUUID);

        // FIX: Mark as loading BEFORE async task, not after
        // This prevents race conditions where GUI opens during load
        loadingOwners.add(ownerUUID);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PetData> loadedPets = storageManager.loadPets(ownerUUID);
            Bukkit.getScheduler().runTask(plugin, () -> {
                loadedPets.forEach(pet -> petDataMap.put(pet.getPetUUID(), pet));
                loadingOwners.remove(ownerUUID); // Done loading
                loadedOwners.add(ownerUUID); // Now fully loaded
                plugin.debugLog("Loaded " + loadedPets.size() + " pets for " + ownerUUID);
            });
        });
    }

    public void unloadPetsForPlayer(UUID ownerUUID) {
        cancelPendingOwnerSave(ownerUUID);

        // FIX: If still loading, wait for it to complete before unloading
        // to prevent data corruption
        loadingOwners.remove(ownerUUID);

        List<PetData> petsToSave = getPetsOwnedBy(ownerUUID);

        // FIX: Only save if we have data to save (prevents overwriting with empty)
        if (!petsToSave.isEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storageManager.savePets(ownerUUID, petsToSave));
        }

        petsToSave.forEach(pet -> petDataMap.remove(pet.getPetUUID()));
        loadedOwners.remove(ownerUUID);
        plugin.debugLog("Unloaded " + petsToSave.size() + " pets for " + ownerUUID);
    }

    /**
     * Evicts cached pet data for an offline owner.
     * This is called when an offline owner's last pet is unloaded from a chunk.
     * Does NOT save data since offline pets shouldn't have uncommitted changes.
     */
    public void evictOfflineOwnerData(UUID ownerUUID) {
        if (ownerUUID == null || isOwnerLoaded(ownerUUID)) {
            return; // Don't evict for online owners
        }

        List<PetData> offlinePets = getPetsOwnedBy(ownerUUID);
        offlinePets.forEach(pet -> petDataMap.remove(pet.getPetUUID()));
        plugin.debugLog("Evicted " + offlinePets.size() + " cached pets for offline owner " + ownerUUID);
    }

    public PetData registerNonTameablePet(Entity entity, UUID ownerUUID, String displayName) {
        if (entity == null || entity.isDead() || ownerUUID == null) {
            this.plugin.getLogger().log(Level.WARNING, "Attempted to register an invalid non-tameable pet entity: {0}",
                    entity != null ? entity.getUniqueId() : "null");
            return null;
        }
        UUID petUUID = entity.getUniqueId();
        if (this.petDataMap.containsKey(petUUID)) {
            return this.petDataMap.get(petUUID);
        } else {
            String finalName = displayName != null && !displayName.isEmpty() ? ChatColor.stripColor(displayName)
                    : this.assignNewDefaultName(entity.getType());
            PetData data = new PetData(petUUID, ownerUUID, entity.getType(), finalName);
            this.petDataMap.put(petUUID, data);

            // Capture metadata immediately for variant/state data
            if (entity instanceof LivingEntity le) {
                captureMetadata(data, le);

                // Enforce persistence for non-tameable pets
                le.setRemoveWhenFarAway(false);
            }
            // Do NOT forcefully name the entity. Let it remain vanilla unless renamed by
            // user.
            // entity.setCustomName(...);

            String ownerName = Bukkit.getOfflinePlayer(ownerUUID).getName();
            this.plugin.debugLog("Registered new non-tameable pet: " + finalName + " (Owner: " + ownerName + ")");

            queueOwnerSave(ownerUUID);
            return data;
        }
    }

    public void markPetDeadOffline(UUID ownerUUID, UUID petUUID, EntityType type, String displayName,
            Map<String, Object> metadata) {
        if (ownerUUID == null || petUUID == null)
            return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PetData> pets = storageManager.loadPets(ownerUUID);
            boolean found = false;
            for (PetData p : pets) {
                if (p.getPetUUID().equals(petUUID)) {
                    p.setDead(true);
                    if (metadata != null && !metadata.isEmpty()) {
                        p.setMetadata(new java.util.HashMap<>(metadata));
                    }
                    if (p.getEntityType() == null)
                        p.setEntityType(type);
                    if (p.getDisplayName() == null || p.getDisplayName().isEmpty()
                            || plugin.getLanguageManager().getString("pet.unknown_name")
                                    .equalsIgnoreCase(p.getDisplayName())) {
                        p.setDisplayName(displayName != null ? displayName
                                : plugin.getLanguageManager().getString("pet.unknown_name"));
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                PetData newData = new PetData(petUUID, ownerUUID, type,
                        displayName != null ? displayName : plugin.getLanguageManager().getString("pet.unknown_name"));
                newData.setDead(true);
                if (metadata != null && !metadata.isEmpty()) {
                    newData.setMetadata(new java.util.HashMap<>(metadata));
                }
                pets.add(newData);
            }
            storageManager.savePets(ownerUUID, pets);
        });
    }

    public void removePetRecord(UUID petUUID) {
        if (petUUID == null)
            return;
        PetData removed = this.petDataMap.remove(petUUID);
        UUID ownerUUID = removed != null ? removed.getOwnerUUID() : null;
        if (ownerUUID == null)
            return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PetData> pets = storageManager.loadPets(ownerUUID);
            pets.removeIf(p -> p.getPetUUID().equals(petUUID));
            storageManager.savePets(ownerUUID, pets);
        });
    }

    private String captureFullEntityState(LivingEntity entity) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos);

            Map<String, Object> state = new HashMap<>();

            // 1. Core
            state.put("health", entity.getHealth());
            state.put("remainingAir", entity.getRemainingAir());
            state.put("fallDistance", entity.getFallDistance());
            state.put("fireTicks", entity.getFireTicks());
            state.put("customName", entity.getCustomName());
            state.put("customNameVisible", entity.isCustomNameVisible());
            state.put("isGlowing", entity.isGlowing());
            state.put("isInvulnerable", entity.isInvulnerable());
            state.put("isSilent", entity.isSilent());
            state.put("hasAI", entity.hasAI());
            state.put("canPickupItems", entity.getCanPickupItems());
            state.put("collidable", entity.isCollidable());
            state.put("gliding", entity.isGliding());
            state.put("swimming", entity.isSwimming());

            // 2. Attributes
            Map<String, Double> attributes = new HashMap<>();
            for (Attribute attr : Attribute.values()) {
                org.bukkit.attribute.AttributeInstance instance = entity.getAttribute(attr);
                if (instance != null) {
                    attributes.put(attr.name(), instance.getBaseValue());
                }
            }
            state.put("attributes", attributes);

            // 3. Potion Effects
            state.put("potionEffects", new ArrayList<>(entity.getActivePotionEffects()));

            // 4. Equipment
            EntityEquipment eq = entity.getEquipment();
            if (eq != null) {
                Map<String, Object> equipment = new HashMap<>();
                if (eq.getHelmet() != null)
                    equipment.put("helmet", eq.getHelmet().serialize());
                if (eq.getChestplate() != null)
                    equipment.put("chestplate", eq.getChestplate().serialize());
                if (eq.getLeggings() != null)
                    equipment.put("leggings", eq.getLeggings().serialize());
                if (eq.getBoots() != null)
                    equipment.put("boots", eq.getBoots().serialize());
                if (eq.getItemInMainHand() != null)
                    equipment.put("mainHand", eq.getItemInMainHand().serialize());
                if (eq.getItemInOffHand() != null)
                    equipment.put("offHand", eq.getItemInOffHand().serialize());
                state.put("equipment", equipment);
            }

            // 5. Special Type Data
            Map<String, Object> typeData = new HashMap<>();
            if (entity instanceof Ageable a) {
                typeData.put("age", a.getAge());
                typeData.put("isAdult", a.isAdult());
                typeData.put("ageLock", a.getAgeLock());
            }
            if (entity instanceof Tameable t) {
                typeData.put("isTamed", t.isTamed());
                if (t.getOwner() != null)
                    typeData.put("ownerUUID", t.getOwner().getUniqueId().toString());
            }
            if (entity instanceof Sittable s) {
                typeData.put("isSitting", s.isSitting());
            }
            if (entity instanceof Steerable s) {
                typeData.put("hasSaddle", s.hasSaddle());
            }
            if (entity instanceof ChestedHorse ch) {
                typeData.put("hasChest", ch.isCarryingChest());
            }

            // Variants
            if (entity instanceof Cat c) {
                typeData.put("catType", c.getCatType().name());
                typeData.put("collarColor", c.getCollarColor().name());
            }
            if (entity instanceof Wolf w) {
                typeData.put("collarColor", w.getCollarColor().name());
                typeData.put("isAngry", w.isAngry());
                String v = WolfVariantUtil.getVariantName(w);
                if (v != null)
                    typeData.put("wolfVariant", v);
            }
            if (entity instanceof Parrot p) {
                typeData.put("parrotVariant", p.getVariant().name());
            }
            if (entity instanceof Axolotl a) {
                typeData.put("axolotlVariant", a.getVariant().name());
                typeData.put("isPlayingDead", a.isPlayingDead());
            }
            if (entity instanceof Rabbit r) {
                typeData.put("rabbitType", r.getRabbitType().name());
            }
            if (entity instanceof Llama l) {
                typeData.put("llamaColor", l.getColor().name());
                typeData.put("llamaStrength", l.getStrength());
                if (l.getInventory().getDecor() != null)
                    typeData.put("llamaDecor", l.getInventory().getDecor().serialize());
            }
            if (entity instanceof Horse h) {
                typeData.put("horseColor", h.getColor().name());
                typeData.put("horseStyle", h.getStyle().name());
            }
            if (entity instanceof MushroomCow mc) {
                typeData.put("mooshroomVariant", mc.getVariant().name());
            }
            if (entity instanceof Panda p) {
                typeData.put("pandaMain", p.getMainGene().name());
                typeData.put("pandaHidden", p.getHiddenGene().name());
            }
            if (entity instanceof Fox f) {
                typeData.put("foxType", f.getFoxType().name());
                typeData.put("isCrouching", f.isCrouching());
                typeData.put("isSleeping", f.isSleeping());
            }
            if (entity instanceof TropicalFish t) {
                typeData.put("tropPattern", t.getPattern().name());
                typeData.put("tropBodyColor", t.getBodyColor().name());
                typeData.put("tropPatternColor", t.getPatternColor().name());
            }
            if (entity instanceof AbstractHorse ah) {
                typeData.put("domestication", ah.getDomestication());
                typeData.put("maxDomestication", ah.getMaxDomestication());
                typeData.put("jumpStrength", ah.getJumpStrength());

                // Inventory
                List<Map<String, Object>> invItems = new ArrayList<>();
                ItemStack[] contents = ah.getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    if (contents[i] != null && !contents[i].getType().isAir()) {
                        Map<String, Object> slotMap = new HashMap<>();
                        slotMap.put("s", i);
                        slotMap.put("i", contents[i].serialize());
                        invItems.add(slotMap);
                    }
                }
                typeData.put("horseInventory", invItems);
            }

            state.put("typeData", typeData);

            boos.writeObject(state);
            boos.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("Error capturing entity state: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private boolean applyFullEntityState(LivingEntity entity, String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream bois = new BukkitObjectInputStream(bais);

            @SuppressWarnings("unchecked")
            Map<String, Object> state = (Map<String, Object>) bois.readObject();
            bois.close();

            // 1. Core
            if (state.containsKey("health"))
                entity.setHealth(Math.min((double) state.get("health"),
                        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
            if (state.containsKey("remainingAir"))
                entity.setRemainingAir((int) state.get("remainingAir"));
            if (state.containsKey("fallDistance"))
                entity.setFallDistance(((Number) state.get("fallDistance")).floatValue());
            if (state.containsKey("fireTicks"))
                entity.setFireTicks((int) state.get("fireTicks"));
            if (state.containsKey("customName"))
                entity.setCustomName((String) state.get("customName"));
            if (state.containsKey("customNameVisible"))
                entity.setCustomNameVisible((boolean) state.get("customNameVisible"));
            if (state.containsKey("isGlowing"))
                entity.setGlowing((boolean) state.get("isGlowing"));
            if (state.containsKey("isInvulnerable"))
                entity.setInvulnerable((boolean) state.get("isInvulnerable"));
            if (state.containsKey("isSilent"))
                entity.setSilent((boolean) state.get("isSilent"));
            if (state.containsKey("hasAI"))
                entity.setAI((boolean) state.get("hasAI"));
            if (state.containsKey("canPickupItems"))
                entity.setCanPickupItems((boolean) state.get("canPickupItems"));
            if (state.containsKey("collidable"))
                entity.setCollidable((boolean) state.get("collidable"));
            if (state.containsKey("gliding"))
                entity.setGliding((boolean) state.get("gliding"));
            if (state.containsKey("swimming"))
                entity.setSwimming((boolean) state.get("swimming"));

            // 2. Attributes
            if (state.containsKey("attributes")) {
                @SuppressWarnings("unchecked")
                Map<String, Double> attributes = (Map<String, Double>) state.get("attributes");
                for (Map.Entry<String, Double> entry : attributes.entrySet()) {
                    org.bukkit.attribute.AttributeInstance instance = entity
                            .getAttribute(Attribute.valueOf(entry.getKey()));
                    if (instance != null) {
                        instance.setBaseValue(entry.getValue());
                    }
                }
                // Re-apply health after attribute update (specifically Max Health)
                if (state.containsKey("health")) {
                    double max = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    entity.setHealth(Math.min((double) state.get("health"), max));
                }
            }

            // 3. Potion Effects
            // Clear existing first? Maybe not needed as spawn is fresh.
            if (state.containsKey("potionEffects")) {
                @SuppressWarnings("unchecked")
                List<PotionEffect> effects = (List<PotionEffect>) state.get("potionEffects");
                for (PotionEffect pe : effects) {
                    entity.addPotionEffect(pe);
                }
            }

            // 4. Equipment
            if (state.containsKey("equipment") && entity.getEquipment() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> eqMap = (Map<String, Object>) state.get("equipment");
                if (eqMap.containsKey("helmet"))
                    entity.getEquipment().setHelmet(ItemStack.deserialize((Map<String, Object>) eqMap.get("helmet")));
                if (eqMap.containsKey("chestplate"))
                    entity.getEquipment()
                            .setChestplate(ItemStack.deserialize((Map<String, Object>) eqMap.get("chestplate")));
                if (eqMap.containsKey("leggings"))
                    entity.getEquipment()
                            .setLeggings(ItemStack.deserialize((Map<String, Object>) eqMap.get("leggings")));
                if (eqMap.containsKey("boots"))
                    entity.getEquipment().setBoots(ItemStack.deserialize((Map<String, Object>) eqMap.get("boots")));
                if (eqMap.containsKey("mainHand"))
                    entity.getEquipment()
                            .setItemInMainHand(ItemStack.deserialize((Map<String, Object>) eqMap.get("mainHand")));
                if (eqMap.containsKey("offHand"))
                    entity.getEquipment()
                            .setItemInOffHand(ItemStack.deserialize((Map<String, Object>) eqMap.get("offHand")));
            }

            // 5. Type Data
            if (state.containsKey("typeData")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typeData = (Map<String, Object>) state.get("typeData");

                if (entity instanceof Ageable a) {
                    if (typeData.containsKey("age"))
                        a.setAge((int) typeData.get("age"));
                    if (typeData.containsKey("ageLock"))
                        a.setAgeLock((boolean) typeData.get("ageLock"));
                }
                if (entity instanceof Tameable t) {
                    if (typeData.containsKey("isTamed"))
                        t.setTamed((boolean) typeData.get("isTamed"));
                    // Owner is usually re-applied by PetManager logic, but we can try here if
                    // needed
                }
                if (entity instanceof Sittable s && typeData.containsKey("isSitting")) {
                    s.setSitting((boolean) typeData.get("isSitting"));
                }
                if (entity instanceof Steerable s && typeData.containsKey("hasSaddle")
                        && (boolean) typeData.get("hasSaddle")) {
                    // s.setSaddle(true); // Deprecated/Not universal? AbstractHorse uses Inventory
                }

                if (entity instanceof ChestedHorse ch && typeData.containsKey("hasChest")) {
                    ch.setCarryingChest((boolean) typeData.get("hasChest"));
                }

                if (entity instanceof Cat c) {
                    if (typeData.containsKey("catType"))
                        c.setCatType(Cat.Type.valueOf((String) typeData.get("catType")));
                    if (typeData.containsKey("collarColor"))
                        c.setCollarColor(DyeColor.valueOf((String) typeData.get("collarColor")));
                }
                if (entity instanceof Wolf w) {
                    if (typeData.containsKey("collarColor"))
                        w.setCollarColor(DyeColor.valueOf((String) typeData.get("collarColor")));
                    if (typeData.containsKey("isAngry"))
                        w.setAngry((boolean) typeData.get("isAngry"));

                    // "The Delay Trick": Apply variant 2 ticks later to prevent server overwrite on
                    // spawn
                    if (typeData.containsKey("wolfVariant")) {
                        String variant = (String) typeData.get("wolfVariant");
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            WolfVariantUtil.setVariant(w, variant);
                        }, 2L);
                    }
                }
                if (entity instanceof Parrot p && typeData.containsKey("parrotVariant")) {
                    p.setVariant(Parrot.Variant.valueOf((String) typeData.get("parrotVariant")));
                }
                if (entity instanceof Axolotl a) {
                    if (typeData.containsKey("axolotlVariant"))
                        a.setVariant(Axolotl.Variant.valueOf((String) typeData.get("axolotlVariant")));
                    if (typeData.containsKey("isPlayingDead"))
                        a.setPlayingDead((boolean) typeData.get("isPlayingDead"));
                }
                if (entity instanceof Rabbit r && typeData.containsKey("rabbitType")) {
                    r.setRabbitType(Rabbit.Type.valueOf((String) typeData.get("rabbitType")));
                }
                if (entity instanceof Llama l) {
                    if (typeData.containsKey("llamaColor"))
                        l.setColor(Llama.Color.valueOf((String) typeData.get("llamaColor")));
                    if (typeData.containsKey("llamaStrength"))
                        l.setStrength((int) typeData.get("llamaStrength"));
                    if (typeData.containsKey("llamaDecor"))
                        l.getInventory()
                                .setDecor(ItemStack.deserialize((Map<String, Object>) typeData.get("llamaDecor")));
                }
                if (entity instanceof Horse h) {
                    if (typeData.containsKey("horseColor"))
                        h.setColor(Horse.Color.valueOf((String) typeData.get("horseColor")));
                    if (typeData.containsKey("horseStyle"))
                        h.setStyle(Horse.Style.valueOf((String) typeData.get("horseStyle")));
                }
                if (entity instanceof MushroomCow mc && typeData.containsKey("mooshroomVariant")) {
                    mc.setVariant(MushroomCow.Variant.valueOf((String) typeData.get("mooshroomVariant")));
                }
                if (entity instanceof Panda p) {
                    if (typeData.containsKey("pandaMain"))
                        p.setMainGene(Panda.Gene.valueOf((String) typeData.get("pandaMain")));
                    if (typeData.containsKey("pandaHidden"))
                        p.setHiddenGene(Panda.Gene.valueOf((String) typeData.get("pandaHidden")));
                }
                if (entity instanceof Fox f) {
                    if (typeData.containsKey("foxType"))
                        f.setFoxType(Fox.Type.valueOf((String) typeData.get("foxType")));
                    if (typeData.containsKey("isCrouching"))
                        f.setCrouching((boolean) typeData.get("isCrouching"));
                    if (typeData.containsKey("isSleeping"))
                        f.setSleeping((boolean) typeData.get("isSleeping"));
                }
                if (entity instanceof TropicalFish t) {
                    if (typeData.containsKey("tropPattern"))
                        t.setPattern(TropicalFish.Pattern.valueOf((String) typeData.get("tropPattern")));
                    if (typeData.containsKey("tropBodyColor"))
                        t.setBodyColor(DyeColor.valueOf((String) typeData.get("tropBodyColor")));
                    if (typeData.containsKey("tropPatternColor"))
                        t.setPatternColor(DyeColor.valueOf((String) typeData.get("tropPatternColor")));
                }
                if (entity instanceof AbstractHorse ah) {
                    if (typeData.containsKey("domestication"))
                        ah.setDomestication((int) typeData.get("domestication"));
                    if (typeData.containsKey("maxDomestication"))
                        ah.setMaxDomestication((int) typeData.get("maxDomestication"));
                    if (typeData.containsKey("jumpStrength"))
                        ah.setJumpStrength((double) typeData.get("jumpStrength"));

                    if (typeData.containsKey("horseInventory")) {
                        List<Map<String, Object>> invItems = (List<Map<String, Object>>) typeData.get("horseInventory");
                        for (Map<String, Object> iMap : invItems) {
                            ah.getInventory().setItem((int) iMap.get("s"),
                                    ItemStack.deserialize((Map<String, Object>) iMap.get("i")));
                        }
                    }
                }
            }

            // 6. REVIVAL OVERRIDES (Reset bad states)
            // We want to restore attributes (like Max Health), but we want the pet to be
            // ALIVE.
            // So we force full health, extinguish fire, and reset air.
            if (entity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                entity.setHealth(entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            }
            entity.setFireTicks(0);
            entity.setRemainingAir(entity.getMaximumAir());
            entity.setFallDistance(0);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error restoring entity state from Base64: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static final class WolfVariantUtil {
        private static final Method GET_VARIANT;
        private static final Method SET_VARIANT;
        private static final Class<?> VARIANT_CLASS;
        private static final Object WOLF_VARIANT_REGISTRY;
        private static final Method REGISTRY_GET_METHOD;

        static {
            Method get = null, set = null;
            Class<?> variantClass = null;
            Object registry = null;
            Method registryGet = null;

            try {

                Class<Wolf> wolfClass = Wolf.class;
                get = wolfClass.getMethod("getVariant");
                variantClass = get.getReturnType();
                set = wolfClass.getMethod("setVariant", variantClass);

                Method getRegistry = Bukkit.class.getMethod("getRegistry", Class.class);
                registry = getRegistry.invoke(null, variantClass);

                Class<?> registryClass = Class.forName("org.bukkit.Registry");
                registryGet = registryClass.getMethod("get", NamespacedKey.class);

            } catch (Exception ignored) {

            }
            GET_VARIANT = get;
            SET_VARIANT = set;
            VARIANT_CLASS = variantClass;
            WOLF_VARIANT_REGISTRY = registry;
            REGISTRY_GET_METHOD = registryGet;
        }

        static String getVariantName(Wolf wolf) {
            if (GET_VARIANT == null)
                return null;
            try {

                Object variant = GET_VARIANT.invoke(wolf);
                if (variant == null)
                    return null;

                Method getKey = variant.getClass().getMethod("getKey");
                Object namespacedKey = getKey.invoke(variant);

                return namespacedKey.toString();
            } catch (Exception e) {
                return null;
            }
        }

        static boolean setVariant(Wolf wolf, String nameOrKey) {
            if (SET_VARIANT == null || WOLF_VARIANT_REGISTRY == null || nameOrKey == null)
                return false;
            try {

                NamespacedKey key = NamespacedKey.fromString(nameOrKey.toLowerCase(Locale.ROOT));
                if (key == null)
                    return false;

                Object variantToSet = REGISTRY_GET_METHOD.invoke(WOLF_VARIANT_REGISTRY, key);
                if (variantToSet == null)
                    return false;

                SET_VARIANT.invoke(wolf, variantToSet);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

}
