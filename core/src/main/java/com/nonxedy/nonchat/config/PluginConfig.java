package com.nonxedy.nonchat.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.util.chat.filters.CapsFilter;
import com.nonxedy.nonchat.util.chat.filters.WordBlocker;
import com.nonxedy.nonchat.util.chat.formatting.ChatTypeUtil;
import com.nonxedy.nonchat.util.chat.formatting.HoverTextUtil;
import com.nonxedy.nonchat.util.core.broadcast.BroadcastMessage;

import lombok.Getter;

/**
 * Central configuration manager for the NonChat plugin
 * Handles loading, saving and accessing all plugin settings
 */
@Getter
public class PluginConfig {
    // Plugin instance for resource access
    private final Nonchat plugin;
    // File object representing the config.yml file
    private final File configFile;
    // Configuration object to store and manage plugin settings
    private FileConfiguration config;
    // Debug mode flag
    private boolean debug;
    // Language setting
    private String language;
    // Default channel
    private String defaultChannel;

    // Constructor initializes config file path and loads configuration
    public PluginConfig(Nonchat plugin) {
        this.plugin = plugin;
        // Sets config file path to plugins/nonchat/config.yml
        this.configFile = new File("plugins/nonchat", "config.yml");
        loadConfig();
    }

    /**
     * Loads configuration from file or creates default
     */
    public void loadConfig() {
        // Check if config file exists
        if (!configFile.exists()) {
            // Create default configuration if file doesn't exist
            createDefaultConfig();
        } else {
            // Check if configuration needs to be updated (using BankPlus approach)
            updateConfigIfNeeded();
        }
        // Load configuration from file
        this.config = YamlConfiguration.loadConfiguration(configFile);
        // Set debug mode from config
        this.debug = config.getBoolean("debug", false);
        // Set language from config
        this.language = config.getString("language", "en");
        // Set default channel
        this.defaultChannel = config.getString("default-channel", "local");
    }

    /**
     * Creates default configuration file with initial settings
     * @throws RuntimeException if file creation fails
     */
    private void createDefaultConfig() {
        try {
            // Create directories if they don't exist
            configFile.getParentFile().mkdirs();
            // Create new config file
            configFile.createNewFile();

            // Initialize new configuration object
            config = new YamlConfiguration();
            // Set default configuration values
            setDefaultValues();
            // Save the configuration to file
            saveConfig();
        } catch (IOException e) {
            // Throw runtime exception if config creation fails
            throw new RuntimeException("Failed to create default config", e);
        }
    }

        /**
     * Sets all default configuration values
     */
    private void setDefaultValues() {
        // Language setting
        config.set("language", "en");
        
        // Debug mode setting
        config.set("debug", false);

        // Update checker settings
        config.set("update-checker", true);
        
        // Default channel setting
        config.set("default-channel", "local");
        
        // Message delivery notifications
        config.set("message-delivery.notify-undelivered", true);

        // Join/Quit messages settings
        config.set("join-messages.enabled", true);
        config.set("join-messages.format", "§8(§a+§8) %luckperms_prefix% §f%player_name%§r %luckperms_suffix%");
        config.set("join-messages.first-join-format", "§8(§a+§8) §f%player_name%§r §7joined for the first time!");
        config.set("quit-messages.enabled", true);
        config.set("quit-messages.format", "§8(§c-§8) %luckperms_prefix% §f%player_name%§r %luckperms_suffix%");
        
        // Private chat configuration
        config.set("private-chat.sender.format", "§8[§fYou §8-> §f{receiver}§8] §f{message}");
        config.set("private-chat.sender.hover.enabled", true);
        config.set("private-chat.sender.hover.text", Arrays.asList(
            "§7Sent to §f{receiver}",
            "§8Click to reply"
        ));
        config.set("private-chat.receiver.format", "§8[§f{sender} §f-> §fYou§8] §f{message}");
        config.set("private-chat.receiver.hover.enabled", true);
        config.set("private-chat.receiver.hover.text", Arrays.asList(
            "§7From §f{sender}",
            "§8Click to reply"
        ));
        config.set("private-chat.click-actions.enabled", true);
        config.set("private-chat.click-actions.reply-command", "/msg {sender} ");
        config.set("spy-format", "§f{sender} §7-> §f{target}§7: §7{message}");
        
        // Chat bubbles configuration
        config.set("chat-bubbles.enabled", true);
        config.set("chat-bubbles.duration", 5);
        config.set("chat-bubbles.height", 2.5);
        config.set("chat-bubbles.scale", 1.0);
        config.set("chat-bubbles.scale-x", 1.0);
        config.set("chat-bubbles.scale-y", 1.0);
        config.set("chat-bubbles.scale-z", 1.0);
        config.set("chat-bubbles.background-color", "#00000000");
        
        // Create default channel configurations
        createDefaultChannels();
        
        // Roleplay commands
        config.set("roleplay-commands.me.enabled", true);
        config.set("roleplay-commands.me.format", "&7*%player_name%: {message}");
        config.set("roleplay-commands.roll.enabled", true);
        config.set("roleplay-commands.roll.format", "&7*%player_name% rolled a {number}");
        
        // Hover text
        config.set("hover-text.enabled", true);
        List<String> defaultHoverFormat = Arrays.asList(
            "&#FFAFFB⭐ %player_name%", 
            "&#FFAFFB► Rank: &#FFFFFF%luckperms_prefix%",
            "&#FFAFFB► Balance: &#FFFFFF$%vault_eco_balance%",
            "&#FFAFFB► Level: &#FFFFFF%player_level%",
            "&#FFAFFB► Playtime: &#FFFFFF%statistic_time_played%",
            "&#FFAFFB► Location: &#FFFFFF%player_world%",
            "&#FFAFFB► Ping: &#FFFFFF%player_ping%ms",
            "§7",
            "§8Click to send a private message"
        );
        config.set("hover-text.format", defaultHoverFormat);
        
        // Banned words
        config.set("banned-words", Arrays.asList("spam", "badword", "anotherbadword", "плохой"));

        // Banned patterns (regex)
        config.set("banned-patterns", Arrays.asList(".*\\btest\\b.*", ".*\\d{4,}.*"));
        
        // Caps filter
        config.set("caps-filter.enabled", true);
        config.set("caps-filter.max-caps-percentage", 70);
        config.set("caps-filter.min-length", 4);
        
        // Broadcast settings
        config.set("broadcast.format", "\n§#FFAFFBBroadcast: §f{message}\n");
        config.set("broadcast.random", true);
        config.set("broadcast.example.enabled", true);
        config.set("broadcast.example.message", "This message will be sent every 60 seconds");
        config.set("broadcast.example.interval", 60);
        config.set("broadcast.example.display-in-console", true);
        
        // Anti-advertisement settings
        config.set("anti-ad.enabled", true);
        config.set("anti-ad.sensitivity", 0.7);
        config.set("anti-ad.whitelisted-urls", Arrays.asList(
            "discord.gg/NAWsxe3J3R"
        ));
        config.set("anti-ad.staff-notify", true);
        config.set("anti-ad.punish-command", "ban %player% advertising");
        
        // Anti-spam settings
        config.set("anti-spam.enabled", true);
        config.set("anti-spam.console-notify", true);
        
        // Repetitive message detection
        config.set("anti-spam.repetitive.enabled", true);
        config.set("anti-spam.repetitive.threshold", 3);
        config.set("anti-spam.repetitive.time-window", 10);
        config.set("anti-spam.repetitive.message", "&cPlease do not send the same message repeatedly!");
        config.set("anti-spam.repetitive.actions", Arrays.asList("block", "notify-staff"));
        
        // Similar message detection
        config.set("anti-spam.similar.enabled", true);
        config.set("anti-spam.similar.threshold", 0.85);
        config.set("anti-spam.similar.time-window", 30);
        config.set("anti-spam.similar.message", "&cPlease do not send similar messages repeatedly!");
        config.set("anti-spam.similar.actions", Arrays.asList("block", "notify-staff"));
        
        // Flood detection
        config.set("anti-spam.flood.enabled", true);
        config.set("anti-spam.flood.max-messages", 5);
        config.set("anti-spam.flood.time-window", 5);
        config.set("anti-spam.flood.message", "&cYou are sending messages too quickly! Please slow down.");
        config.set("anti-spam.flood.actions", Arrays.asList("block", "notify-staff"));
        
    }

