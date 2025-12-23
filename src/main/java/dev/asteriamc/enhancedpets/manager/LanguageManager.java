package dev.asteriamc.enhancedpets.manager;

import dev.asteriamc.enhancedpets.Enhancedpets;
import dev.asteriamc.enhancedpets.util.ColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class LanguageManager {
    private final Enhancedpets plugin;
    private FileConfiguration langConfig;
    private File langFile;

    public LanguageManager(Enhancedpets plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        if (langFile == null) {
            langFile = new File(plugin.getDataFolder(), "language.yml");
        }
        if (!langFile.exists()) {
            plugin.saveResource("language.yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // MIGRATION: Fix wrongly-nested pet/misc sections (they should be at root, not
        // under menus)
        boolean migrated = false;
        if (langConfig.contains("menus.pet") && !langConfig.contains("pet")) {
            // pet section is wrongly nested under menus - move it to root
            var petSection = langConfig.getConfigurationSection("menus.pet");
            if (petSection != null) {
                for (String key : petSection.getKeys(false)) {
                    langConfig.set("pet." + key, petSection.get(key));
                }
                langConfig.set("menus.pet", null);
                migrated = true;
                plugin.getLogger().info("Migrated 'pet' section from menus.pet to root level.");
            }
        }
        if (langConfig.contains("menus.misc") && !langConfig.contains("misc")) {
            var miscSection = langConfig.getConfigurationSection("menus.misc");
            if (miscSection != null) {
                for (String key : miscSection.getKeys(false)) {
                    langConfig.set("misc." + key, miscSection.get(key));
                }
                langConfig.set("menus.misc", null);
                migrated = true;
                plugin.getLogger().info("Migrated 'misc' section from menus.misc to root level.");
            }
        }
        if (migrated) {
            save();
        }

        // Load defaults from internal resource and merge any missing keys
        try (java.io.InputStream defStream = plugin.getResource("language.yml")) {
            if (defStream != null) {
                FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8));

                // Explicitly add any missing keys from defaults to user config
                boolean updated = false;
                for (String key : defaults.getKeys(true)) {
                    if (!langConfig.contains(key)) {
                        langConfig.set(key, defaults.get(key));
                        updated = true;
                    }
                }

                if (updated) {
                    save();
                    plugin.getLogger().info("Updated language.yml with new keys from plugin.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not load default language.yml", e);
        }
    }

    public void save() {
        if (langConfig == null || langFile == null)
            return;
        try {
            langConfig.save(langFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save language.yml", e);
        }
    }

    public void sendMessage(CommandSender sender, String key, Placeholder... placeholders) {
        if (sender == null)
            return;
        if (!langConfig.contains(key)) {
            // Fallback for missing keys
            sender.sendMessage("Missing lang key: " + key);
            return;
        }
        List<String> messages = getMessageList(key);
        if (messages.isEmpty()) {
            // Try single string
            String single = langConfig.getString(key);
            if (single != null && !single.isEmpty()) {
                messages = Collections.singletonList(single);
            } else {
                return;
            }
        }

        for (String msg : messages) {
            for (Placeholder p : placeholders) {
                if (p.value != null) {
                    msg = msg.replace("%" + p.key + "%", p.value);
                }
            }
            msg = ColorUtil.colorize(msg);
            sender.sendMessage(msg);
        }
    }

    // Helper for simple "count" replacements or quick pairs
    public void sendReplacements(CommandSender sender, String key, String... quickReplacements) {
        Placeholder[] placeholders = new Placeholder[quickReplacements.length / 2];
        for (int i = 0; i < quickReplacements.length; i += 2) {
            if (i + 1 < quickReplacements.length) {
                placeholders[i / 2] = new Placeholder(quickReplacements[i], quickReplacements[i + 1]);
            }
        }
        sendMessage(sender, key, placeholders);
    }

    public String getString(String key) {
        return getString(key, new Placeholder[0]);
    }

    public String getStringReplacements(String key, String... quickReplacements) {
        Placeholder[] placeholders = toPlaceholders(quickReplacements);
        return getString(key, placeholders);
    }

    public String getString(String key, Placeholder... placeholders) {
        String msg = langConfig.getString(key);
        if (msg == null && langConfig.isList(key)) {
            List<String> list = langConfig.getStringList(key);
            if (!list.isEmpty())
                msg = list.get(0);
        }
        if (msg == null)
            return "Missing key: " + key;

        for (Placeholder p : placeholders) {
            if (p.value != null) {
                msg = msg.replace("%" + p.key + "%", p.value);
            }
        }
        return ColorUtil.colorize(msg);
    }

    public List<String> getStringList(String key) {
        return getStringList(key, new Placeholder[0]);
    }

    public List<String> getStringListReplacements(String key, String... quickReplacements) {
        Placeholder[] placeholders = toPlaceholders(quickReplacements);
        return getStringList(key, placeholders);
    }

    public List<String> getStringList(String key, Placeholder... placeholders) {
        List<String> messages = new java.util.ArrayList<>();
        if (langConfig.isList(key)) {
            messages.addAll(langConfig.getStringList(key));
        } else {
            String single = langConfig.getString(key);
            if (single != null)
                messages.add(single);
        }

        for (int i = 0; i < messages.size(); i++) {
            String msg = messages.get(i);
            for (Placeholder p : placeholders) {
                if (p.value != null) {
                    msg = msg.replace("%" + p.key + "%", p.value);
                }
            }
            messages.set(i, ColorUtil.colorize(msg));
        }
        return messages;
    }

    private Placeholder[] toPlaceholders(String... quickReplacements) {
        Placeholder[] placeholders = new Placeholder[quickReplacements.length / 2];
        for (int i = 0; i < quickReplacements.length; i += 2) {
            if (i + 1 < quickReplacements.length) {
                placeholders[i / 2] = new Placeholder(quickReplacements[i], quickReplacements[i + 1]);
            }
        }
        return placeholders;
    }

    private List<String> getMessageList(String key) {
        if (langConfig.isList(key)) {
            return langConfig.getStringList(key);
        }
        return Collections.emptyList();
    }

    public static class Placeholder {
        final String key;
        final String value;

        public Placeholder(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public Placeholder(String key, Object value) {
            this.key = key;
            this.value = String.valueOf(value);
        }
    }
}
