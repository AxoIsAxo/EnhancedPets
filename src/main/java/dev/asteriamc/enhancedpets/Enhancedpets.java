package dev.asteriamc.enhancedpets;

import dev.asteriamc.enhancedpets.commands.PetCommand;
import dev.asteriamc.enhancedpets.config.ConfigManager;
import dev.asteriamc.enhancedpets.gui.PetGUIListener;
import dev.asteriamc.enhancedpets.gui.PetManagerGUI;
import dev.asteriamc.enhancedpets.listeners.PetListener;
import dev.asteriamc.enhancedpets.listeners.PlayerChatListener;
import dev.asteriamc.enhancedpets.manager.PetManager;
import dev.asteriamc.enhancedpets.tasks.GrowthGuardTask;
import dev.asteriamc.enhancedpets.tasks.PetTargetingTask;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class Enhancedpets extends JavaPlugin {
   private static Enhancedpets instance;
   private ConfigManager configManager;
   private PetManager petManager;
   private PetManagerGUI guiManager;
   private PetGUIListener petGUIListener;
   private PetTargetingTask targetingTaskRunnable;
   private BukkitTask targetingTask;

   public void onEnable() {
      instance = this;
      this.getLogger().info("EnhancedPets is enabling...");
      this.configManager = new ConfigManager(this);
      this.petManager = new PetManager(this);
      this.guiManager = new PetManagerGUI(this);
      this.petGUIListener = new PetGUIListener(this, this.guiManager);
      this.loadConfigurationAndData();
      PetCommand petCommandExecutor = new PetCommand(this, this.guiManager);
      Objects.requireNonNull(this.getCommand("pets")).setExecutor(petCommandExecutor);
      Bukkit.getPluginManager().registerEvents(new PetListener(this), this);
      Bukkit.getPluginManager().registerEvents(this.petGUIListener, this);
      Bukkit.getPluginManager().registerEvents(new PlayerChatListener(this, this.petManager, this.guiManager, this.petGUIListener), this);
      this.startTargetingTask();
      this.getLogger().info("EnhancedPets has been enabled successfully!");
   }

   public void onDisable() {
      this.getLogger().info("EnhancedPets is disabling...");
      this.stopTargetingTask();
      if (this.petManager != null) {
         this.petManager.savePetData();
      }

      this.getLogger().info("EnhancedPets has been disabled.");
   }

   private void loadConfigurationAndData() {
      try {
         this.configManager.loadConfig();
         this.petManager.loadPetData();
      } catch (Exception var2) {
         this.getLogger().log(Level.SEVERE, "Failed during initial configuration/data load!", (Throwable)var2);
      }
   }

   public void reloadPluginConfig(CommandSender sender) {
      sender.sendMessage(ChatColor.YELLOW + "Reloading EnhancedPets configuration...");
      this.stopTargetingTask();

      try {
         this.configManager.loadConfig();
         sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully.");
         sender.sendMessage(ChatColor.GRAY + "(Pet data was not reloaded from config.)");
      } catch (Exception var6) {
         sender.sendMessage(ChatColor.RED + "An error occurred while reloading the configuration. Check console logs!");
         this.getLogger().log(Level.SEVERE, "Error during EnhancedPets configuration reload:", (Throwable)var6);
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
         this.targetingTaskRunnable = new PetTargetingTask(this, this.petManager);
         new GrowthGuardTask(this).runTaskTimer(this, 20L, 20L);
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

   public static Enhancedpets getInstance() {
      return instance;
   }

   public ConfigManager getConfigManager() {
      return this.configManager;
   }

   public PetManager getPetManager() {
      return this.petManager;
   }

   public PetManagerGUI getGuiManager() {
      return this.guiManager;
   }

   public PetGUIListener getPetGUIListener() {
      return this.petGUIListener;
   }
}