    // Creates default chat channels configuration
    private void createDefaultChannels() {
        // Configure global chat channel
        config.set("channels.global.enabled", true);
        config.set("channels.global.display-name", "Global");
        config.set("channels.global.format", "§7(§6G§7)§r %luckperms_prefix% §f%player_name%§r %luckperms_suffix%§7: §f{message}");
        config.set("channels.global.radius", -1);
        config.set("channels.global.character", "!");
        config.set("channels.global.send-permission", "");
        config.set("channels.global.receive-permission", "");
        config.set("channels.global.cooldown", 0);
        config.set("channels.global.min-length", 0);
        config.set("channels.global.max-length", -1);
        config.set("channels.global.switch-message", "");

        // Configure local chat channel
        config.set("channels.local.enabled", true);
        config.set("channels.local.display-name", "Local");
        config.set("channels.local.format", "§7(§6L§7)§r %luckperms_prefix% §f%player_name%§r %luckperms_suffix%§7: §f{message}");
        config.set("channels.local.radius", 100);
        config.set("channels.local.character", "");
        config.set("channels.local.send-permission", "");
        config.set("channels.local.receive-permission", "");
        config.set("channels.local.cooldown", 0);
        config.set("channels.local.min-length", 0);
        config.set("channels.local.max-length", -1);
        config.set("channels.local.switch-message", "");
        
        // Configure staff chat channel
        config.set("channels.staff.enabled", true);
        config.set("channels.staff.display-name", "Staff");
        config.set("channels.staff.format", "§7(§bSC§7)§r %luckperms_prefix% §f%player_name%§r %luckperms_suffix%§7: §f{message}");
        config.set("channels.staff.radius", -1);
        config.set("channels.staff.character", "*");
        config.set("channels.staff.send-permission", "nonchat.chat.staff");
        config.set("channels.staff.receive-permission", "nonchat.chat.staff");
        config.set("channels.staff.cooldown", 0);
        config.set("channels.staff.min-length", 0);
        config.set("channels.staff.max-length", -1);
        config.set("channels.staff.switch-message", "");
    }

    /**
     * Gets configured language
     * @return Language code (en, ru)
     */
    @NotNull
    public String getLanguage() {
        return language;
    }
    
    /**
     * Gets the default channel ID
     * @return Default channel ID
     */
    @NotNull
    public String getDefaultChannel() {
        return defaultChannel;
    }

    /**
     * Checks if undelivered message notifications are enabled
     * @return true if notifications are enabled
     */
    public boolean isUndeliveredMessageNotificationEnabled() {
        return config.getBoolean("message-delivery.notify-undelivered", true);
    }

    /**
     * Sets undelivered message notification enabled state
     * @param enabled New enabled state
     */
    public void setUndeliveredMessageNotificationEnabled(boolean enabled) {
        config.set("message-delivery.notify-undelivered", enabled);
        saveConfig();
    }

    /**
     * Checks if interactive placeholders are globally enabled
     * @return true if enabled
     */
    public boolean isInteractivePlaceholdersEnabled() {
        return config.getBoolean("interactive-placeholders.enabled", true);
    }

    /**
     * Sets interactive placeholders global enabled state
     * @param enabled New enabled state
     */
    public void setInteractivePlaceholdersEnabled(boolean enabled) {
        config.set("interactive-placeholders.enabled", enabled);
        saveConfig();
    }

    /**
     * Gets all configured custom placeholders from config
     * @return Map of placeholder keys to their configurations
     */
    @NotNull
    public Map<String, ConfigurationSection> getCustomPlaceholders() {
        Map<String, ConfigurationSection> placeholders = new HashMap<>();
        ConfigurationSection customPlaceholdersSection =
            config.getConfigurationSection("interactive-placeholders.custom-placeholders");

        if (customPlaceholdersSection != null) {
            for (String key : customPlaceholdersSection.getKeys(false)) {
                ConfigurationSection placeholderConfig = customPlaceholdersSection.getConfigurationSection(key);
                if (placeholderConfig != null) {
                    placeholders.put(key, placeholderConfig);
                }
            }
        }

        return placeholders;
    }

