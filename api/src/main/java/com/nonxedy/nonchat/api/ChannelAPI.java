package com.nonxedy.nonchat.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * API for interacting with the nonchat plugin's channel system.
 * This class provides static methods for other plugins to access and
 * manipulate the chat channels.
 */
public class ChannelAPI {
    private static ChannelAccess access;

    public static void initialize(ChannelAccess instance) {
        access = instance;
    }

    /**
     * Gets all available chat channels
     * @return Collection of all channels, or an empty collection if the API is not initialized
     */
    public static Collection<Channel> getAllChannels() {
        return access == null ? Collections.emptyList() : access.getAllChannels();
    }

    // Maps to store message processors and filters
    private static final Map<String, List<MessageProcessor>> channelProcessors = new HashMap<>();
    private static final Map<String, List<MessageFilter>> channelFilters = new HashMap<>();

    /**
     * Gets a channel by its ID
     * @param channelId The channel ID
     * @return The channel, or null if not found or the API is not initialized
     */
    public static Channel getChannel(String channelId) {
        return access == null ? null : access.getChannel(channelId);
    }

    /**
     * Gets a channel by its ID wrapped in an Optional
     * @param channelId The channel ID
     * @return Optional containing the channel, or empty if not found
     */
    public static Optional<Channel> getChannelOptional(String channelId) {
        return Optional.ofNullable(getChannel(channelId));
    }

    /**
     * Gets the current channel of a player
     * @param player The player
     * @return The player's current channel, or null if the API is not initialized
     */
    public static Channel getPlayerChannel(Player player) {
        return access == null ? null : access.getPlayerChannel(player);
    }

    /**
     * Checks if a message is meant for a specific channel
     * @param message The message to check
     * @param channelId The channel ID to check against
     * @return true if the message belongs to the channel
     */
    public static boolean isMessageForChannel(String message, String channelId) {
        return getChannelOptional(channelId)
            .filter(Channel::isEnabled)
            .filter(Channel::hasPrefix)
            .map(channel -> message.startsWith(channel.getPrefix()))
            .orElse(false);
    }

    /**
     * Finds channels that match the given predicate
     * @param predicate The condition to match
     * @return A list of channels that match the condition
     */
    public static List<Channel> findChannels(Predicate<Channel> predicate) {
        return getAllChannels().stream()
            .filter(predicate)
            .collect(Collectors.toList());
    }

    /**
     * Finds the first channel that matches the given predicate
     * @param predicate The condition to match
     * @return Optional containing the first matching channel, or empty if none found
     */
    public static Optional<Channel> findChannel(Predicate<Channel> predicate) {
        return getAllChannels().stream()
            .filter(predicate)
            .findFirst();
    }

    /**
     * Gets all enabled channels
     * @return List of enabled channels
     */
    public static List<Channel> getEnabledChannels() {
        return getAllChannels().stream()
            .filter(Channel::isEnabled)
            .collect(Collectors.toList());
    }

    /**
     * Gets all global channels
     * @return List of global channels
     */
    public static List<Channel> getGlobalChannels() {
        return getAllChannels().stream()
            .filter(Channel::isGlobal)
            .collect(Collectors.toList());
    }

    /**
     * Gets channels accessible to the player (can send messages)
     * @param player The player to check
     * @return List of channels the player can use
     */
    public static List<Channel> getAccessibleChannels(Player player) {
        return getAllChannels().stream()
            .filter(Channel::isEnabled)
            .filter(channel -> channel.canSend(player))
            .collect(Collectors.toList());
    }

    /**
     * Gets players who can access the specified channel
     * @param channelId The channel ID
     * @return List of players who can access the channel
     */
    public static List<Player> getPlayersWithAccess(String channelId) {
        Channel channel = getChannel(channelId);
        if (channel == null) {
            return Collections.emptyList();
        }
        
        return Bukkit.getOnlinePlayers().stream()
            .filter(player -> channel.canSend(player) || channel.canReceive(player))
            .collect(Collectors.toList());
    }

