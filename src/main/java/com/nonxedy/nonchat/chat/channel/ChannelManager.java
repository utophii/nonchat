package com.nonxedy.nonchat.chat.channel;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.Channel;
import com.nonxedy.nonchat.api.ChannelAPI;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.util.AsyncConfigSaver;
import com.nonxedy.nonchat.util.chat.formatting.HoverTextUtil;

/**
 * Manages all chat channels in the nonchat plugin.
 */
public class ChannelManager {
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final Map<Player, Channel> playerChannels = new ConcurrentHashMap<>();
    private final Map<Player, Long> lastMessageTimes = new ConcurrentHashMap<>();
    private String defaultChannelId;
    private final PluginConfig config;
    private final AsyncConfigSaver asyncConfigSaver;

    public ChannelManager(Nonchat plugin, PluginConfig config) {
        this.config = config;
        this.asyncConfigSaver = new AsyncConfigSaver(plugin, config);
        loadChannels();
    }

    /**
     * Loads all channels from configuration.
     */
    public void loadChannels() {
        // Clear existing channels
        channels.clear();
        
        // Get the channels section from config
        ConfigurationSection channelsSection = config.getConfigurationSection("channels");
        if (channelsSection == null) {
            // Create default channels if none exist
            createDefaultChannels();
            return;
        }
        
        // Get default channel id
        this.defaultChannelId = config.getString("default-channel", "global");
        
        HoverTextUtil hoverTextUtil = config.getHoverTextUtil();
        
        // Load each channel
        for (String channelId : channelsSection.getKeys(false)) {
            ConfigurationSection channelSection = channelsSection.getConfigurationSection(channelId);
            if (channelSection == null) continue;
            
            boolean enabled = channelSection.getBoolean("enabled", true);
            String displayName = channelSection.getString("display-name", channelId);
            String format = channelSection.getString("format", "{prefix}{sender}{suffix}: {message}");
            String prefix = channelSection.getString("character", "");
            
            // Validate prefix
            if (prefix != null && !prefix.isEmpty()) {
                // Check for whitespace
                if (prefix.contains(" ")) {
                    Bukkit.getLogger().warning("Channel " + channelId + " has invalid prefix with whitespace. Prefix will be ignored.");
                    prefix = "";
                }
                // Check length (max 10 characters)
                else if (prefix.length() > 10) {
                    Bukkit.getLogger().warning("Channel " + channelId + " prefix too long (max 10 characters). Prefix will be truncated.");
                    prefix = prefix.substring(0, 10);
                }
            }
            
            String sendPermission = channelSection.getString("send-permission", "");
            String receivePermission = channelSection.getString("receive-permission", "");
            
            // Handle radius - can be numeric or "world"
            Object radiusObj = channelSection.get("radius");
            int radius = -1;
            String world = "";
            
            switch (radiusObj) {
                case String string -> {
                    String radiusStr = string.toLowerCase().trim();
                    if (radiusStr.equals("world")) {
                        radius = -2; // Use -2 to indicate world-specific (not global)
                        world = "world"; // Default world name, can be configured
                    } else {
                        try {
                            radius = Integer.parseInt(radiusStr);
                        } catch (NumberFormatException e) {
                            radius = -1; // Default to global if invalid
                        }
                    }
                }
                case Integer integer -> radius = integer;
                default -> {
                }
            }
            
            int cooldown = channelSection.getInt("cooldown", 0);
            int minLength = channelSection.getInt("min-length", 0);
            int maxLength = channelSection.getInt("max-length", 256);

            // Create and register channel
            Channel channel = new BaseChannel(
                channelId, displayName, format, prefix, sendPermission, receivePermission,
                radius, world, enabled, hoverTextUtil, cooldown, minLength, maxLength
            );
            
            if (config.isDebug()) {
                Bukkit.getLogger().log(Level.INFO, "Loaded channel: {0}, max-length: {1}", new Object[]{channelId, maxLength});
            }
            
            channels.put(channelId, channel);
        }
        
        // If no channels were loaded, create default ones
        if (channels.isEmpty()) {
            createDefaultChannels();
        }
    }
    
