package dev.asteriamc.enhancedpets;

import dev.asteriamc.enhancedpets.commands.PetCommand;
import dev.asteriamc.enhancedpets.config.ConfigManager;
import dev.asteriamc.enhancedpets.gui.PetGUIListener;
import dev.asteriamc.enhancedpets.gui.PetManagerGUI;
import dev.asteriamc.enhancedpets.listeners.PetListener;
import dev.asteriamc.enhancedpets.listeners.PlayerChatListener;
import dev.asteriamc.enhancedpets.listeners.PlayerConnectionListener;
import dev.asteriamc.enhancedpets.manager.PetManager;
import dev.asteriamc.enhancedpets.manager.PetStorageManager;
import dev.asteriamc.enhancedpets.tasks.GrowthGuardTask;
import dev.asteriamc.enhancedpets.tasks.PetTargetingTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

public final class Enhancedpets extends JavaPlugin {
    private static Enhancedpets instance;
    private ConfigManager configManager;
    private PetManager petManager;
    private PetManagerGUI guiManager;
    private PetGUIListener petGUIListener;
    private PetTargetingTask targetingTaskRunnable;
    private BukkitTask targetingTask;
    private PetStorageManager storageManager;
    private BukkitTask autosaveTask;
    private PetListener petListener;
    private BukkitTask growthGuardTask;

    public static Enhancedpets getInstance() {
        return instance;
    }

    public void onEnable() {
        instance = this;
        this.getLogger().info("EnhancedPets is enabling...");
        this.storageManager = new PetStorageManager(this);
        this.storageManager.migrateFromConfig();
        this.configManager = new ConfigManager(this);
        this.petManager = new PetManager(this);
        this.guiManager = new PetManagerGUI(this);
        this.petGUIListener = new PetGUIListener(this, this.guiManager);
        this.loadConfigurationAndData();
        PetCommand petCommandExecutor = new PetCommand(this, this.guiManager);
        Objects.requireNonNull(this.getCommand("pets")).setExecutor(petCommandExecutor);


        this.petListener = new PetListener(this);

        Bukkit.getPluginManager().registerEvents(this.petListener, this);
        Bukkit.getPluginManager().registerEvents(this.petGUIListener, this);
        Bukkit.getPluginManager().registerEvents(new PlayerChatListener(this, this.petManager, this.guiManager, this.petGUIListener), this);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            getLogger().info("Reload detected. Loading pet data for " + Bukkit.getOnlinePlayers().size() + " online players...");
            Bukkit.getOnlinePlayers().forEach(player -> petManager.loadPetsForPlayer(player.getUniqueId()));
        }

        this.startTargetingTask();
        this.startAutosaveTask();
        this.getLogger().info("EnhancedPets has been enabled successfully!");
    }

    public PetListener getPetListener() {
        return this.petListener;
    }

    @Override
    public void onDisable() {
        this.getLogger().info("EnhancedPets is disabling...");

        this.stopAutosaveTask();
        this.stopTargetingTask();

        if (this.petManager != null) {
            this.petManager.cancelAllPendingOwnerSaves();
            this.petManager.saveAllCachedDataImmediate();
        }

        this.getLogger().info("EnhancedPets has been disabled.");
    }

    private void startAutosaveTask() {
        stopAutosaveTask();
        long periodTicks = 2L * 60L * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                () -> {
                    if (petManager != null) {
                        petManager.saveAllCachedData();
                    }
                },
                periodTicks, periodTicks
        );
        getLogger().info("Autosave scheduled every 2 minutes.");
    }

    private void stopAutosaveTask() {
        if (autosaveTask != null && !autosaveTask.isCancelled()) {
            autosaveTask.cancel();
            autosaveTask = null;
            getLogger().info("Autosave task cancelled.");
        }
    }

    private void loadConfigurationAndData() {
        try {
            this.configManager.loadConfig();
        } catch (Exception var2) {
            this.getLogger().log(Level.SEVERE, "Failed during initial configuration load!", var2);
        }
    }

    public void reloadPluginConfig(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading EnhancedPets configuration...");
        this.stopTargetingTask();

        try {
            this.configManager.loadConfig();
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully.");
            sender.sendMessage(ChatColor.GRAY + "(Pet data is now stored in JSON files and was not reloaded.)");
        } catch (Exception var6) {
            sender.sendMessage(ChatColor.RED + "An error occurred while reloading the configuration. Check console logs!");
            this.getLogger().log(Level.SEVERE, "Error during EnhancedPets configuration reload:", var6);
        } finally {
            this.startTargetingTask();
            sender.sendMessage(ChatColor.YELLOW + "Pet targeting task restarted.");
        }
    }

    public void startTargetingTask() {
        this.stopTargetingTask();
        long delayTicks = 100L;
        long periodTicks = 40L;
        if (this.petManager == null) {
            this.getLogger().severe("PetManager is null, cannot start targeting task!");
        } else {
            if (growthGuardTask != null && !growthGuardTask.isCancelled()) {
                growthGuardTask.cancel();
            }
            growthGuardTask = new GrowthGuardTask(this).runTaskTimer(this, 20L, 20L);
            this.targetingTaskRunnable = new PetTargetingTask(this, this.petManager);
            this.targetingTask = this.targetingTaskRunnable.runTaskTimer(this, delayTicks, periodTicks);
            this.getLogger().log(Level.INFO, "Scheduled Aggressive Pet Targeting Task (runs every {0} seconds).", periodTicks / 20.0);
        }
    }


    public void stopTargetingTask() {
        if (this.targetingTask != null && !this.targetingTask.isCancelled()) {
            this.targetingTask.cancel();
            this.getLogger().info("Cancelled Aggressive Pet Targeting Task.");
            this.targetingTask = null;
            this.targetingTaskRunnable = null;
        }
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public PetManager getPetManager() {
        return this.petManager;
    }

    public PetStorageManager getStorageManager() {
        return this.storageManager;
    }

    public PetManagerGUI getGuiManager() {
        return this.guiManager;
    }

    public PetGUIListener getPetGUIListener() {
        return this.petGUIListener;
    }
}
