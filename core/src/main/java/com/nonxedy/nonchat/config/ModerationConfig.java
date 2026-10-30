package com.nonxedy.nonchat.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.util.chat.filters.CapsFilter;

/**
 * Settings of the chat moderation system, stored in moderation.yml.
 * On first load the settings of the old config.yml sections (banned-words, caps-filter, anti-ad,
 * anti-spam) are moved here by {@link ModerationMigrator}.
 */
public class ModerationConfig {

    private static final String FILE_NAME = "moderation.yml";

    private final Nonchat plugin;
    private final PluginMessages messages;
    private final File file;
    private volatile FileConfiguration config = new YamlConfiguration();

    public ModerationConfig(Nonchat plugin, PluginMessages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /**
     * Loads moderation.yml, creating it first if it does not exist: from the old config.yml
     * settings when there are any, from the bundled defaults otherwise
     */
    public void load() {
        if (!file.exists()) {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (!ModerationMigrator.migrate(configFile, file, messages, readTemplate())) {
                plugin.saveResource(FILE_NAME, false);
            }
        } else {
            // Add the keys introduced by newer versions, keeping the user's values
            ConfigUpdater.updateFile(plugin, file, FILE_NAME, null);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        plugin.getLogger().log(Level.FINE, "Loaded {0}", FILE_NAME);
    }

    private String readTemplate() {
        try (InputStream stream = plugin.getResource(FILE_NAME)) {
            return stream != null ? new String(stream.readAllBytes(), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public void reload() {
        load();
    }

    /**
     * Gets a top-level section ('banned-words', 'caps', 'advertising' or 'spam')
     * @return The section, or null if it is missing
     */
    public ConfigurationSection getSection(String key) {
        return config.getConfigurationSection(key);
    }

    @NotNull
    public List<String> getBannedWords() {
        return config.getStringList("banned-words.words");
    }

    @NotNull
    public List<String> getBannedPatterns() {
        return config.getStringList("banned-words.patterns");
    }

    public boolean isBannedWordsWholeWords() {
        return config.getBoolean("banned-words.whole-words", false);
    }

    @NotNull
    public List<String> getBannedWordsExceptions() {
        return config.getStringList("banned-words.exceptions");
    }

    public boolean isWordBlockingEnabled() {
        return config.getBoolean("banned-words.enabled", true);
    }

    /**
     * Gets caps filter instance
     * @return CapsFilter built from the current settings
     */
    public CapsFilter getCapsFilter() {
        return new CapsFilter(
            config.getBoolean("caps.enabled", true),
            getMaxCapsPercentage(),
            config.getInt("caps.min-length", 4)
        );
    }

    public int getMaxCapsPercentage() {
        return config.getInt("caps.max-caps-percentage", 70);
    }

    public boolean isAntiAdEnabled() {
        return config.getBoolean("advertising.enabled", true);
    }

    public float getAntiAdSensitivity() {
        return (float) config.getDouble("advertising.sensitivity", 0.7);
    }

    public List<String> getAntiAdWhitelistedUrls() {
        return config.getStringList("advertising.whitelisted-urls");
    }

    public boolean isAntiSpamEnabled() {
        return config.getBoolean("spam.enabled", true);
    }

    public boolean isAntiSpamRepetitiveEnabled() {
        return config.getBoolean("spam.repetitive.enabled", true);
    }

    public int getAntiSpamRepetitiveThreshold() {
        return config.getInt("spam.repetitive.threshold", 3);
    }

    public int getAntiSpamRepetitiveTimeWindow() {
        return config.getInt("spam.repetitive.time-window", 10);
    }

    public boolean isAntiSpamSimilarEnabled() {
        return config.getBoolean("spam.similar.enabled", true);
    }

    public double getAntiSpamSimilarThreshold() {
        return config.getDouble("spam.similar.threshold", 0.85);
    }

    public int getAntiSpamSimilarTimeWindow() {
        return config.getInt("spam.similar.time-window", 30);
    }

    public boolean isAntiSpamFloodEnabled() {
        return config.getBoolean("spam.flood.enabled", true);
    }

    public int getAntiSpamFloodMaxMessages() {
        return config.getInt("spam.flood.max-messages", 5);
    }

    public int getAntiSpamFloodTimeWindow() {
        return config.getInt("spam.flood.time-window", 5);
    }

    /**
     * Checks whether the TLD registry should be refreshed from the network on startup and reload.
     * When disabled (or when the download fails), the registry snapshot bundled in the jar is used.
     */
    public boolean shouldUpdateTldList() {
        return config.getBoolean("advertising.update-tld-list", true);
    }

    /**
     * Gets the URL of the TLD registry source. Any mirror of the official
     * IANA tlds-alpha-by-domain.txt file can be used.
     */
    public String getTldListUrl() {
        return config.getString("advertising.tld-list-url",
                "https://data.iana.org/TLD/tlds-alpha-by-domain.txt");
    }
}