    /**
     * Registers a message processor for a specific channel
     * @param channelId The channel ID
     * @param processor The message processor
     */
    public static void registerMessageProcessor(String channelId, MessageProcessor processor) {
        Objects.requireNonNull(processor, "Processor cannot be null");
        
        channelProcessors.computeIfAbsent(channelId, k -> new ArrayList<>()).add(processor);
    }

    /**
     * Unregisters a message processor from a specific channel
     * @param channelId The channel ID
     * @param processor The message processor to remove
     * @return true if the processor was removed, false if it wasn't registered
     */
    public static boolean unregisterMessageProcessor(String channelId, MessageProcessor processor) {
        if (!channelProcessors.containsKey(channelId)) {
            return false;
        }
        
        List<MessageProcessor> processors = channelProcessors.get(channelId);
        return processors.remove(processor);
    }

    /**
     * Gets all message processors registered for a channel
     * @param channelId The channel ID
     * @return List of processors (could be empty)
     */
    public static List<MessageProcessor> getMessageProcessors(String channelId) {
        return channelProcessors.getOrDefault(channelId, Collections.emptyList());
    }

    /**
     * Registers a message filter for a specific channel
     * @param channelId The channel ID
     * @param filter The message filter
     */
    public static void registerMessageFilter(String channelId, MessageFilter filter) {
        Objects.requireNonNull(filter, "Filter cannot be null");
        
        channelFilters.computeIfAbsent(channelId, k -> new ArrayList<>()).add(filter);
    }

    /**
     * Unregisters a message filter from a specific channel
     * @param channelId The channel ID
     * @param filter The message filter to remove
     * @return true if the filter was removed, false if it wasn't registered
     */
    public static boolean unregisterMessageFilter(String channelId, MessageFilter filter) {
        if (!channelFilters.containsKey(channelId)) {
            return false;
        }
        
        List<MessageFilter> filters = channelFilters.get(channelId);
        return filters.remove(filter);
    }

    /**
     * Gets all message filters registered for a channel
     * @param channelId The channel ID
     * @return List of filters (could be empty)
     */
    public static List<MessageFilter> getMessageFilters(String channelId) {
        return channelFilters.getOrDefault(channelId, Collections.emptyList());
    }

    /**
     * Processes a message through all registered processors for a channel
     * @param player The player sending the message
     * @param message The original message
     * @param channelId The channel ID
     * @return The processed message, or null if the message should be cancelled
     */
    public static String processMessage(Player player, String message, String channelId) {
        String result = message;
        
        for (MessageProcessor processor : getMessageProcessors(channelId)) {
            result = processor.process(player, result);
            if (result == null) {
                return null; // Message cancelled
            }
        }
        
        return result;
    }

    /**
     * Checks if a message should be filtered based on registered filters
     * @param player The player sending the message
     * @param message The message to check
     * @param channelId The channel ID
     * @return true if the message should be filtered, false otherwise
     */
    public static boolean shouldFilterMessage(Player player, String message, String channelId) {
        return getMessageFilters(channelId).stream()
            .anyMatch(filter -> filter.shouldFilter(player, message));
    }

    /**
     * Cleans up all registered processors and filters for a specific channel
     * Should be called when a channel is deleted
     * @param channelId The channel ID to clean up
     */
    public static void cleanupChannel(String channelId) {
        channelProcessors.remove(channelId);
        channelFilters.remove(channelId);
    }

    /**
     * Cleans up all registered processors and filters
     * Should be called when the plugin is disabled
     */
    public static void cleanupAll() {
        channelProcessors.clear();
        channelFilters.clear();
        access = null;
    }

    public interface ChannelAccess {
        Collection<Channel> getAllChannels();

        Channel getChannel(String channelId);

        Channel getPlayerChannel(Player player);
    }
}