    /**
     * Checks if a specific custom placeholder is enabled
     * @param placeholderKey The placeholder key
     * @return true if enabled
     */
    public boolean isCustomPlaceholderEnabled(String placeholderKey) {
        return config.getBoolean("interactive-placeholders.custom-placeholders." + placeholderKey + ".enabled", false);
    }

    /**
     * Gets the activation name for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The activation name (e.g., "loc" for [loc])
     */
    @NotNull
    public String getCustomPlaceholderActivation(String placeholderKey) {
        return config.getString("interactive-placeholders.custom-placeholders." + placeholderKey + ".placeholder", placeholderKey);
    }

    /**
     * Gets the display name for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The display name
     */
    @NotNull
    public String getCustomPlaceholderDisplayName(String placeholderKey) {
        return config.getString("interactive-placeholders.custom-placeholders." + placeholderKey + ".display-name", placeholderKey);
    }

    /**
     * Gets the format string for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The format string
     */
    @NotNull
    public String getCustomPlaceholderFormat(String placeholderKey) {
        return config.getString("interactive-placeholders.custom-placeholders." + placeholderKey + ".format", "[" + placeholderKey + "]");
    }

    /**
     * Gets the permission required for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The permission string (empty if none required)
     */
    @NotNull
    public String getCustomPlaceholderPermission(String placeholderKey) {
        return config.getString("interactive-placeholders.custom-placeholders." + placeholderKey + ".permission", "");
    }

    /**
     * Gets the hover text for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return List of hover text lines
     */
    @NotNull
    public List<String> getCustomPlaceholderHoverText(String placeholderKey) {
        return config.getStringList("interactive-placeholders.custom-placeholders." + placeholderKey + ".hover-text");
    }

    /**
     * Gets the click action type for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The click action type
     */
    @NotNull
    public String getCustomPlaceholderClickActionType(String placeholderKey) {
        ConfigurationSection clickActionSection =
            config.getConfigurationSection("interactive-placeholders.custom-placeholders." + placeholderKey + ".click-action");
        if (clickActionSection != null) {
            return clickActionSection.getString("type", "none");
        }
        return "none";
    }

    /**
     * Gets the click action value for a custom placeholder
     * @param placeholderKey The placeholder key
     * @return The click action value
     */
    @NotNull
    public String getCustomPlaceholderClickActionValue(String placeholderKey) {
        ConfigurationSection clickActionSection =
            config.getConfigurationSection("interactive-placeholders.custom-placeholders." + placeholderKey + ".click-action");
        if (clickActionSection != null) {
            return clickActionSection.getString("value", "");
        }
        return "";
    }




    /**
     * Checks if join messages are enabled
     * @return true if enabled
     */
    public boolean isJoinMessageEnabled() {
        return config.getBoolean("join-messages.enabled", true);
    }
    
    /**
     * Gets join message format
     * @return Formatted join message string
     */
    @NotNull
    public String getJoinFormat() {
        return config.getString("join-messages.format", "§8(§a+§8) %luckperms_prefix% §f%player_name%§r %luckperms_suffix%");
    }
    
    /**
     * Gets first join message format
     * @return Formatted first join message string
     */
    @NotNull
    public String getFirstJoinFormat() {
        return config.getString("join-messages.first-join-format", "§8(§a+§8) §f%player_name%§r §7joined for the first time!");
    }
    
    /**
     * Checks if quit messages are enabled
     * @return true if enabled
     */
    public boolean isQuitMessageEnabled() {
        return config.getBoolean("quit-messages.enabled", true);
    }
    
    /**
     * Gets quit message format
     * @return Formatted quit message string
     */
    @NotNull
    public String getQuitFormat() {
        return config.getString("quit-messages.format", "§8(§c-§8) %luckperms_prefix% §f%player_name%§r %luckperms_suffix%");
    }

    /**
     * Checks if join sound is enabled
     * @return true if join sound is enabled
     */
    public boolean isJoinSoundEnabled() {
        return config.getBoolean("join-messages.sound-enabled", true);
    }

    /**
     * Gets join sound name
     * @return Join sound name
     */
    @NotNull
    public String getJoinSound() {
        return config.getString("join-messages.sound", "entity.experience_orb.pickup");
    }

    /**
     * Gets join sound volume
     * @return Join sound volume (0.0 to 1.0)
     */
    public float getJoinSoundVolume() {
        return (float) config.getDouble("join-messages.sound-volume", 1.0);
    }

    /**
     * Gets join sound pitch
     * @return Join sound pitch (0.5 to 2.0)
     */
    public float getJoinSoundPitch() {
        return (float) config.getDouble("join-messages.sound-pitch", 1.0);
    }

    /**
     * Checks if quit sound is enabled
     * @return true if quit sound is enabled
     */
    public boolean isQuitSoundEnabled() {
        return config.getBoolean("quit-messages.sound-enabled", true);
    }

    /**
     * Gets quit sound name
     * @return Quit sound name
     */
    @NotNull
    public String getQuitSound() {
        return config.getString("quit-messages.sound", "entity.villager.no");
    }

    /**
     * Gets quit sound volume
     * @return Quit sound volume (0.0 to 1.0)
     */
    public float getQuitSoundVolume() {
        return (float) config.getDouble("quit-messages.sound-volume", 1.0);
    }

    /**
     * Gets quit sound pitch
     * @return Quit sound pitch (0.5 to 2.0)
     */
    public float getQuitSoundPitch() {
        return (float) config.getDouble("quit-messages.sound-pitch", 1.0);
    }

    /**
     * Checks if mention sounds are enabled
     * @return true if mention sounds are enabled
     */
    public boolean isMentionSoundEnabled() {
        return config.getBoolean("mention-sounds.enabled", true);
    }

    /**
     * Gets mention sound name
     * @return Mention sound name
     */
    @NotNull
    public String getMentionSound() {
        return config.getString("mention-sounds.sound", "entity.experience_orb.pickup");
    }

