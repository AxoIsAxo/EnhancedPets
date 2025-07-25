package dev.asteriamc.enhancedpets.config;

import dev.asteriamc.enhancedpets.Enhancedpets;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
   private final Enhancedpets plugin;
   private FileConfiguration config;
   private boolean catsAttackHostiles;
   private String dogCreeperBehavior;
   private boolean ocelotTamingLegacyStyle;
   private boolean shiftdoubleclickgui;

   public ConfigManager(Enhancedpets plugin) {
      this.plugin = plugin;
   }

   public void loadConfig() {
      this.plugin.saveDefaultConfig();
      this.plugin.reloadConfig();
      this.config = this.plugin.getConfig();
      this.catsAttackHostiles = this.config.getBoolean("cats-attack-hostiles", false);
      this.dogCreeperBehavior = this.config.getString("dog-creeper-behavior", "NEUTRAL").toUpperCase();
      this.ocelotTamingLegacyStyle = this.config.getBoolean("ocelot-taming-legacy-style", false);
      this.shiftdoubleclickgui = this.config.getBoolean("shift-doubleclick-pet-gui",true);
      if (!this.dogCreeperBehavior.equals("NEUTRAL") && !this.dogCreeperBehavior.equals("ATTACK") && !this.dogCreeperBehavior.equals("FLEE")) {
         this.plugin.getLogger().warning("Invalid value for 'dog-creeper-behavior' in config.yml. Defaulting to NEUTRAL.");
         this.dogCreeperBehavior = "NEUTRAL";
      }

      if (!this.config.contains("next-pet-id")) {
         this.config.set("next-pet-id", 1);
      }

      if (!this.config.contains("pet-data")) {
         this.config.createSection("pet-data");
      }
   }

   public boolean isCatsAttackHostiles() {
      return this.catsAttackHostiles;
   }

   public String getDogCreeperBehavior() {
      return this.dogCreeperBehavior;
   }

   public boolean isOcelotTamingLegacyStyle() {
      return this.ocelotTamingLegacyStyle;
   }

   public boolean isShiftDoubleClickGUI(){
      return this.shiftdoubleclickgui;
   }

   public int getNextPetId() {
      return this.config.getInt("next-pet-id", 1);
   }

   public void incrementAndSaveNextPetId() {
      int nextId = this.getNextPetId();
      this.config.set("next-pet-id", nextId + 1);
      this.plugin.saveConfig();
   }

   public FileConfiguration getConfig() {
      return this.config;
   }
}