    /**
     * Creates default channels if none are configured.
     */
    private void createDefaultChannels() {
        HoverTextUtil hoverTextUtil = config.getHoverTextUtil();
        
        // Create global channel
        Channel globalChannel = new BaseChannel(
            "global", "Global", "§7(§6G§7)§r {prefix} §f{sender}§r {suffix}§7: §f{message}",
            "!", "", "", -1, true, hoverTextUtil, 0, 0, 256
        );
        channels.put("global", globalChannel);
        
        // Create local channel
        Channel localChannel = new BaseChannel(
            "local", "Local", "§7(§6L§7)§r {prefix} §f{sender}§r {suffix}§7: §f{message}",
            "", "", "", 100, true, hoverTextUtil, 0, 0, 256
        );
        channels.put("local", localChannel);
        
        // Set default channel
        this.defaultChannelId = "local";
        
        // Save default channels to config asynchronously
        saveChannelToConfig("global", globalChannel);
        saveChannelToConfig("local", localChannel);
        asyncConfigSaver.saveAsync("default-channel", defaultChannelId);
    }
    
    /**
     * Checks if a prefix is unique among all channels.
     * @param prefix The prefix to check
     * @param excludeChannelId Channel ID to exclude from the check (for updates), or null
     * @return true if the prefix is unique or empty, false if already in use
     */
    public boolean isPrefixUnique(String prefix, String excludeChannelId) {
        if (prefix == null || prefix.isEmpty()) {
            return true; // Empty prefixes are always valid
        }
        
        return channels.values().stream()
            .filter(channel -> excludeChannelId == null || !channel.getId().equals(excludeChannelId))
            .filter(Channel::hasPrefix)
            .noneMatch(channel -> channel.getPrefix().equals(prefix));
    }
    
    /**
     * Creates a new channel with the specified properties.
     * @param channelId The unique channel ID (must be lowercase letters, numbers and hyphens only)
     * @param displayName The display name for the channel
     * @param format The message format for the channel
     * @param prefix The trigger prefix, or null/empty for none
     * @param sendPermission Permission to send to this channel, or empty for everyone
     * @param receivePermission Permission to receive from this channel, or empty for everyone
     * @param radius Radius of the channel in blocks, or -1 for global
     * @param cooldown Cooldown between messages in seconds
     * @param minLength Minimum message length
     * @param maxLength Maximum message length, or -1 for unlimited
     * @return The created channel, or null if the ID already exists or prefix is invalid
     */
    public Channel createChannel(String channelId, String displayName, String format,
                                String prefix, String sendPermission, String receivePermission,
                                int radius, int cooldown, int minLength, int maxLength) {
        // Check if channel already exists
        if (channels.containsKey(channelId)) {
            return null;
        }
        
        // Sanitize inputs
        channelId = channelId.toLowerCase();
        if (!channelId.matches("^[a-z0-9-]+$")) {
            return null; // Invalid channel ID
        }
        
        // Validate prefix
        if (prefix != null && !prefix.isEmpty()) {
            // Check for whitespace
            if (prefix.contains(" ")) {
                return null; // Invalid prefix with whitespace
            }
            // Check length (max 10 characters)
            if (prefix.length() > 10) {
                return null; // Prefix too long
            }
            // Check uniqueness
            if (!isPrefixUnique(prefix, null)) {
                return null; // Prefix already in use
            }
        }
        
        // Normalize null prefix to empty string
        if (prefix == null) {
            prefix = "";
        }
        
        // Create the channel
        Channel channel = new BaseChannel(
            channelId,
            displayName,
            format,
            prefix,
            sendPermission,
            receivePermission,
            radius,
            "", // Default empty world
            true, // Enabled by default
            config.getHoverTextUtil(),
            cooldown,
            minLength,
            maxLength
        );
        
        // Add to channels map
        channels.put(channelId, channel);
        
        // Save to config asynchronously
        saveChannelToConfig(channelId, channel);
        asyncConfigSaver.saveNow();
        
        return channel;
    }
    
