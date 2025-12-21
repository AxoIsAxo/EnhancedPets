package dev.asteriamc.enhancedpets.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.data.PetData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
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
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
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
        File playerFile = new File(playerDataFolder, ownerUUID.toString() + ".json");
        if (!playerFile.exists()) {
            return new ArrayList<>();
        }

        Object lock = ioLocks.computeIfAbsent(ownerUUID, k -> new Object());
        synchronized (lock) {
            try {
                String json = Files.readString(playerFile.toPath());
                Type listType = new TypeToken<ArrayList<HashMap<String, Object>>>() {
                }.getType();
                List<Map<String, Object>> serializedPets = gson.fromJson(json, listType);

                if (serializedPets == null) return new ArrayList<>();

                List<PetData> pets = new ArrayList<>();
                for (Map<String, Object> petMap : serializedPets) {
                    if (petMap.containsKey("petUUID")) {
                        UUID petUUID = UUID.fromString((String) petMap.get("petUUID"));
                        PetData data = PetData.deserialize(petUUID, petMap);
                        if (data != null) {
                            pets.add(data);
                        }
                    }
                }
                return pets;
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not load pet data for " + ownerUUID, ex);
                return new ArrayList<>();
            } catch (com.google.gson.JsonSyntaxException jse) {
                plugin.getLogger().log(Level.SEVERE, "Corrupt JSON for " + ownerUUID + " — leaving file intact.", jse);
                return new ArrayList<>();
            }
        }
    }

    public void savePets(UUID ownerUUID, List<PetData> pets) {
        File playerFile = new File(playerDataFolder, ownerUUID.toString() + ".json");


        List<PetData> snapshot = (pets == null) ? Collections.emptyList() : new ArrayList<>(pets);

        List<Map<String, Object>> serializedPets = snapshot.stream()
                .map(petData -> {
                    Map<String, Object> map = petData.serialize();
                    map.put("petUUID", petData.getPetUUID().toString());
                    return map;
                })
                .collect(Collectors.toList());

        Object lock = ioLocks.computeIfAbsent(ownerUUID, k -> new Object());
        synchronized (lock) {
            File tempFile = new File(playerFile.getParentFile(), playerFile.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tempFile, false)) {
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


    public void migrateFromConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(configFile);
        if (!oldConfig.isConfigurationSection("pet-data") || oldConfig.getConfigurationSection("pet-data").getKeys(false).isEmpty()) {
            return;
        }

        plugin.debugLog("Old pet data found in config.yml. Starting one-time migration...");
        int migratedCount = 0;
        Map<UUID, List<PetData>> petsByOwner = new HashMap<>();

        try {
            for (String key : oldConfig.getConfigurationSection("pet-data").getKeys(false)) {
                UUID petUUID = UUID.fromString(key);
                Map<String, Object> petMap = oldConfig.getConfigurationSection("pet-data." + key).getValues(false);
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


    public int getNextPetId() {
        return this.dataConfig.getInt("next-pet-id", 1);
    }

    public void incrementAndSaveNextPetId() {
        int nextId = getNextPetId();
        this.dataConfig.set("next-pet-id", nextId + 1);
        saveDataConfig();
    }

    private void saveDataConfig() {
        try {
            this.dataConfig.save(this.dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save to data.yml!", e);
        }
    }
}

