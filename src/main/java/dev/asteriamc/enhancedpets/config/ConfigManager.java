package dev.asteriamc.enhancedpets.config;

import dev.asteriamc.enhancedpets.Enhancedpets;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class ConfigManager {

    public enum Feature {
        MODE_PASSIVE("mode-passive"),
        MODE_NEUTRAL("mode-neutral"),
        MODE_AGGRESSIVE("mode-aggressive"),
        SIT("sit"),
        TELEPORT("teleport"),
        CALM("calm"),
        RENAME("rename"),
        FRIENDLY_PLAYERS("friendly-players"),
        TRANSFER("transfer"),
        PROTECTION("protection"),
        STATION("station"),
        TARGETING("targeting"),
        CREEPER_BEHAVIOR("creeper-behavior"),
        PUBLIC_ACCESS("public-access"),
        HEAL("heal"),
        STORAGE("storage"),
        FREE_PET("free-pet"),
        REMOVE_PET("remove-pet"),
        CUSTOMIZATION("customization"),
        FAVORITES("favorites"),
        GROWTH_CONTROL("growth-control"),
        REVIVE("revive"),
        BATCH_ACTIONS("batch-actions"),
        SCAN("scan"),
        ADOPTION("adoption"),
        INCUBATOR("incubator");

        private final String key;

        Feature(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    public static final String DEFAULT_LANGUAGE_FILE = "language.yml";
    private static final Material DEFAULT_REVIVE_ITEM = Material.NETHER_STAR;
    private static final String END_OF_CONFIG_MARKER = "END OF CONFIGURATION";

    private final Enhancedpets plugin;
    private final EnumMap<Feature, Boolean> features = new EnumMap<>(Feature.class);
    private FileConfiguration config;
    private boolean ocelotTamingLegacyStyle;
    private boolean shiftdoubleclickgui;

    private Material reviveItem = DEFAULT_REVIVE_ITEM;
    private int reviveItemAmount = 1;
    private boolean reviveItemRequireMainHand = true;
    private int healCost = 100;
    private boolean debug;
    private String languageFile = DEFAULT_LANGUAGE_FILE;

    public ConfigManager(Enhancedpets plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        this.plugin.saveDefaultConfig();

        // Older config files are missing keys that were added in later versions.
        // Add them (including their comments) before reading the values.
        addMissingDefaultsToFile();
        this.plugin.reloadConfig();
        this.config = this.plugin.getConfig();

        this.ocelotTamingLegacyStyle = this.config.getBoolean("ocelot-taming-legacy-style", false);
        this.shiftdoubleclickgui = this.config.getBoolean("shift-doubleclick-pet-gui", true);

        String reviveItemString = this.config.getString("revive-item", DEFAULT_REVIVE_ITEM.name());
        if (reviveItemString == null) {
            reviveItemString = DEFAULT_REVIVE_ITEM.name();
        }
        Material matchedReviveItem = Material.matchMaterial(reviveItemString);
        if (matchedReviveItem != null && matchedReviveItem != Material.AIR) {
            this.reviveItem = matchedReviveItem;
        } else {
            this.plugin.getLogger().warning("Invalid 'revive-item' in config.yml: '" + reviveItemString
                    + "'. Falling back to " + DEFAULT_REVIVE_ITEM.name() + ".");
            this.reviveItem = DEFAULT_REVIVE_ITEM;
        }

        this.reviveItemAmount = Math.max(1, this.config.getInt("revive-item-amount", 1));
        this.reviveItemRequireMainHand = this.config.getBoolean("revive-item-require-mainhand", true);
        this.healCost = Math.max(0, this.config.getInt("pet-heal-cost", 100));

        this.debug = this.config.getBoolean("debug", false);

        this.languageFile = sanitizeLanguageFile(this.config.getString("language-file", DEFAULT_LANGUAGE_FILE));

        for (Feature feature : Feature.values()) {
            this.features.put(feature, this.config.getBoolean("features." + feature.getKey(), true));
        }
    }

    public boolean isFeatureEnabled(Feature feature) {
        return feature != null && this.features.getOrDefault(feature, true);
    }

    /**
     * Appends any top-level keys from the bundled config.yml that are missing in
     * the server's config.yml. The default text is copied verbatim (comments
     * included), so user edits and comments in the existing file are preserved.
     *
     * @return true when at least one section was added
     */
    private boolean addMissingDefaultsToFile() {
        File configFile = new File(this.plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return false;
        }

        List<String> defaultLines;
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = this.plugin.getResource("config.yml")) {
            if (in == null) {
                return false;
            }
            defaultLines = readLines(in);
            defaults.loadFromString(String.join("\n", defaultLines));
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not read the bundled config.yml", e);
            return false;
        }

        Set<String> missingKeys = new LinkedHashSet<>();
        for (String key : defaults.getKeys(false)) {
            if (!this.plugin.getConfig().contains(key)) {
                missingKeys.add(key);
            }
        }
        if (missingKeys.isEmpty()) {
            return false;
        }

        Map<String, List<String>> defaultBlocks = extractTopLevelBlocks(defaultLines);
        List<String> userLines;
        boolean crlf;
        try {
            String existing = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            crlf = existing.contains("\r\n");
            userLines = new ArrayList<>(List.of(existing.split("\\R", -1)));
            // split(..., -1) keeps the empty element after the final line break.
            // Drop it so writing one separator per line does not add a blank line.
            if (!userLines.isEmpty() && userLines.get(userLines.size() - 1).isEmpty()) {
                userLines.remove(userLines.size() - 1);
            }
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not read config.yml for migration", e);
            return false;
        }

        int insertAt = userLines.size();
        for (int i = 0; i < userLines.size(); i++) {
            if (userLines.get(i).toUpperCase(Locale.ROOT).contains(END_OF_CONFIG_MARKER)) {
                insertAt = i;
                break;
            }
        }

        List<String> addition = new ArrayList<>();
        int added = 0;
        for (String key : missingKeys) {
            List<String> block = defaultBlocks.get(key);
            if (block == null || block.isEmpty()) {
                continue;
            }
            List<String> trimmedBlock = new ArrayList<>(block);
            while (!trimmedBlock.isEmpty() && trimmedBlock.get(trimmedBlock.size() - 1).isBlank()) {
                trimmedBlock.remove(trimmedBlock.size() - 1);
            }
            addition.add("");
            addition.addAll(trimmedBlock);
            added++;
        }
        if (addition.isEmpty()) {
            return false;
        }

        userLines.addAll(insertAt, addition);
        try {
            String lineSeparator = crlf ? "\r\n" : "\n";
            StringBuilder output = new StringBuilder();
            for (String line : userLines) {
                output.append(line).append(lineSeparator);
            }
            Files.writeString(configFile.toPath(), output.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not write migrated config.yml", e);
            return false;
        }

        this.plugin.getLogger().info("Added " + added
                + " new configuration option(s) to config.yml: " + String.join(", ", missingKeys));
        return true;
    }

    private static List<String> readLines(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Splits a flat YAML document into a block per top-level key. A block starts
     * at the comment lines directly above the key and ends right before the next
     * key's comments (or the end-of-config marker).
     */
    private static Map<String, List<String>> extractTopLevelBlocks(List<String> lines) {
        Map<String, List<String>> blocks = new LinkedHashMap<>();
        int contentEnd = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toUpperCase(Locale.ROOT).contains(END_OF_CONFIG_MARKER)) {
                contentEnd = i;
                break;
            }
        }

        List<String> keys = new ArrayList<>();
        List<Integer> keyStarts = new ArrayList<>();
        for (int i = 0; i < contentEnd; i++) {
            String line = lines.get(i);
            if (line.isEmpty() || Character.isWhitespace(line.charAt(0)) || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                keys.add(line.substring(0, colon).trim());
                keyStarts.add(commentStart(lines, i));
            }
        }

        for (int i = 0; i < keys.size(); i++) {
            int start = keyStarts.get(i);
            int end = (i + 1 < keyStarts.size()) ? keyStarts.get(i + 1) : contentEnd;
            blocks.put(keys.get(i), new ArrayList<>(lines.subList(start, end)));
        }
        return blocks;
    }

    private static int commentStart(List<String> lines, int keyIndex) {
        int start = keyIndex;
        while (start > 0) {
            String previous = lines.get(start - 1);
            if (previous.trim().startsWith("#")) {
                start--;
            } else {
                break;
            }
        }
        return start;
    }

    private String sanitizeLanguageFile(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_LANGUAGE_FILE;
        }
        String trimmed = configured.trim();
        if (!trimmed.matches("[A-Za-z0-9_-]+\\.(yml|yaml)")) {
            this.plugin.getLogger().warning("Invalid 'language-file' in config.yml: '" + configured
                    + "'. Expected a file name such as language.yml. Using " + DEFAULT_LANGUAGE_FILE + ".");
            return DEFAULT_LANGUAGE_FILE;
        }
        return trimmed;
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

    public String getLanguageFile() {
        return this.languageFile;
    }
}
