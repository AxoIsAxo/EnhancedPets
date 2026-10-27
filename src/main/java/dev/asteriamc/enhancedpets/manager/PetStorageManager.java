package dev.asteriamc.enhancedpets.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.PetData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PetStorageManager {

    private final Enhancedpets plugin;
    private final File playerDataFolder;
    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final java.util.concurrent.ConcurrentHashMap<UUID, Object> ioLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private FileConfiguration dataConfig;

    public PetStorageManager(Enhancedpets plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        setup();
    }

    private void setup() {
        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            plugin.getLogger().severe("Could not create player data folder: " + playerDataFolder.getAbsolutePath());
        }
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create data.yml!", e);
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (!dataConfig.contains("next-pet-id")) {
            dataConfig.set("next-pet-id", 1);
            saveDataConfig();
        }
    }

    public List<PetData> loadPets(UUID ownerUUID) {
        if (ownerUUID == null) {
            return new ArrayList<>();
        }
        File playerFile = new File(playerDataFolder, ownerUUID.toString() + ".json");
        if (!playerFile.exists()) {
            return new ArrayList<>();
        }

        Object lock = ioLocks.computeIfAbsent(ownerUUID, k -> new Object());
        synchronized (lock) {
            try {
                String json = Files.readString(playerFile.toPath(), StandardCharsets.UTF_8);
                Type listType = new TypeToken<ArrayList<HashMap<String, Object>>>() {
                }.getType();
                List<Map<String, Object>> serializedPets = gson.fromJson(json, listType);

                if (serializedPets == null) return new ArrayList<>();

                List<PetData> pets = new ArrayList<>();
                for (Map<String, Object> petMap : serializedPets) {
                    if (petMap == null) continue;
                    Object uuidValue = petMap.get("petUUID");
                    if (!(uuidValue instanceof String uuidString)) continue;
                    try {
                        UUID petUUID = UUID.fromString(uuidString);
                        PetData data = PetData.deserialize(petUUID, petMap);
                        if (data != null) {
                            pets.add(data);
                        }
                    } catch (IllegalArgumentException | ClassCastException ex) {
                        plugin.getLogger().warning("Skipping invalid pet record in " + playerFile.getName()
                                + ": " + ex.getMessage());
                    }
                }
                return pets;
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not load pet data for " + ownerUUID, ex);
                return new ArrayList<>();
            } catch (com.google.gson.JsonSyntaxException jse) {
                plugin.getLogger().log(Level.SEVERE, "Corrupt JSON for " + ownerUUID + " — leaving file intact.", jse);
                return new ArrayList<>();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not parse pet data for " + ownerUUID + " — leaving file intact.", ex);
                return new ArrayList<>();
            }
        }
    }

    public void savePets(UUID ownerUUID, List<PetData> pets) {
        if (ownerUUID == null) {
            return;
        }
        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            plugin.getLogger().severe("Could not create player data folder: " + playerDataFolder.getAbsolutePath());
            return;
        }
        File playerFile = new File(playerDataFolder, ownerUUID.toString() + ".json");


        List<PetData> snapshot = (pets == null) ? Collections.emptyList() : new ArrayList<>(pets);

        List<Map<String, Object>> serializedPets = snapshot.stream()
                .filter(Objects::nonNull)
                .map(petData -> {
                    Map<String, Object> map = petData.serialize();
                    map.put("petUUID", petData.getPetUUID().toString());
                    return map;
                })
                .collect(Collectors.toList());

        Object lock = ioLocks.computeIfAbsent(ownerUUID, k -> new Object());
        synchronized (lock) {
            File tempFile = new File(playerFile.getParentFile(), playerFile.getName() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(serializedPets, writer);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not write temp pet data for " + ownerUUID, e);
                return;
            }

            try {
                try {
                    Files.move(tempFile.toPath(), playerFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicUnsupported) {
                    Files.move(tempFile.toPath(), playerFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not replace pet data for " + ownerUUID, e);
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (IOException ignore) {
                }
            }
        }
    }

    /**
     * Atomically removes the given pet records from a player's storage file.
     * This is safe for offline players because it does not rely on the in-memory
     * cache.
     */
    public void removePetRecords(UUID ownerUUID, Collection<UUID> petUUIDs) {
        if (ownerUUID == null || petUUIDs == null || petUUIDs.isEmpty()) {
            return;
        }
        Object lock = ioLocks.computeIfAbsent(ownerUUID, k -> new Object());
        synchronized (lock) {
            List<PetData> pets = loadPets(ownerUUID);
            boolean changed = pets.removeIf(pet -> petUUIDs.contains(pet.getPetUUID()));
            if (changed) {
                savePets(ownerUUID, pets);
            }
        }
    }

    /**
     * Atomically replaces an old pet record with a new one. Used when a pet is
     * revived or withdrawn and receives a new entity UUID.
     */
    public void replacePetRecord(UUID ownerUUID, UUID oldPetUUID, PetData newPet) {
        if (ownerUUID == null || newPet == null) {
            return;
        }
        Object lock = ioLocks.computeIfAbsent(ownerUUID, k -> new Object());
        synchronized (lock) {
            List<PetData> pets = loadPets(ownerUUID);
            pets.removeIf(pet -> (oldPetUUID != null && pet.getPetUUID().equals(oldPetUUID))
                    || pet.getPetUUID().equals(newPet.getPetUUID()));
            pets.add(newPet);
            savePets(ownerUUID, pets);
        }
    }

    /**
     * Atomically merges in-memory pets into a player's storage file. Existing
     * records are kept unless the same pet UUID is present in {@code current}.
     */
    public void mergePets(UUID ownerUUID, Collection<PetData> current) {
        if (ownerUUID == null || current == null) {
            return;
        }
        Object lock = ioLocks.computeIfAbsent(ownerUUID, k -> new Object());
        synchronized (lock) {
            Map<UUID, PetData> merged = new LinkedHashMap<>();
            for (PetData pet : loadPets(ownerUUID)) {
                if (pet != null) {
                    merged.put(pet.getPetUUID(), pet);
                }
            }
            for (PetData pet : current) {
                if (pet != null) {
                    merged.put(pet.getPetUUID(), pet);
                }
            }
            savePets(ownerUUID, new ArrayList<>(merged.values()));
        }
    }

    /**
     * Atomically marks a pet as dead in storage, preserving its existing
     * favourite/mode/etc. fields if a record already exists.
     */
    public void markPetDead(UUID ownerUUID, PetData snapshot) {
        if (ownerUUID == null || snapshot == null || snapshot.getPetUUID() == null) {
            return;
        }
        Object lock = ioLocks.computeIfAbsent(ownerUUID, k -> new Object());
        synchronized (lock) {
            List<PetData> pets = loadPets(ownerUUID);
            boolean found = false;
            for (PetData pet : pets) {
                if (pet.getPetUUID().equals(snapshot.getPetUUID())) {
                    pet.setDead(true);
                    pet.setMetadata(snapshot.getMetadata());
                    pet.setBase64EntityState(snapshot.getBase64EntityState());
                    if (pet.getEntityType() == null) {
                        pet.setEntityType(snapshot.getEntityType());
                    }
                    if (pet.getDisplayName() == null || pet.getDisplayName().isEmpty()) {
                        pet.setDisplayName(snapshot.getDisplayName());
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                snapshot.setDead(true);
                pets.add(snapshot);
            }
            savePets(ownerUUID, pets);
        }
    }

    /**
     * Remembers that a pet was freed while its entity was unloaded. The marker is
     * consumed the next time the entity is loaded, so a freed pet cannot be
     * automatically re-registered from its still-tamed entity data.
     */
    public synchronized void markPetFreed(UUID petUUID) {
        if (petUUID == null) {
            return;
        }
        List<String> freed = new ArrayList<>(this.dataConfig.getStringList("freed-pets"));
        String id = petUUID.toString();
        if (!freed.contains(id)) {
            freed.add(id);
            this.dataConfig.set("freed-pets", freed);
            saveDataConfig();
        }
    }

    public synchronized boolean isPetFreed(UUID petUUID) {
        return petUUID != null && this.dataConfig.getStringList("freed-pets").contains(petUUID.toString());
    }

    public synchronized void unmarkPetFreed(UUID petUUID) {
        if (petUUID == null) {
            return;
        }
        List<String> freed = new ArrayList<>(this.dataConfig.getStringList("freed-pets"));
        if (freed.remove(petUUID.toString())) {
            this.dataConfig.set("freed-pets", freed);
            saveDataConfig();
        }
    }


    public void migrateFromConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(configFile);
        org.bukkit.configuration.ConfigurationSection petDataSection = oldConfig.getConfigurationSection("pet-data");
        if (petDataSection == null || petDataSection.getKeys(false).isEmpty()) {
            return;
        }

        plugin.debugLog("Old pet data found in config.yml. Starting one-time migration...");
        int migratedCount = 0;
        Map<UUID, List<PetData>> petsByOwner = new HashMap<>();

        try {
            for (String key : petDataSection.getKeys(false)) {
                UUID petUUID = UUID.fromString(key);
                org.bukkit.configuration.ConfigurationSection petSection = petDataSection
                        .getConfigurationSection(key);
                if (petSection == null) {
                    continue;
                }
                Map<String, Object> petMap = petSection.getValues(false);
                PetData data = PetData.deserialize(petUUID, petMap);
                if (data != null) {
                    petsByOwner.computeIfAbsent(data.getOwnerUUID(), k -> new ArrayList<>()).add(data);
                    migratedCount++;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "An error occurred during data migration!", e);
            plugin.getLogger().warning("Migration failed. Old data has been left in config.yml.");
            return;
        }


        petsByOwner.forEach(this::savePets);


        oldConfig.set("pet-data", null);
        try {
            oldConfig.save(configFile);
            plugin.debugLog("Successfully migrated " + migratedCount + " pets to the new JSON format.");
            plugin.debugLog("Old data has been removed from config.yml.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config.yml after migration. Please remove the 'pet-data' section manually!", e);
        }
    }


    /**
     * Checks if a pet UUID is registered to ANY player (online or offline).
     * Scans all player storage files. This is used to prevent stealing pets via leash adoption.
     * 
     * @param petUUID The pet's UUID to check
     * @return true if the pet is registered to any player
     */
    public boolean isPetRegisteredGlobally(UUID petUUID) {
        if (petUUID == null) return false;
        
        String targetUUID = petUUID.toString();
        File[] playerFiles = playerDataFolder.listFiles((dir, name) -> name.endsWith(".json"));
        
        if (playerFiles == null) return false;
        
        for (File playerFile : playerFiles) {
            try {
                String json = Files.readString(playerFile.toPath(), StandardCharsets.UTF_8);
                // Quick string check before full parse (optimization)
                if (!json.contains(targetUUID)) {
                    continue;
                }
                
                // Full parse to confirm
                Type listType = new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType();
                List<Map<String, Object>> serializedPets = gson.fromJson(json, listType);
                
                if (serializedPets == null) continue;
                
                for (Map<String, Object> petMap : serializedPets) {
                    if (petMap != null && targetUUID.equals(petMap.get("petUUID"))) {
                        return true;
                    }
                }
            } catch (IOException | RuntimeException e) {
                // Skip corrupted files
                plugin.getLogger().fine("Skipping file during global pet check: " + playerFile.getName());
            }
        }
        
        return false;
    }


    public synchronized int getNextPetId() {
        return this.dataConfig.getInt("next-pet-id", 1);
    }

    public synchronized void incrementAndSaveNextPetId() {
        int nextId = getNextPetId();
        this.dataConfig.set("next-pet-id", nextId + 1);
        saveDataConfig();
    }

    /**
     * Atomically reserves and returns the next pet ID.
     */
    public synchronized int getNextPetIdAndIncrement() {
        int nextId = getNextPetId();
        this.dataConfig.set("next-pet-id", nextId + 1);
        saveDataConfig();
        return nextId;
    }

    private synchronized void saveDataConfig() {
        try {
            this.dataConfig.save(this.dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save to data.yml!", e);
        }
    }
}

