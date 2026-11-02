package com.nonxedy.nonchat.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.IConfigurable;
import com.nonxedy.nonchat.config.ModerationConfig;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.placeholders.impl.ConfigurablePlaceholder;
import com.nonxedy.nonchat.util.InteractivePlaceholderManager;

public class ConfigService implements IConfigurable {
    private final Nonchat plugin;
    private final PluginConfig config;
    private final PluginMessages messages;
    private final ModerationConfig moderation;

    public ConfigService(Nonchat plugin) {
        this.plugin = plugin;
        this.config = new PluginConfig(plugin);
        this.messages = new PluginMessages(plugin);
        this.moderation = new ModerationConfig(plugin, messages);
        load();
        this.messages.loadLanguage();
    }

    @Override
    public void load() {
        messages.loadLanguage();
        // Migrates the old sections out of config.yml, so it has to be read afterwards
        moderation.load();
        config.loadConfig();
    }

    @Override
    public void save() {
        config.saveConfig();
    }

    @Override
    public void reload() {
        //plugin.reloadConfig();
        messages.reloadConfig();
        moderation.reload();
        config.reloadConfig();
    }

    public PluginConfig getConfig() {
        return config;
    }

    public ModerationConfig getModeration() {
        return moderation;
    }

    public PluginMessages getMessages() {
        return messages;
    }

    public String getMessage(String key) {
        return messages.getString(key);
    }

    public void logCommand(String command, String[] args) {
        if (config.isDebug()) {
            plugin.logCommand(command, args);
        }
    }

    public void logError(String error) {
        if (config.isDebug()) {
            plugin.logError(error);
        }
    }

    @Override
    public String getString(String path) {
        return config.getString(path);
    }

    @Override
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    @Override
    public int getInt(String path) {
        return config.getInt(path);
    }

    @Override
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    @Override
    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    @Override
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    @Override
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    @Override
    public void set(String path, Object value) {
        config.set(path, value);
    }

    @Override
    public boolean contains(String path) {
        return config.contains(path);
    }

    @Override
    public ConfigurationSection getSection(String path) {
        return config.getConfigurationSection(path);
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        return config.getKeys(deep);
    }

    /**
     * Checks if undelivered message notifications are enabled
     * @return true if notifications are enabled
     */
    @Override
    public boolean isUndeliveredMessageNotificationEnabled() {
        return config.isUndeliveredMessageNotificationEnabled();
    }

    /**
     * Sets undelivered message notification enabled state
     * @param enabled New enabled state
     */
    @Override
    public void setUndeliveredMessageNotificationEnabled(boolean enabled) {
        config.setUndeliveredMessageNotificationEnabled(enabled);
    }

    /**
     * Checks if interactive placeholders are globally enabled
     * @return true if enabled
     */
    public boolean isInteractivePlaceholdersEnabled() {
        return config.isInteractivePlaceholdersEnabled();
    }

    /**
     * Sets interactive placeholders global enabled state
     * @param enabled New enabled state
     */
    public void setInteractivePlaceholdersEnabled(boolean enabled) {
        config.setInteractivePlaceholdersEnabled(enabled);
    }

    /**
     * Gets all configured custom placeholders from config
     * @return Map of placeholder keys to their configurations
     */
    public Map<String, org.bukkit.configuration.ConfigurationSection> getCustomPlaceholders() {
        return config.getCustomPlaceholders();
    }

    /**
     * Checks if a specific custom placeholder is enabled
     * @param placeholderKey The placeholder key
     * @return true if enabled
     */
    public boolean isCustomPlaceholderEnabled(String placeholderKey) {
        return config.isCustomPlaceholderEnabled(placeholderKey);
    }

    /**
     * Gets the activation name for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The activation name (e.g., "loc" for [loc])
     */
    public String getCustomPlaceholderActivation(String placeholderKey) {
        return config.getCustomPlaceholderActivation(placeholderKey);
    }

    /**
     * Gets the display name for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The display name
     */
    public String getCustomPlaceholderDisplayName(String placeholderKey) {
        return config.getCustomPlaceholderDisplayName(placeholderKey);
    }

    /**
     * Gets the format string for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The format string
     */
    public String getCustomPlaceholderFormat(String placeholderKey) {
        return config.getCustomPlaceholderFormat(placeholderKey);
    }

    /**
     * Gets the permission required for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The permission string (empty if none required)
     */
    public String getCustomPlaceholderPermission(String placeholderKey) {
        return config.getCustomPlaceholderPermission(placeholderKey);
    }

    /**
     * Gets the hover text for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return List of hover text lines
     */
    public List<String> getCustomPlaceholderHoverText(String placeholderKey) {
        return config.getCustomPlaceholderHoverText(placeholderKey);
    }

    /**
     * Gets the click action type for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The click action type
     */
    public String getCustomPlaceholderClickActionType(String placeholderKey) {
        return config.getCustomPlaceholderClickActionType(placeholderKey);
    }

    /**
     * Gets the click action value for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The click action value
     */
    public String getCustomPlaceholderClickActionValue(String placeholderKey) {
        return config.getCustomPlaceholderClickActionValue(placeholderKey);
    }

    /**
     * Loads custom placeholders from config and registers them with the interactive placeholder manager
     * @param placeholderManager The manager to register placeholders with
     */
    public void loadCustomPlaceholdersFromConfig(InteractivePlaceholderManager placeholderManager) {
        try {
            // Load custom placeholders from config
            ConfigurationSection customPlaceholdersSection =
                plugin.getConfig().getConfigurationSection("interactive-placeholders.custom-placeholders");

            if (customPlaceholdersSection != null) {
                int loadedCount = 0;
                for (String placeholderKey : customPlaceholdersSection.getKeys(false)) {
                    ConfigurationSection placeholderConfig =
                        customPlaceholdersSection.getConfigurationSection(placeholderKey);

                    if (placeholderConfig != null && placeholderConfig.getBoolean("enabled", false)) {
                        String displayName = placeholderConfig.getString("display-name", placeholderKey);
                        String description = placeholderConfig.getString("description", "");
                        String permission = placeholderConfig.getString("permission", "");
                        String activationKey = placeholderConfig.getString("placeholder", placeholderKey);
                        String format = placeholderConfig.getString("format", "[" + activationKey + "]");
                        List<String> hoverText = placeholderConfig.getStringList("hover-text");

                        // Click action
                        String clickActionType = "none";
                        String clickActionValue = "";
                        ConfigurationSection clickActionSection =
                            placeholderConfig.getConfigurationSection("click-action");
                        if (clickActionSection != null) {
                            clickActionType = clickActionSection.getString("type", "none");
                            clickActionValue = clickActionSection.getString("value", "");
                        }

                        // Create and register the placeholder
                        ConfigurablePlaceholder placeholder = new ConfigurablePlaceholder(
                            placeholderKey,
                            activationKey,
                            displayName,
                            description,
                            true, // Already checked enabled above
                            permission,
                            format,
                            hoverText,
                            clickActionType,
                            clickActionValue
                        );

                        placeholderManager.registerPlaceholder(placeholder);
                        loadedCount++;
                    }
                }
                plugin.getLogger().log(Level.INFO, "Loaded {0} custom interactive placeholders from config", loadedCount);
            } else {
                plugin.getLogger().info("No custom placeholders section found in config");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load custom placeholders from config: {0}", e.getMessage());
        }
    }
}
