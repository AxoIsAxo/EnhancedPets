package dev.asteriamc.enhancedpets.config;

import dev.asteriamc.enhancedpets.Enhancedpets;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private static final Material DEFAULT_REVIVE_ITEM = Material.NETHER_STAR;

    private final Enhancedpets plugin;
    private FileConfiguration config;
    private boolean ocelotTamingLegacyStyle;
    private boolean shiftdoubleclickgui;

    private Material reviveItem = DEFAULT_REVIVE_ITEM;
    private int reviveItemAmount = 1;
    private boolean reviveItemRequireMainHand = true;
    private int healCost = 100;
    private boolean debug;

    public ConfigManager(Enhancedpets plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        this.config = this.plugin.getConfig();
        this.ocelotTamingLegacyStyle = this.config.getBoolean("ocelot-taming-legacy-style", false);
        this.shiftdoubleclickgui = this.config.getBoolean("shift-doubleclick-pet-gui", true);

        String reviveItemString = this.config.getString("revive-item", DEFAULT_REVIVE_ITEM.name());
        for (Material material : Material.values()) {
            if (material.name().equalsIgnoreCase(reviveItemString)) {
                this.reviveItem = material;
                break;
            }
        }

        this.reviveItemAmount = this.config.getInt("revive-item-amount", 1);
        this.reviveItemRequireMainHand = this.config.getBoolean("revive-item-require-mainhand", true);
        this.healCost = this.config.getInt("pet-heal-cost", 100);

        this.debug = this.config.getBoolean("debug", false);
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

    public Material getReviveItem() {
        return this.reviveItem;
    }

    public int getReviveItemAmount() {
        return this.reviveItemAmount;
    }

    public boolean isReviveItemRequireMainHand() {
        return this.reviveItemRequireMainHand;
    }

    public int getHealCost() {
        return this.healCost;
    }
}