    /**
     * Updates an existing channel with new properties, including Discord channel ID and webhook.
     * @param channelId The channel ID to update
     * @param displayName The display name for the channel (null to keep existing)
     * @param format The message format for the channel (null to keep existing)
     * @param prefix The trigger prefix (null to keep existing, empty string to remove)
     * @param sendPermission Permission to send to this channel (null to keep existing)
     * @param receivePermission Permission to receive from this channel (null to keep existing)
     * @param radius Radius of the channel in blocks (-1 for global, null to keep existing)
     * @param enabled Whether the channel is enabled (null to keep existing)
     * @param cooldown Cooldown between messages in seconds (null to keep existing)
     * @param minLength Minimum message length (null to keep existing)
     * @param maxLength Maximum message length (null to keep existing)
     * @return True if the channel was updated, false otherwise
     */
    public boolean updateChannel(String channelId, String displayName, String format,
                                String prefix, String sendPermission, String receivePermission,
                                Integer radius, Boolean enabled, Integer cooldown, 
                                Integer minLength, Integer maxLength) {
        // Get existing channel
        Channel existingChannel = getChannel(channelId);
        if (existingChannel == null) {
            return false;
        }
        
        // Determine the prefix to use
        String finalPrefix = prefix != null ? prefix : existingChannel.getPrefix();
        
        // Validate prefix if it's being changed
        if (prefix != null && !prefix.isEmpty()) {
            // Check for whitespace
            if (prefix.contains(" ")) {
                return false; // Invalid prefix with whitespace
            }
            // Check length (max 10 characters)
            if (prefix.length() > 10) {
                return false; // Prefix too long
            }
            // Check uniqueness (excluding current channel)
            if (!isPrefixUnique(prefix, channelId)) {
                return false; // Prefix already in use by another channel
            }
        }
        
        // Since we can't modify the existing channel directly (it's immutable), 
        // we create a new one with updated properties
        
        Channel updatedChannel = new BaseChannel(
            channelId,
            displayName != null ? displayName : existingChannel.getDisplayName(),
            format != null ? format : existingChannel.getFormat(),
            finalPrefix,
            sendPermission != null ? sendPermission : existingChannel.getSendPermission(),
            receivePermission != null ? receivePermission : existingChannel.getReceivePermission(),
            radius != null ? radius : existingChannel.getRadius(),
            existingChannel.getWorld(), // Keep existing world
            enabled != null ? enabled : existingChannel.isEnabled(),
            config.getHoverTextUtil(),
            cooldown != null ? cooldown : existingChannel.getCooldown(),
            minLength != null ? minLength : existingChannel.getMinLength(),
            maxLength != null ? maxLength : existingChannel.getMaxLength()
        );
        
        // Replace in channels map
        channels.put(channelId, updatedChannel);
        
        // Save to config asynchronously
        saveChannelToConfig(channelId, updatedChannel);
        asyncConfigSaver.saveNow();
        
        return true;
    }
    
    /**
     * Deletes a channel.
     * @param channelId The channel ID to delete
     * @return True if the channel was deleted, false otherwise
     */
    public boolean deleteChannel(String channelId) {
        // Can't delete if it's the default channel
        if (channelId.equals(defaultChannelId)) {
            return false;
        }

        // Check if channel exists
        if (!channels.containsKey(channelId)) {
            return false;
        }

        // Clean up API registrations before removing the channel
        ChannelAPI.cleanupChannel(channelId);

        // Remove from channels map
        channels.remove(channelId);

        // Remove from config asynchronously
        asyncConfigSaver.saveAsync("channels." + channelId, null);
        asyncConfigSaver.saveNow();

        // Switch any players using this channel to the default
        Channel defaultChannel = getDefaultChannel();
        for (Player player : playerChannels.keySet()) {
            if (playerChannels.get(player).getId().equals(channelId)) {
                playerChannels.put(player, defaultChannel);
            }
        }

        return true;
    }
    
    /**
     * Sets a new default channel.
     * @param channelId The channel ID to set as default
     * @return True if successful, false if channel doesn't exist or isn't enabled
     */
    public boolean setDefaultChannel(String channelId) {
        Channel channel = getChannel(channelId);
        if (channel == null || !channel.isEnabled()) {
            return false;
        }
        
        this.defaultChannelId = channelId;
        asyncConfigSaver.saveAsync("default-channel", channelId);
        asyncConfigSaver.saveNow();
        
        return true;
    }
    