    /**
     * Gets mention sound volume
     * @return Mention sound volume (0.0 to 1.0)
     */
    public float getMentionSoundVolume() {
        return (float) config.getDouble("mention-sounds.volume", 1.0);
    }

    /**
     * Gets mention sound pitch
     * @return Mention sound pitch (0.5 to 2.0)
     */
    public float getMentionSoundPitch() {
        return (float) config.getDouble("mention-sounds.pitch", 1.0);
    }

    /**
     * Checks if mention coloring is enabled
     * @return true if mention coloring is enabled
     */
    public boolean isMentionColoringEnabled() {
        return config.getBoolean("mention-colors.enabled", true);
    }

    /**
     * Gets mention color
     * @return Mention color code
     */
    @NotNull
    public String getMentionColor() {
        return config.getString("mention-colors.color", "&#FFAFFB");
    }

    /**
     * Checks if players can be mentioned by typing their name without the '@' prefix
     * @return true if bare player names should also trigger mentions
     */
    public boolean isMentionWithoutAtEnabled() {
        return config.getBoolean("mentions.allow-without-at", false);
    }

    /**
     * Gets private chat sender message format
     * @return Private chat sender format string
     */
    @NotNull
    public String getPrivateChatSenderFormat() {
        return config.getString("private-chat.sender.format", "§8[§fYou §8-> §f{receiver}§8] §f{message}");
    }

    /**
     * Gets private chat receiver message format
     * @return Private chat receiver format string
     */
    @NotNull
    public String getPrivateChatReceiverFormat() {
        return config.getString("private-chat.receiver.format", "§8[§f{sender} §8-> §fYou§8] §f{message}");
    }

    /**
     * Checks if private chat sender hover text is enabled
     * @return true if sender hover text is enabled
     */
    public boolean isPrivateChatSenderHoverEnabled() {
        if (config.contains("private-chat.sender.hover.enabled")) {
            return config.getBoolean("private-chat.sender.hover.enabled", true);
        }
        // Legacy fallback
        return config.getBoolean("private-chat.hover.enabled", true);
    }

    /**
     * Checks if private chat receiver hover text is enabled
     * @return true if receiver hover text is enabled
     */
    public boolean isPrivateChatReceiverHoverEnabled() {
        if (config.contains("private-chat.receiver.hover.enabled")) {
            return config.getBoolean("private-chat.receiver.hover.enabled", true);
        }
        // Legacy fallback
        return config.getBoolean("private-chat.hover.enabled", true);
    }

    /**
     * Gets private chat sender hover text list
     * @return List of hover text lines for sender
     */
    @NotNull
    public List<String> getPrivateChatSenderHover() {
        List<String> hover = config.getStringList("private-chat.sender.hover.text");
        if (hover.isEmpty()) {
            // Legacy fallback
            hover = config.getStringList("private-chat.hover.sender-hover");
        }
        return hover;
    }

    /**
     * Gets private chat receiver hover text list
     * @return List of hover text lines for receiver
     */
    @NotNull
    public List<String> getPrivateChatReceiverHover() {
        List<String> hover = config.getStringList("private-chat.receiver.hover.text");
        if (hover.isEmpty()) {
            // Legacy fallback
            hover = config.getStringList("private-chat.hover.receiver-hover");
        }
        return hover;
    }

    /**
     * Checks if private chat click actions are enabled globally
     * @return true if click actions are enabled
     */
    public boolean isPrivateChatClickActionsEnabled() {
        return config.getBoolean("private-chat.click-actions.enabled", true);
    }

    /**
     * Gets private chat default reply command
     * @return Reply command template
     */
    @NotNull
    public String getPrivateChatReplyCommand() {
        return config.getString("private-chat.click-actions.reply-command", "/msg {sender} ");
    }

    /**
     * Gets spy message format
     * @return Spy message format string
     */
    @NotNull
    public String getSpyFormat() {
        return config.getString("spy-format", "§f{sender} §7-> §f{target}§7: §7{message}");
    }

    /**
     * Checks if broadcast system is enabled
     * @return true if enabled
     */
    public boolean isBroadcastEnabled() {
        return config.getBoolean("broadcast.enabled", true);
    }

    /**
     * Gets broadcast command format
     * @return Broadcast command format string
     */
    @NotNull
    public String getBroadcastFormat() {
        return config.getString("broadcast.format", "\n§#FFAFFBBroadcast: §f{message}\n");
    }

    /**
     * Sets broadcast command format
     * @param format New format string
     */
    public void setBroadcastFormat(String format) {
        config.set("broadcast.format", format);
        saveConfig();
    }

    /**
     * Checks if random broadcast is enabled
     * @return true if random mode enabled
     */
    public boolean isRandomBroadcastEnabled() {
        return config.getBoolean("broadcast.random", false);
    }

    /**
     * Gets default broadcast message
     * @return Default message string
     */
    @NotNull
    public String getBroadcastMessage() {
        return config.getString("broadcast.message", "Default broadcast message");
    }

    /**
     * Gets broadcast interval in seconds
     * @return Interval between broadcasts
     */
    public int getBroadcastInterval() {
        return config.getInt("broadcast.interval", 60);
    }

