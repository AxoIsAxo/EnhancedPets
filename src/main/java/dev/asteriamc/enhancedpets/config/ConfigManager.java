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
    private boolean happyGhastFireball;
    private boolean debug;


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
        this.shiftdoubleclickgui = this.config.getBoolean("shift-doubleclick-pet-gui", true);
        this.happyGhastFireball = this.config.getBoolean("happy-ghast-fireball", true);
        this.debug = this.config.getBoolean("debug", false);
        if (!this.dogCreeperBehavior.equals("NEUTRAL") && !this.dogCreeperBehavior.equals("ATTACK") && !this.dogCreeperBehavior.equals("FLEE")) {
            this.plugin.getLogger().warning("Invalid value for 'dog-creeper-behavior' in config.yml. Defaulting to NEUTRAL.");
            this.dogCreeperBehavior = "NEUTRAL";
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

    public boolean isShiftDoubleClickGUI() {
        return this.shiftdoubleclickgui;
    }

    public FileConfiguration getConfig() {
        return this.config;
    }

    public boolean isDebug() {
        return this.debug;
    }

    public boolean isHappyGhastFireballEnabled() {
        return this.happyGhastFireball;
    }

}