    /**
     * Saves a channel's configuration to the config file.
     * @param channelId The channel ID
     * @param channel The channel to save
     */
    private void saveChannelToConfig(String channelId, Channel channel) {
        String basePath = "channels." + channelId + ".";
        asyncConfigSaver.saveAsync(basePath + "enabled", channel.isEnabled());
        asyncConfigSaver.saveAsync(basePath + "display-name", channel.getDisplayName());
        asyncConfigSaver.saveAsync(basePath + "format", channel.getFormat());
        asyncConfigSaver.saveAsync(basePath + "character", channel.hasPrefix() ? channel.getPrefix() : "");
        asyncConfigSaver.saveAsync(basePath + "send-permission", channel.getSendPermission());
        asyncConfigSaver.saveAsync(basePath + "receive-permission", channel.getReceivePermission());

        // Save radius - use "world" string for world-specific channels
        if (channel.isWorldSpecific()) {
            asyncConfigSaver.saveAsync(basePath + "radius", "world");
        } else {
            asyncConfigSaver.saveAsync(basePath + "radius", channel.getRadius());
        }

        asyncConfigSaver.saveAsync(basePath + "cooldown", channel.getCooldown());
        asyncConfigSaver.saveAsync(basePath + "min-length", channel.getMinLength());
        asyncConfigSaver.saveAsync(basePath + "max-length", channel.getMaxLength());
    }

    /**
     * Gets a channel by its ID.
     * @param id The channel ID
     * @return The channel, or null if not found
     */
    @Nullable
    public Channel getChannel(String id) {
        return channels.get(id);
    }
    
    /**
     * Gets the default channel.
     * @return The default channel
     */
    @NotNull
    public Channel getDefaultChannel() {
        Channel defaultChannel = channels.get(defaultChannelId);
        if (defaultChannel == null) {
            // If default channel doesn't exist, return the first available channel
            Optional<Channel> any = channels.values().stream().findFirst();
            return any.orElseThrow(() -> new IllegalStateException("No channels available"));
        }
        return defaultChannel;
    }
    
    /**
     * Gets all channels.
     * @return Collection of all channels
     */
    @NotNull
    public Collection<Channel> getAllChannels() {
        return channels.values();
    }
    