    /**
     * Gets all configured broadcast messages
     * @return Map of broadcast messages
     */
    @NotNull
    public Map<String, BroadcastMessage> getBroadcastMessages() {
        Map<String, BroadcastMessage> messages = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("broadcast");
        
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (key.equals("random")) continue;
                
                ConfigurationSection messageSection = section.getConfigurationSection(key);
                if (messageSection != null) {
                    messages.put(key, createBroadcastMessage(
                        messageSection.getBoolean("enabled", true),
                        messageSection.getString("message", "Default message"),
                        messageSection.getInt("interval", 60),
                        messageSection.getBoolean("display-in-console", true)
                    ));
                }
            }
        }
        return messages;
    }

    /**
     * Checks if chat bubbles are enabled
     * @return true if enabled
     */
    public boolean isChatBubblesEnabled() {
        return config.getBoolean("chat-bubbles.enabled", true);
    }

    /**
     * Gets chat bubble duration in seconds
     * @return Duration time
     */
    public int getChatBubblesDuration() {
        return config.getInt("chat-bubbles.duration", 5);
    }

    /**
     * Gets chat bubble height above player
     * @return Height in blocks
     */
    public double getChatBubblesHeight() {
        return config.getDouble("chat-bubbles.height", 2.5);
    }

    /**
     * Gets chat bubble scale
     * @return Scale multiplier (1.0 = normal size)
     */
    public double getChatBubblesScale() {
        return config.getDouble("chat-bubbles.scale", 1.0);
    }

    /**
     * Gets chat bubble scale X axis
     * @return Scale X multiplier (1.0 = normal size)
     */
    public double getChatBubblesScaleX() {
        return config.getDouble("chat-bubbles.scale-x", 1.0);
    }

    /**
     * Gets chat bubble scale Y axis
     * @return Scale Y multiplier (1.0 = normal size)
     */
    public double getChatBubblesScaleY() {
        return config.getDouble("chat-bubbles.scale-y", 1.0);
    }

    /**
     * Gets chat bubble scale Z axis
     * @return Scale Z multiplier (1.0 = normal size)
     */
    public double getChatBubblesScaleZ() {
        return config.getDouble("chat-bubbles.scale-z", 1.0);
    }

    /**
     * Gets chat bubble background color
     * @return Background color as hex string (e.g., "#00000000")
     */
    @NotNull
    public String getChatBubblesBackgroundColor() {
        return config.getString("chat-bubbles.background-color", "#00000000");
    }

    /**
     * Gets list of banned words
     * @return List of blocked words
     */
    @NotNull
    public List<String> getBannedWords() {
        return config.getStringList("banned-words.words");
    }

    /**
     * Gets list of banned regex patterns
     * @return List of blocked patterns
     */
    @NotNull
    public List<String> getBannedPatterns() {
        return config.getStringList("banned-words.patterns");
    }

    /**
     * Gets word blocker instance
     * @return Configured WordBlocker
     */
    @NotNull
    public WordBlocker getWordBlocker() {
        return new WordBlocker(getBannedWords(), getBannedPatterns());
    }

    /**
     * Checks if word blocking is enabled
     * @return true if word blocking is enabled
     */
    public boolean isWordBlockingEnabled() {
        return config.getBoolean("banned-words.enabled", true);
    }

    /**
     * Creates a new BroadcastMessage instance
     * @param enabled Whether the message is enabled
     * @param message The message content
     * @param interval The broadcast interval
     * @param displayInConsole Whether if the message should be display on console or not
     * @return New BroadcastMessage instance
     */
    public BroadcastMessage createBroadcastMessage(boolean enabled, String message, int interval, boolean displayInConsole) {
        return new BroadcastMessage(enabled, message, interval, displayInConsole);
    }

    /**
     * Checks if update checker is enabled
     * @return true if enabled
     */
    public boolean isUpdateCheckerEnabled() {
        return config.getBoolean("update-checker", true);
    }

    /**
     * Gets chat type by prefix character
     * @param prefix Character to check
     * @return Matching ChatTypeUtil or default
     */
    public ChatTypeUtil getChatTypeByChar(char prefix) {
        // Get all available chat types from configuration
        Map<String, ChatTypeUtil> chats = getChats();
        // Stream through all chat types and find the first one matching the prefix
        return chats.values().stream()
            // Filter to find chat with matching prefix character
            .filter(chat -> chat.getChatChar() == prefix)
            // Get the first matching chat type
            .findFirst()
            // If no chat found with this prefix, return default local chat
            .orElse(getDefaultChatType());
    }

    /**
     * Gets default chat type configuration
     * @return Default ChatTypeUtil
     */
    public ChatTypeUtil getDefaultChatType() {
        // Get all available chats from config
        // Return local chat if exists, otherwise create new default local chat
        return getChats().getOrDefault("local", 
            // Create new ChatTypeUtil with default local chat settings:
            new ChatTypeUtil(true, 
                "§7(§6L§7)§r {prefix} §f{sender}§r {suffix}§7: §f{message}", 
                100, '\0', null));
    }

    /**
     * Checks if specific chat type is enabled
     * @param channelId Channel ID to check
     * @return true if enabled
     */
    public boolean isChatEnabled(String channelId) {
        // Get enabled status from config, default to false if not found
        return config.getBoolean("channels." + channelId + ".enabled", false);
    }

    /**
     * Sets enabled state for chat type
     * @param channelId Channel ID to modify
     * @param enabled New enabled state
     */
    public void setChatEnabled(String channelId, boolean enabled) {
        // Update enabled status in config
        config.set("channels." + channelId + ".enabled", enabled);
        // Save changes to config file
        saveConfig();
    }

    /**
     * Gets all configured chat channels
     * @return Map of channel IDs to ChatTypeUtil objects
     */
    public Map<String, ChatTypeUtil> getChats() {
        Map<String, ChatTypeUtil> channels = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("channels");
        
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection channelSection = section.getConfigurationSection(key);
                if (channelSection != null) {
                    boolean enabled = channelSection.getBoolean("enabled", true);
                    String displayName = channelSection.getString("display-name", key);
                    String format = channelSection.getString("format", "{prefix} {sender} {suffix}: {message}");
                    int radius = channelSection.getInt("radius", -1);
                    String charStr = channelSection.getString("character", "");
                    char chatChar = charStr.isEmpty() ? '\0' : charStr.charAt(0);
                    
                    // Get send permission
                    String sendPermission = channelSection.getString("send-permission", "");
                    
                    // For backward compatibility, check old permission field too
                    if (sendPermission.isEmpty() && channelSection.contains("permission")) {
                        sendPermission = channelSection.getString("permission");
                    }
                    
                    // Get additional properties
                    String receivePermission = channelSection.getString("receive-permission", "");
                    int cooldown = channelSection.getInt("cooldown", 0);
                    int minLength = channelSection.getInt("min-length", 0);
                    int maxLength = channelSection.getInt("max-length", -1);
                    String switchMessage = channelSection.getString("switch-message");
                    
                    channels.put(key, new ChatTypeUtil(
                        enabled,
                        displayName,
                        format,
                        radius,
                        chatChar,
                        sendPermission,
                        receivePermission,
                        cooldown,
                        minLength,
                        maxLength,
                        switchMessage
                    ));
                }
            }
        }
        
        // For backward compatibility, check old "chats" section too
        ConfigurationSection oldSection = config.getConfigurationSection("chats");
        if (oldSection != null && channels.isEmpty()) {
            for (String key : oldSection.getKeys(false)) {
                ConfigurationSection chatSection = oldSection.getConfigurationSection(key);
                if (chatSection != null) {
                    boolean enabled = chatSection.getBoolean("enabled", true);
                    String format = chatSection.getString("format", "{prefix} {sender} {suffix}: {message}");
                    int radius = chatSection.getInt("radius", -1);
                    String charStr = chatSection.getString("char", "");
                    char chatChar = charStr.isEmpty() ? '\0' : charStr.charAt(0);
                    
                    // Permission is optional - if not specified, it will be null
                    String permission = null;
                    if (chatSection.contains("permission")) {
                        permission = chatSection.getString("permission");
                    }
                    
                    channels.put(key, new ChatTypeUtil(enabled, format, radius, chatChar, permission));
                }
            }
        }
        
        return channels;
    }

    /**
     * Gets debug mode state
     * @return true if debug enabled
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Gets debug log retention days from config
     * @return Number of days to keep debug logs (default 7)
     */
    public int getDebugLogRetentionDays() {
        return config.getInt("debug-log-retention-days", 7);
    }

    /**
     * Sets debug mode state
     * @param debug New debug state
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
        config.set("debug", debug); 
        saveConfig();
    }

    /**
     * Checks if /me command is enabled
     * @return true if enabled
     */
    public boolean isMeCommandEnabled() {
        return config.getBoolean("roleplay-commands.me.enabled", true);
    }

    /**
     * Gets /me command format
     * @return Format string
     */
    public String getMeFormat() {
        return config.getString("roleplay-commands.me.format", "&7*%player_name%: {message}");
    }

    /**
     * Checks if /roll command is enabled
     * @return true if enabled
     */
    public boolean isRollCommandEnabled() {
        return config.getBoolean("roleplay-commands.roll.enabled", true);
    }

    /**
     * Gets /roll command format
     * @return Format string
     */
    public String getRollFormat() {
        return config.getString("roleplay-commands.roll.format", "&7*%player_name% rolled a {number}");
    }

    /**
     * Checks if caps filter is enabled
     * @return true if enabled
     */
    public boolean isCapsFilterEnabled() {
        return config.getBoolean("caps-filter.enabled", true);
    }

    /**
     * Gets maximum allowed caps percentage
     * @return Maximum percentage
     */
    public int getMaxCapsPercentage() {
        return config.getInt("caps-filter.max-caps-percentage", 70);
    }

    /**
     * Gets minimum length for caps checking
     * @return Minimum length
     */
    public int getMinCapsLength() {
        return config.getInt("caps-filter.min-length", 4);
    }

    /**
     * Gets caps filter instance
     * @return Configured CapsFilter
     */
    public CapsFilter getCapsFilter() {
        return new CapsFilter(
            isCapsFilterEnabled(),
            getMaxCapsPercentage(),
            getMinCapsLength()
        );
    }

    /**
     * Checks if hover text is enabled
     * @return true if enabled
     */
    public boolean isHoverEnabled() {
        return config.getBoolean("hover-text.enabled", true);
    }

    /**
     * Gets hover text format
     * @return List of format lines
     */
    public List<String> getHoverFormat() {
        return config.getStringList("hover-text.format");
    }

    /**
     * Gets hover text utility instance
     * @return Configured HoverTextUtil
     */
    public HoverTextUtil getHoverTextUtil() {
        return new HoverTextUtil(getHoverFormat(), isHoverEnabled());
    }

    /**
     * Checks if anti-advertisement system is enabled
     * @return true if enabled
     */
    public boolean isAntiAdEnabled() {
        return config.getBoolean("anti-ad.enabled", true);
    }

    /**
     * Gets anti-advertisement sensitivity level
     * @return Sensitivity between 0.0 and 1.0
     */
    public float getAntiAdSensitivity() {
        return (float) config.getDouble("anti-ad.sensitivity", 0.7);
    }

    /**
     * Gets list of whitelisted URLs
     * @return List of allowed domains/IPs
     */
    public List<String> getAntiAdWhitelistedUrls() {
        return config.getStringList("anti-ad.whitelisted-urls");
    }

    /**
     * Checks if staff should be notified about ads
     * @return true if notifications enabled
     */
    public boolean shouldNotifyStaffAboutAds() {
        return config.getBoolean("anti-ad.staff-notify", true);
    }

    /**
     * Gets the punishment command to execute when advertisement is detected
     * @return Punishment command with %player% placeholder or empty string if no punishment 
     */
    public String getAntiAdPunishCommand() {
        return config.getString("anti-ad.punish-command", "ban %player_name% advertising");
    }

    /**
     * Checks if anti-spam system is enabled
     * @return true if enabled
     */
    public boolean isAntiSpamEnabled() {
        return config.getBoolean("anti-spam.enabled", true);
    }

    /**
     * Checks if repetitive message detection is enabled
     * @return true if enabled
     */
    public boolean isAntiSpamRepetitiveEnabled() {
        return config.getBoolean("anti-spam.repetitive.enabled", true);
    }

    /**
     * Gets repetitive message detection threshold
     * @return Maximum number of identical messages allowed
     */
    public int getAntiSpamRepetitiveThreshold() {
        return config.getInt("anti-spam.repetitive.threshold", 3);
    }

    /**
     * Gets repetitive message detection time window
     * @return Time window in seconds
     */
    public int getAntiSpamRepetitiveTimeWindow() {
        return config.getInt("anti-spam.repetitive.time-window", 10);
    }

    /**
     * Gets repetitive spam warning message
     * @return Warning message template
     */
    @NotNull
    public String getAntiSpamRepetitiveMessage() {
        return config.getString("anti-spam.repetitive.message", "&cPlease do not send the same message repeatedly!");
    }

    /**
     * Gets actions to execute when repetitive spam is detected
     * @return List of actions/commands
     */
    @NotNull
    public List<String> getAntiSpamRepetitiveActions() {
        return config.getStringList("anti-spam.repetitive.actions");
    }

    /**
     * Checks if similar message detection is enabled
     * @return true if enabled
     */
    public boolean isAntiSpamSimilarEnabled() {
        return config.getBoolean("anti-spam.similar.enabled", true);
    }

    /**
     * Gets similar message detection threshold
     * @return Similarity threshold (0.0-1.0)
     */
    public double getAntiSpamSimilarThreshold() {
        return config.getDouble("anti-spam.similar.threshold", 0.85);
    }

    /**
     * Gets similar message detection time window
     * @return Time window in seconds
     */
    public int getAntiSpamSimilarTimeWindow() {
        return config.getInt("anti-spam.similar.time-window", 30);
    }

    /**
     * Gets similar spam warning message
     * @return Warning message template
     */
    @NotNull
    public String getAntiSpamSimilarMessage() {
        return config.getString("anti-spam.similar.message", "&cPlease do not send similar messages repeatedly!");
    }

    /**
     * Gets actions to execute when similar spam is detected
     * @return List of actions/commands
     */
    @NotNull
    public List<String> getAntiSpamSimilarActions() {
        return config.getStringList("anti-spam.similar.actions");
    }

    /**
     * Checks if flood detection is enabled
     * @return true if enabled
     */
    public boolean isAntiSpamFloodEnabled() {
        return config.getBoolean("anti-spam.flood.enabled", true);
    }

    /**
     * Gets flood detection maximum messages
     * @return Maximum messages allowed in time window
     */
    public int getAntiSpamFloodMaxMessages() {
        return config.getInt("anti-spam.flood.max-messages", 5);
    }

    /**
     * Gets flood detection time window
     * @return Time window in seconds
     */
    public int getAntiSpamFloodTimeWindow() {
        return config.getInt("anti-spam.flood.time-window", 5);
    }

    /**
     * Gets flood spam warning message
     * @return Warning message template
     */
    @NotNull
    public String getAntiSpamFloodMessage() {
        return config.getString("anti-spam.flood.message", "&cYou are sending messages too quickly! Please slow down.");
    }

    /**
     * Gets actions to execute when flood spam is detected
     * @return List of actions/commands
     */
    @NotNull
    public List<String> getAntiSpamFloodActions() {
        return config.getStringList("anti-spam.flood.actions");
    }

    /**
     * Checks if console notifications for spam detection are enabled
     * @return true if console notifications are enabled
     */
    public boolean isAntiSpamConsoleNotifyEnabled() {
        return config.getBoolean("anti-spam.console-notify", true);
    }

    /**
     * Saves configuration to file
     * @throws RuntimeException if save fails
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config", e);
        }
    }

    // Reloads configuration from file
    public void reloadConfig() {
        loadConfig();
    }

    public String getString(String path) {
        return config.getString(path);
    }

    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }

    public boolean contains(String path) {
        return config.contains(path);
    }

    public ConfigurationSection getConfigurationSection(String path) {
        return config.getConfigurationSection(path);
    }

    public Set<String> getKeys(boolean deep) {
        return config.getKeys(deep);
    }

    /**
     * Reads a resource into a list of strings
     * @param resourcePath Resource path to read
     * @return List of resource lines
     * @throws IOException if resource cannot be read
     */
    private List<String> readResourceToList(String resourcePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream resource = plugin.getResource(resourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Processes file lines into FileLine objects
     * @param fileLines List of file lines
     * @return Map of position to FileLine
     */
    private HashMap<Integer, FileLine> processFileLines(List<String> fileLines) {
        HashMap<Integer, FileLine> processedLines = new HashMap<>();
        int positions = 1;

        for (int i = 0; i < fileLines.size(); i++) {
            String line = fileLines.get(i);
            if (isListContent(line)) continue;

            if (!line.isEmpty() && !isComment(line) && line.contains(":")) {
                String[] split = line.split(":");
                boolean isValue = split.length > 1 && !isComment(split[1]);
                boolean isHeader = split.length == 1 || isComment(split[1]);
                boolean isList = isHeader && i + 1 < fileLines.size() && isListContent(fileLines.get(i + 1));
                
                processedLines.put(positions, new FileLine(line, isValue, isHeader, isList));
                positions++;
                continue;
            }
            processedLines.put(positions, new FileLine(line, false, false, false));
            positions++;
        }
        return processedLines;
    }

    /**
     * Checks if the plugin was updated and updates configuration accordingly
     */
    private void updateConfigIfNeeded() {
        File savesFile = new File(plugin.getDataFolder(), "saves.yml");
        FileConfiguration savesConfig;
        
        if (!savesFile.exists()) {
            try {
                savesFile.getParentFile().mkdirs();
                savesFile.createNewFile();
                savesConfig = new YamlConfiguration();
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.WARNING, "[nonchat] Failed to create saves.yml: {0}", e.getMessage());
                return;
            }
        } else {
            savesConfig = YamlConfiguration.loadConfiguration(savesFile);
        }

        String currentVersion = savesConfig.getString("version");
        String pluginVersion = plugin.getDescription().getVersion();
        boolean isUpdated = pluginVersion.equals(currentVersion);



        // Only save if version changed
        if (currentVersion == null || !currentVersion.equals(pluginVersion)) {
            savesConfig.set("version", pluginVersion);
            try {
                savesConfig.save(savesFile);
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.WARNING, "[nonchat] Could not save saves.yml: {0}", e.getMessage());
            }
        }

        if (!isUpdated) {
            Bukkit.getLogger().log(Level.INFO, "[nonchat] Plugin version changed from {0} to {1}", 
                new Object[]{(currentVersion != null ? currentVersion : "unknown"), pluginVersion});
            Bukkit.getLogger().log(Level.INFO, "[nonchat] Checking for configuration updates...");
            setupConfigFile(true);
        } else {
            setupConfigFile(false);
        }
    }

    /**
     * Updates the config file with missing paths from the default configuration
     * Preserves user values and comments, adds only missing keys
     */
    private void setupConfigFile(boolean backup) {
        try {
            if (backup) {
                createBackup();
            }

            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            
            FileConfiguration defaultConfig;
            try (InputStream resourceStream = plugin.getResource("config.yml")) {
                if (resourceStream == null) {
                    Bukkit.getLogger().log(Level.WARNING, "[nonchat] Could not load default config.yml from plugin resources!");
                    return;
                }
                
                defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resourceStream, StandardCharsets.UTF_8)
                );
            }

            boolean hasChanges = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (currentConfig.contains(key)) continue;
                if (defaultConfig.isConfigurationSection(key)) continue;

                if (shouldAddMissingKey(key, currentConfig)) {
                    currentConfig.set(key, defaultConfig.get(key));
                    hasChanges = true;
                    Bukkit.getLogger().log(Level.INFO, "[nonchat] Added missing configuration key: {0}", key);
                }
            }

            if (hasChanges) {
                List<String> templateAsList = readResourceToList("config.yml");
                HashMap<Integer, FileLine> templateLines = processFileLines(templateAsList);

                StringBuilder builder = new StringBuilder();
                HashMap<Integer, String> headers = new HashMap<>();

                int templatePositions = templateLines.size() + 1;
                for (int pos = 1; pos < templatePositions; pos++) {
                    FileLine fileLine = templateLines.get(pos);
                    if (fileLine == null) continue;

                    String line = fileLine.getLine();
                    if (!fileLine.isValue() && !fileLine.isHeader() && !fileLine.isList()) {
                        builder.append(line).append("\n");
                        continue;
                    }

                    int spaces = 0;
                    for (char c : line.toCharArray()) {
                        if (c == ' ') spaces++;
                        else break;
                    }

                    int point = spaces / 2;
                    String identifier = line.substring(spaces).split(":")[0];
                    if (fileLine.isHeader()) headers.put(point, identifier);

                    if (fileLine.isValue()) {
                        StringBuilder path = new StringBuilder();
                        for (int i = 0; i <= point - 1; i++) {
                            String header = headers.get(i);
                            if (header != null) path.append(header).append(".");
                        }
                        path.append(identifier);

                        for (int i = 0; i < spaces; i++) builder.append(" ");
                        builder.append(identifier).append(": ");

                        Object value = currentConfig.get(path.toString());
                        if (value instanceof String string) {
                            String stringValue = string.replace("\n", "\\n");
                            builder.append("\"").append(stringValue).append("\"\n");
                        } else {
                            builder.append(value).append("\n");
                        }
                        continue;
                    }

                    if (fileLine.isList()) {
                        StringBuilder path = new StringBuilder();
                        for (int i = 0; i <= point - 1; i++) {
                            String header = headers.get(i);
                            if (header != null) path.append(header).append(".");
                        }
                        path.append(identifier);

                        for (int i = 0; i < spaces; i++) builder.append(" ");
                        builder.append(identifier).append(":");

                        List<String> value = currentConfig.getStringList(path.toString());
                        if (value.isEmpty()) builder.append(" []\n");
                        else {
                            builder.append("\n");
                            for (String listLine : value) {
                                for (int i = 0; i < spaces + 2; i++) builder.append(" ");
                                String escapedListLine = listLine.replace("\n", "\\n");
                                boolean useSingleQuotes = escapedListLine.contains("\\");
                                String quote = useSingleQuotes ? "'" : "\"";
                                builder.append("- ").append(quote).append(escapedListLine).append(quote).append("\n");
                            }
                        }
                        continue;
                    }

                    builder.append(line).append("\n");
                }

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                    writer.write(builder.toString());
                    writer.flush();
                }
                
                plugin.logResponse("Configuration updated successfully with new options");
                if (backup) {
                    plugin.logResponse("A backup of your previous configuration was created in the 'backups' folder.");
                }
            }

        } catch (IOException e) {
            plugin.logError("Failed to update configuration: " + e.getMessage());
        }
    }

    /**
     * Creates a backup of the current configuration file
     */
    private void createBackup() {
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String timestamp = dateFormat.format(new Date());
            File backupFile = new File(backupDir, "config_" + timestamp + ".yml.backup");

            try (BufferedReader reader = new BufferedReader(new FileReader(configFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(backupFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            
            plugin.logResponse("Created configuration backup: " + backupFile.getName());

        } catch (IOException e) {
            plugin.logError("Failed to create configuration backup: " + e.getMessage());
        }
    }

    /**
     * Determines if a missing key should be added based on intelligent section detection
     */
    private boolean shouldAddMissingKey(String key, FileConfiguration currentConfig) {
        if (!key.contains(".")) {
            return true;
        }

        String[] parts = key.split("\\.");
        String topLevelSection = parts[0];
        
        // For all other sections, add missing keys if it's a new feature section
        if (!hasAnyKeysInSection(currentConfig, topLevelSection)) {
            return true;
        }
        
        // If user has some keys in this section, add missing keys to complete it
        return true;
    }

    /**
     * Checks if the user has ANY configuration keys under a given section
     * This is more robust than maintaining a manual list
     */
    private boolean hasAnyKeysInSection(FileConfiguration config, String sectionName) {
        if (!config.contains(sectionName)) {
            return false;
        }
        
        Set<String> allKeys = config.getKeys(true);
        for (String existingKey : allKeys) {
            if (existingKey.startsWith(sectionName + ".") || existingKey.equals(sectionName)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Checks if a line is a comment (starts with #)
     */
    private boolean isComment(String s) {
        return s.replace(" ", "").startsWith("#");
    }

    /**
     * Checks if a line is list content (starts with -)
     */
    private boolean isListContent(String s) {
        return s.replace(" ", "").startsWith("-");
    }

    /**
     * Inner class to represent a line in the configuration file
     */
    private static class FileLine {
        private final String line;
        private final boolean isValue, isHeader, isList;

        public FileLine(String line, boolean isValue, boolean isHeader, boolean isList) {
            this.line = line;
            this.isValue = isValue;
            this.isHeader = isHeader;
            this.isList = isList;
        }

        public String getLine() {
            return line;
        }

        public boolean isValue() {
            return isValue;
        }

        public boolean isHeader() {
            return isHeader;
        }

        public boolean isList() {
            return isList;
        }
    }
}
