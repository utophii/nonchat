package com.nonxedy.nonchat.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    /**
     * Default mention notification: a plain chat message, matching the value shipped
     * in {@code config.yml}.
     */
    public static final String DEFAULT_MENTION_MESSAGE =
            "message:&#ffffffYou were mentioned in chat by &#84FFB8{player}!";

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
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
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
            try (InputStream stream = plugin.getResource("config.yml")) {
                if (stream == null) throw new IOException("Missing bundled config.yml");
                config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            // Save the configuration to file
            saveConfig();
        } catch (IOException e) {
            // Throw runtime exception if config creation fails
            throw new RuntimeException("Failed to create default config", e);
        }
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
        return config.getBoolean("mention-sounds.enabled", true) && !getMentionSound().isEmpty();
    }

    /**
     * Gets mention sound name
     * @return Mention sound name
     */
    @NotNull
    public String getMentionSound() {
        String sound = config.getString("mention-sounds.sound", "entity.experience_orb.pickup");
        return sound == null ? "" : sound.trim();
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
     * Checks if the mention system (coloring, sound, notification, tab-completion)
     * is enabled globally. When set to false all @mention behaviour is disabled,
     * including coloring, sound, notification popup, and @-name tab completion.
     * @return true if the mention system is enabled
     */
    public boolean isMentionsEnabled() {
        return config.getBoolean("mentions.enabled", true);
    }

    /**
     * Sets the global mention system enabled state
     * @param enabled New enabled state
     */
    public void setMentionsEnabled(boolean enabled) {
        config.set("mentions.enabled", enabled);
        saveConfig();
    }

    /**
     * Checks if players can be mentioned by typing their name without the '@' prefix
     * @return true if bare player names should also trigger mentions
     */
    public boolean isMentionWithoutAtEnabled() {
        return config.getBoolean("mentions.allow-without-at", false);
    }

    /**
     * Gets the mention notification definition ({@code mentions.mention-message}).
     *
     * <p>The value is written as {@code <type>:<text>} where the type is one of
     * {@code message}, {@code actionbar}, {@code title} or {@code bossbar}, for
     * example {@code title:&#84FFB8You were mentioned!}. An empty value - or a value
     * without one of those types - means the mentioned player is not notified at all
     * (sounds are configured separately in {@code mention-sounds}). See
     * {@link com.nonxedy.nonchat.util.special.mention.MentionNotification} for the
     * full syntax, including per-type options.</p>
     *
     * @return raw notification definition, empty when notifications are disabled
     */
    @NotNull
    public String getMentionMessage() {
        String value = config.getString("mentions.mention-message", DEFAULT_MENTION_MESSAGE);
        return value == null ? "" : value.trim();
    }

    /**
     * Checks if private messaging (/msg, /reply) is enabled
     * @return true if enabled
     */
    public boolean isPrivateChatEnabled() {
        return config.getBoolean("private-chat.enabled", true);
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
     * Gets the reply command for the receiver's private message.
     * When the receiver clicks the message, this command is inserted.
     * Defaults to /msg {sender} so the receiver replies to the sender.
     * @return Reply command template
     */
    @NotNull
    public String getPrivateChatReceiverReplyCommand() {
        return config.getString("private-chat.click-actions.receiver-reply-command", "/msg {sender} ");
    }

    /**
     * Gets the reply command for the sender's own private message.
     * When the sender clicks their own message, this command is inserted.
     * Defaults to /msg {receiver} so the sender replies to the receiver.
     * @return Reply command template
     */
    @NotNull
    public String getPrivateChatSenderReplyCommand() {
        return config.getString("private-chat.click-actions.sender-reply-command", "/msg {receiver} ");
    }

    /**
     * Checks if private message sounds are enabled
     * @return true if private message sound is configured
     */
    public boolean isPrivateMessageSoundEnabled() {
        return !getPrivateMessageSound().isEmpty();
    }

    /**
     * Gets private message sound name
     * @return Private message sound name
     */
    @NotNull
    public String getPrivateMessageSound() {
        String sound = config.getString("private-chat.sound", "");
        return sound == null ? "" : sound.trim();
    }

    /**
     * Gets private message sound volume
     * @return Private message sound volume (0.0 to 1.0)
     */
    public float getPrivateMessageSoundVolume() {
        return (float) config.getDouble("private-chat.sound-volume", 1.0);
    }

    /**
     * Gets private message sound pitch
     * @return Private message sound pitch (0.5 to 2.0)
     */
    public float getPrivateMessageSoundPitch() {
        return (float) config.getDouble("private-chat.sound-pitch", 1.0);
    }

    /**
     * Checks whether private messages are restricted to configured world groups
     *
     * @return true when world separation is enabled
     */
    public boolean isPrivateChatWorldSeparationEnabled() {
        return config.getBoolean("private-chat.world-separation.enabled", false);
    }

    /**
     * Checks whether two world names belong to the same private-message scope.
     * Worlds in the same configured group can communicate. Worlds that are not
     * in a group can communicate only with that exact world
     *
     * @param firstWorld first world name
     * @param secondWorld second world name
     * @return true when private messaging is allowed between the worlds
     */
    public boolean canPrivateMessageBetweenWorlds(String firstWorld, String secondWorld) {
        if (!isPrivateChatWorldSeparationEnabled()) {
            return true;
        }
        if (firstWorld == null || secondWorld == null) {
            return false;
        }
        if (firstWorld.equalsIgnoreCase(secondWorld)) {
            return true;
        }

        ConfigurationSection groups = config.getConfigurationSection("private-chat.world-separation.groups");
        if (groups == null) {
            return false;
        }

        for (String groupName : groups.getKeys(false)) {
            List<String> worlds = groups.getStringList(groupName);
            boolean containsFirst = worlds.stream().anyMatch(world -> world.equalsIgnoreCase(firstWorld));
            boolean containsSecond = worlds.stream().anyMatch(world -> world.equalsIgnoreCase(secondWorld));
            if (containsFirst && containsSecond) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether two players can exchange private messages under the
     * configured world-separation policy
     *
     * @param sender private-message sender
     * @param receiver private-message receiver
     * @return true when the players are in the same private-message scope
     */
    public boolean canPrivateMessage(org.bukkit.entity.Player sender, org.bukkit.entity.Player receiver) {
        return sender != null && receiver != null
                && canPrivateMessageBetweenWorlds(sender.getWorld().getName(), receiver.getWorld().getName());
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
     * Checks if broadcast sounds are enabled
     * @return true if broadcast sound is configured
     */
    public boolean isBroadcastSoundEnabled() {
        return !getBroadcastSound().isEmpty();
    }

    /**
     * Gets broadcast sound name
     * @return Broadcast sound name
     */
    @NotNull
    public String getBroadcastSound() {
        String sound = config.getString("broadcast.sound", "");
        return sound == null ? "" : sound.trim();
    }

    /**
     * Gets broadcast sound volume
     * @return Broadcast sound volume (0.0 to 1.0)
     */
    public float getBroadcastSoundVolume() {
        return (float) config.getDouble("broadcast.sound-volume", 1.0);
    }

    /**
     * Gets broadcast sound pitch
     * @return Broadcast sound pitch (0.5 to 2.0)
     */
    public float getBroadcastSoundPitch() {
        return (float) config.getDouble("broadcast.sound-pitch", 1.0);
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
     * Gets the command suggested when a player clicks a name in chat.
     * @return Click command template
     */
    @NotNull
    public String getHoverTextClickCommand() {
        return config.getString("hover-text.click-command", "/m %player_name% ");
    }

    /**
     * Gets hover text utility instance
     * @return Configured HoverTextUtil
     */
    public HoverTextUtil getHoverTextUtil() {
        return new HoverTextUtil(getHoverFormat(), isHoverEnabled(), getHoverTextClickCommand());
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
     * Records the running plugin version and adds keys that are missing from config.yml
     */
    private void updateConfigIfNeeded() {
        File savesFile = new File(plugin.getDataFolder(), "saves.yml");
        FileConfiguration savesConfig = YamlConfiguration.loadConfiguration(savesFile);

        String currentVersion = savesConfig.getString("version");
        String pluginVersion = plugin.getPluginMeta().getVersion();

        if (!pluginVersion.equals(currentVersion)) {
            Bukkit.getLogger().log(Level.INFO, "[nonchat] Plugin version changed from {0} to {1}",
                new Object[]{(currentVersion != null ? currentVersion : "unknown"), pluginVersion});
            savesConfig.set("version", pluginVersion);
            try {
                savesConfig.save(savesFile);
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.WARNING, "[nonchat] Could not save saves.yml: {0}", e.getMessage());
            }
        }

        ConfigUpdater.updateFile(plugin, configFile, "config.yml", null);
    }
}