    /**
     * Gets all enabled channels.
     * @return Collection of enabled channels
     */
    @NotNull
    public Collection<Channel> getEnabledChannels() {
        return channels.values().stream()
                .filter(Channel::isEnabled)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all global channels.
     * @return Collection of global channels
     */
    @NotNull
    public Collection<Channel> getGlobalChannels() {
        return channels.values().stream()
                .filter(Channel::isGlobal)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all local channels.
     * @return Collection of local channels
     */
    @NotNull
    public Collection<Channel> getLocalChannels() {
        return channels.values().stream()
                .filter(channel -> !channel.isGlobal())
                .collect(Collectors.toList());
    }
    
    /**
     * Gets channels that the player can send messages to.
     * @param player The player to check permissions for
     * @return Collection of channels the player can use
     */
    @NotNull
    public Collection<Channel> getChannelsForPlayer(Player player) {
        return channels.values().stream()
                .filter(Channel::isEnabled)
                .filter(channel -> channel.canSend(player))
                .collect(Collectors.toList());
    }
    
    /**
     * Determines the channel for a message based on its prefix.
     * If multiple channels have prefixes that match the message start,
     * the channel with the longest matching prefix is selected.
     * @param message The message to check
     * @return The appropriate channel, or null if no prefix match found
     */
    @Nullable
    public Channel getChannelForMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        // Find all channels whose prefix matches the start of the message
        // Sort by prefix length (longest first) to prioritize longer prefixes
        return channels.values().stream()
            .filter(Channel::isEnabled)
            .filter(Channel::hasPrefix)
            .filter(channel -> message.startsWith(channel.getPrefix()))
            .sorted((c1, c2) -> Integer.compare(c2.getPrefix().length(), c1.getPrefix().length()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Finds a channel without a prefix (empty prefix channel).
     * If multiple channels have no prefix, returns the first enabled one found.
     * @return The channel without prefix, or null if none found
     */
    @Nullable
    public Channel getChannelWithoutPrefix() {
        return channels.values().stream()
            .filter(Channel::isEnabled)
            .filter(channel -> !channel.hasPrefix())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Determines the channel for a message based on its prefix, checking player permissions first.
     * If multiple channels have prefixes that match the message start, the channel with the 
     * longest matching prefix is selected, but only if the player has permission to send to it.
     * @param message The message to check
     * @param player The player sending the message
     * @return The appropriate channel the player has permission for, or null if no match found
     */
    @Nullable
    public Channel getChannelForMessageWithPermission(String message, Player player) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        // Find all channels whose prefix matches the start of the message
        // Filter by player permission (canSend)
        // Sort by prefix length (longest first) to prioritize longer prefixes
        return channels.values().stream()
            .filter(Channel::isEnabled)
            .filter(Channel::hasPrefix)
            .filter(channel -> message.startsWith(channel.getPrefix()))
            .filter(channel -> channel.canSend(player))
            .sorted((c1, c2) -> Integer.compare(c2.getPrefix().length(), c1.getPrefix().length()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Finds a channel without a prefix that the player has permission to send to.
     * @param player The player to check permissions for
     * @return The channel without prefix that player can use, or null if none found
     */
    @Nullable
    public Channel getChannelWithoutPrefixForPlayer(Player player) {
        return channels.values().stream()
            .filter(Channel::isEnabled)
            .filter(channel -> !channel.hasPrefix())
            .filter(channel -> channel.canSend(player))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Finds a channel by its trigger prefix.
     * @param prefix The prefix to search for
     * @return Optional containing the channel, or empty if not found
     */
    public Optional<Channel> findChannelByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return Optional.empty();
        }
        
        return channels.values().stream()
            .filter(Channel::isEnabled)
            .filter(Channel::hasPrefix)
            .filter(channel -> channel.getPrefix().equals(prefix))
            .findFirst();
    }
    
    /**
     * Sets a player's active channel.
     * @param player The player
     * @param channelId The channel ID
     * @return true if successful, false if channel not found or not enabled
     */
    public boolean setPlayerChannel(Player player, String channelId) {
        Channel channel = getChannel(channelId);
        
        if (channel != null && channel.isEnabled()) {
            playerChannels.put(player, channel);
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets a player's active channel.
     * @param player The player
     * @return The player's channel, or the default if none set
     */
    @NotNull
    public Channel getPlayerChannel(Player player) {
        return playerChannels.getOrDefault(player, getDefaultChannel());
    }
    
    /**
     * Removes a player's channel setting.
     * @param player The player to remove
     */
    public void removePlayerChannel(Player player) {
        playerChannels.remove(player);
    }
    
    /**
     * Cleans up player data when they disconnect.
     * @param player The player who disconnected
     */
    public void cleanupPlayer(Player player) {
        playerChannels.remove(player);
        lastMessageTimes.remove(player);
    }
    
    /**
     * Records when a player sends a message for cooldown tracking.
     * @param player The player
     */
    public void recordMessageSent(Player player) {
        // Clean up old entries to prevent memory leaks
        lastMessageTimes.entrySet().removeIf(entry -> !entry.getKey().isOnline());
        
        lastMessageTimes.put(player, System.currentTimeMillis());
    }
    
    /**
     * Checks if a player can send a message based on cooldown.
     * @param player The player to check
     * @param channel The channel to check
     * @return true if player can send a message, false if on cooldown
     */
    public boolean canSendMessage(Player player, Channel channel) {
        if (channel.getCooldown() <= 0 || player.hasPermission("nonchat.bypass.cooldown")) {
            return true;
        }
        
        Long lastMessageTime = lastMessageTimes.get(player);
        if (lastMessageTime == null) {
            return true;
        }
        
        long cooldownMillis = channel.getCooldown() * 1000L;
        long timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime;
        
        return timeSinceLastMessage >= cooldownMillis;
    }
    
    /**
     * Gets the remaining cooldown time for a player in a channel.
     * @param player The player
     * @param channel The channel
     * @return Remaining cooldown in seconds, 0 if no cooldown
     */
    public int getRemainingCooldown(Player player, Channel channel) {
        if (channel.getCooldown() <= 0 || player.hasPermission("nonchat.bypass.cooldown")) {
            return 0;
        }
        
        Long lastMessageTime = lastMessageTimes.get(player);
        if (lastMessageTime == null) {
            return 0;
        }
        
        long cooldownMillis = channel.getCooldown() * 1000L;
        long timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime;
        
        if (timeSinceLastMessage >= cooldownMillis) {
            return 0;
        }
        
        // Calculate remaining seconds and add 1 to avoid "wait 0 seconds" message
        // This ensures we show at least 1 second when there's still some cooldown remaining
        int remainingSeconds = (int) ((cooldownMillis - timeSinceLastMessage) / 1000);
        return remainingSeconds > 0 ? remainingSeconds : 1;
    }
}
