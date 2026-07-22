package com.nonxedy.nonchat.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.Channel;
import com.nonxedy.nonchat.api.ChannelAPI;
import com.nonxedy.nonchat.chat.channel.ChannelManager;
import com.nonxedy.nonchat.chat.channel.ResolvedChannelMessage;
import com.nonxedy.nonchat.command.impl.IgnoreCommand;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.util.chat.filters.AdDetector;
import com.nonxedy.nonchat.util.chat.filters.CapsFilter;
import com.nonxedy.nonchat.util.chat.filters.SpamDetector;
import com.nonxedy.nonchat.util.chat.filters.WordBlocker;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;

public class ChatManager {

    private final Nonchat plugin;
    private final PluginConfig config;
    private final PluginMessages messages;
    private final ChannelManager channelManager;
    private final Pattern atMentionPattern = Pattern.compile("@(\\w+)");
    private final Pattern bareMentionPattern = Pattern.compile("@?\\b(\\w+)\\b");
    private final Map<Player, List<?>> bubbles = new ConcurrentHashMap<>();
    private final Map<Player, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private IgnoreCommand ignoreCommand;
    private final AdDetector adDetector;
    private final SpamDetector spamDetector;

    public ChatManager(Nonchat plugin, PluginConfig config, PluginMessages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.adDetector = new AdDetector(config, config.getAntiAdSensitivity(), config.getAntiAdPunishCommand());
        this.spamDetector = new SpamDetector(config, messages);
        this.channelManager = new ChannelManager(plugin, config);
        this.ignoreCommand = plugin.getIgnoreCommand();
        startBubbleUpdater();
    }

    // Processes a player message on the server thread
    public void processChat(Player player, String messageContent) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> processChat(player, messageContent));
            return;
        }
        // The player may have disconnected before an async chat event reached
        // the server thread.
        if (!player.isOnline()) {
            return;
        }

        // Log incoming message
        plugin.logChatMessage("Incoming: Player=" + player.getName() + " Message=\"" + messageContent + "\"");

        // Get or create player-specific lock
        ReentrantLock lock = playerLocks.computeIfAbsent(player, p -> new ReentrantLock());
        lock.lock();
        try {
            ChatProcessingContext context = new ChatProcessingContext(player, messageContent);

            // Basic validation
            if (!validateBasicMessage(context)) {
                return;
            }

            // Apply filters
            if (!applyFilters(context)) {
                plugin.logChatMessage("Filtered: Player=" + player.getName() + " Message=\"" + messageContent + "\" Reason=filter_blocked");
                return;
            }

            // Handle channel logic
            if (!handleChannelLogic(context)) {
                return;
            }

            // Validate channel permissions and limits
            if (!validateChannelAccess(context)) {
                plugin.logChatMessage("Filtered: Player=" + player.getName() + " Message=\"" + messageContent + "\" Reason=channel_access_denied");
                return;
            }

            // Process message through API
            if (!processMessageThroughAPI(context)) {
                plugin.logChatMessage("Filtered: Player=" + player.getName() + " Message=\"" + messageContent + "\" Reason=api_blocked");
                return;
            }

            // Handle mentions
            handleMentions(player, context.processedMessage);

            // Format and broadcast
            broadcastProcessedMessage(context);

            // Log delivered message
            plugin.logChatMessage("Delivered: Player=" + player.getName() + " Channel=" + context.channel.getId() + " Message=\"" + context.finalMessage + "\"");

            // Handle bubble creation if needed
            handleBubbleCreation(context);

        } finally {
            lock.unlock();

            // Clean up lock if player is offline
            if (!player.isOnline()) {
                playerLocks.remove(player);
            }
        }
    }

    /**
     * Context class to hold chat processing state
     */
    private static class ChatProcessingContext {
        final Player player;
        String messageContent;
        Channel channel;
        String finalMessage;
        String processedMessage;
        boolean messageDelivered;

        ChatProcessingContext(Player player, String messageContent) {
            this.player = player;
            this.messageContent = messageContent;
        }
    }

    private boolean validateBasicMessage(ChatProcessingContext context) {
        // Check if the message is empty or contains only whitespace
        if (context.messageContent == null || context.messageContent.trim().isEmpty()) {
            return false; // Silently cancel empty messages
        }
        return true;
    }

    private boolean applyFilters(ChatProcessingContext context) {
        Player player = context.player;
        String message = context.messageContent;

        // Check blocked words
        if (handleBlockedWords(player, message)) {
            return false;
        }

        // Check caps filter
        CapsFilter capsFilter = config.getCapsFilter();
        if (!player.hasPermission("nonchat.caps.bypass") && capsFilter.shouldFilter(message)) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("caps-filter")
                    .replace("{percentage}", String.valueOf(capsFilter.getMaxCapsPercentage()))));
            return false;
        }

        // Check spam
        if (config.isAntiSpamEnabled() && !player.hasPermission("nonchat.spam.bypass")) {
            if (spamDetector.shouldFilter(player, message)) {
                return false;
            }
        }

        // Check advertisements
        if (config.isAntiAdEnabled() && !player.hasPermission("nonchat.ad.bypass")) {
            if (adDetector.shouldFilter(player, message)) {
                MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("blocked-words")));
                return false;
            }
        }

        // Handle color permissions
        if (!player.hasPermission("nonchat.color") && ColorUtil.hasColorCodes(message)) {
            message = ColorUtil.stripAllColors(message);
            if (message.trim().isEmpty()) {
                return false; // Silently cancel empty messages after stripping colors
            }
            context.messageContent = message;
        }

        return true;
    }

    private boolean handleChannelLogic(ChatProcessingContext context) {
        Player player = context.player;
        String message = context.messageContent;

        ResolvedChannelMessage resolvedMessage = channelManager.resolveChannelMessage(message, player);
        if (resolvedMessage == null) {
            return false;
        }

        if (resolvedMessage.updatePlayerChannel()) {
            channelManager.setPlayerChannel(player, resolvedMessage.channel().getId());
        }

        if (resolvedMessage.message().trim().isEmpty()) {
            return false;
        }

        context.channel = resolvedMessage.channel();
        context.finalMessage = resolvedMessage.message();
        return true;
    }

    private boolean validateChannelAccess(ChatProcessingContext context) {
        Player player = context.player;
        Channel channel = context.channel;
        String finalMessage = context.finalMessage;

        // Check if channel is enabled
        if (!channel.isEnabled()) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("chat-disabled")));
            return false;
        }

        // Check permissions
        if (!channel.canSend(player)) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("no-permission")));
            return false;
        }

        // Check message length
        String messageForLengthCheck = player.hasPermission("nonchat.color") ? finalMessage
                : ColorUtil.stripAllColors(finalMessage);
        if (messageForLengthCheck.length() < channel.getMinLength()) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("message-too-short")
                    .replace("{min}", String.valueOf(channel.getMinLength()))));
            return false;
        }
        if (channel.getMaxLength() > 0 && messageForLengthCheck.length() > channel.getMaxLength()) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("message-too-long")
                    .replace("{max}", String.valueOf(channel.getMaxLength()))));
            return false;
        }

        // Check cooldown
        if (!channelManager.canSendMessage(player, channel)) {
            int remainingSeconds = channelManager.getRemainingCooldown(player, channel);
            MessageUtil.send(player, ColorUtil.parseComponent(messages.getString("channel-cooldown")
                    .replace("{seconds}", String.valueOf(remainingSeconds))
                    .replace("{channel}", channel.getDisplayName())));
            return false;
        }

        // Record message sent
        channelManager.recordMessageSent(player);
        return true;
    }

    private boolean processMessageThroughAPI(ChatProcessingContext context) {
        Player player = context.player;
        Channel channel = context.channel;
        String finalMessage = context.finalMessage;

        // Check API filters
        if (ChannelAPI.shouldFilterMessage(player, finalMessage, channel.getId())) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("message-filtered")));
            return false;
        }

        // Process through API processors
        String processedMessage = ChannelAPI.processMessage(player, finalMessage, channel.getId());
        if (processedMessage == null) {
            return false; // Message was cancelled by a processor
        }

        // Apply mention coloring if enabled
        String messageToSend = config.isMentionColoringEnabled()
                ? processMentionColoring(processedMessage)
                : processedMessage;

        context.processedMessage = processedMessage;
        context.finalMessage = messageToSend;
        return true;
    }

    private void broadcastProcessedMessage(ChatProcessingContext context) {
        Player player = context.player;
        Channel channel = context.channel;
        String messageToSend = context.finalMessage;
        String processedMessage = context.processedMessage;

        Component formattedMessage = channel.formatMessage(player, messageToSend);
        context.messageDelivered = broadcastMessage(player, formattedMessage, channel, processedMessage);

        // Notify if message wasn't delivered
        if (config.isUndeliveredMessageNotificationEnabled() && !context.messageDelivered) {
            MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("message-not-delivered")));
        }
    }

    private void handleBubbleCreation(ChatProcessingContext context) {
        Player player = context.player;
        Channel channel = context.channel;
        String messageToSend = context.finalMessage;

        // Only show chat bubbles for public channels with permission and if message was
        // delivered
        boolean shouldShowBubble = config.isChatBubblesEnabled()
                && player.hasPermission("nonchat.chatbubbles")
                && isPublicChannel(channel)
                && context.messageDelivered
                && plugin.getPlatformAdapter() != null
                && plugin.getPlatformAdapter().supportsChatBubbles();

        if (shouldShowBubble) {
            scheduleBubbleCreation(player, messageToSend);
        }
    }

    /**
     * Schedules bubble creation with robust error handling and fallbacks
     */
    private void scheduleBubbleCreation(Player player, String message) {
        String bubbleMessage = player.hasPermission("nonchat.color") ? message : ColorUtil.stripAllColors(message);

        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    removeBubble(player);
                    createBubble(player, bubbleMessage);
                } catch (Exception e) {
                    plugin.logError(
                            "Error in bubble creation task for player " + player.getName() + ": " + e.getMessage());
                }
            });
        } catch (IllegalArgumentException e) {
            plugin.logError(
                    "Failed to schedule bubble creation for player " + player.getName() + ": " + e.getMessage());
            // Try immediate execution as fallback
            try {
                removeBubble(player);
                createBubble(player, bubbleMessage);
            } catch (Exception fallbackError) {
                plugin.logError("Fallback bubble creation also failed for player " + player.getName() + ": "
                        + fallbackError.getMessage());
            }
        }
    }

    /**
     * Checks if a channel is considered "public" (should show chat bubbles) A
     * channel is public if it doesn't require special permissions to receive
     * messages or if the config allows bubbles in private channels
     *
     * @param channel The channel to check
     * @return true if the channel should show bubbles, false otherwise
     */
    private boolean isPublicChannel(Channel channel) {
        // If channel has receive permission requirements, it's considered private
        String receivePermission = channel.getReceivePermission();
        if (receivePermission != null && !receivePermission.isEmpty()) {
            return false;
        }

        // If channel has send permission requirements, it's also considered private
        String sendPermission = channel.getSendPermission();
        // Channel is public if it doesn't require any special permissions
        return !(sendPermission != null && !sendPermission.isEmpty());
    }

    private void startBubbleUpdater() {
        Runnable bubbleUpdateTask = this::updateBubbles;

        // Try different scheduling strategies in order of preference
        if (tryScheduleTask(bubbleUpdateTask, 1L, 1L, "primary")) {
            return;
        }

        if (tryScheduleTask(bubbleUpdateTask, 1L, 1L, "fallback")) {
            plugin.logResponse("Bubble updater started with fallback scheduler");
            return;
        }

        // Last resort: run once immediately
        plugin.logResponse("Starting bubble updater with immediate execution as last resort");
        try {
            Bukkit.getScheduler().runTask(plugin, bubbleUpdateTask);
            plugin.logResponse("Bubble updater started with immediate execution");
        } catch (IllegalArgumentException immediateError) {
            plugin.logError("Failed to start bubble updater with immediate execution: " + immediateError.getMessage());
        }
    }

    /**
     * Attempts to schedule a task with the given scheduler type
     */
    private boolean tryScheduleTask(Runnable task, long delay, long period, String schedulerType) {
        try {
            switch (schedulerType) {
                case "primary" -> Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
                case "fallback" -> Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
                default -> {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException e) {
            plugin.logError("Failed to start bubble updater with " + schedulerType + " scheduler: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates bubble positions and cleans up offline players
     */
    private void updateBubbles() {
        try {
            // Update bubble positions for online players
            bubbles.entrySet().stream()
                    .filter(entry -> entry.getKey().isOnline() && !entry.getValue().isEmpty())
                    .forEach(entry -> {
                        try {
                            Player player = entry.getKey();
                            Location newLoc = player.getLocation().add(0, config.getChatBubblesHeight(), 0);
                            plugin.getPlatformAdapter().updateBubblesLocation(entry.getValue(), newLoc);
                        } catch (Exception e) {
                            plugin.logError("Error updating bubbles for player " + entry.getKey().getName() + ": "
                                    + e.getMessage());
                        }
                    });

            // Clean up bubbles for offline players
            bubbles.entrySet().removeIf(entry -> {
                try {
                    if (!entry.getKey().isOnline()) {
                        plugin.getPlatformAdapter().removeBubbles(entry.getValue());
                        return true;
                    }
                } catch (Exception e) {
                    plugin.logError("Error cleaning up bubbles for offline player: " + e.getMessage());
                    return true; // Remove entry on error
                }
                return false;
            });
        } catch (Exception e) {
            plugin.logError("Error in bubble updater: " + e.getMessage());
        }
    }

    private void createBubble(Player player, String message) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return; // Don't spawn bubble if player is in spectator mode
        }

        try {
            Location loc = player.getLocation().add(0, config.getChatBubblesHeight(), 0);

            List<?> playerBubbles = plugin.getPlatformAdapter().spawnMultilineBubble(player, message, loc,
                    config.getChatBubblesScale(), config.getChatBubblesScaleX(), config.getChatBubblesScaleY(),
                    config.getChatBubblesScaleZ(), config.getChatBubblesBackgroundColor());

            // Only add bubbles if they were successfully created
            if (playerBubbles != null && !playerBubbles.isEmpty()) {
                bubbles.put(player, playerBubbles);

                try {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        removeBubble(player);
                    }, config.getChatBubblesDuration() * 20L);
                } catch (IllegalArgumentException e) {
                    plugin.logError(
                            "Failed to schedule bubble removal for player " + player.getName() + ": " + e.getMessage());
                    // Schedule removal using global scheduler as fallback
                    try {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            removeBubble(player);
                        }, config.getChatBubblesDuration() * 20L);
                    } catch (IllegalArgumentException fallbackError) {
                        plugin.logError("Fallback bubble removal scheduling also failed for player " + player.getName()
                                + ": " + fallbackError.getMessage());
                        // Try to remove bubble immediately as last resort
                        try {
                            removeBubble(player);
                        } catch (Exception immediateError) {
                            plugin.logError("Immediate bubble removal also failed for player " + player.getName() + ": "
                                    + immediateError.getMessage());
                        }
                    }
                }
            } else {
                plugin.logError("Failed to create chat bubbles for player: " + player.getName());
            }
        } catch (Exception e) {
            plugin.logError("Error creating chat bubbles for player " + player.getName() + ": " + e.getMessage());
        }
    }

    private void removeBubble(Player player) {
        try {
            List<?> playerBubbles = bubbles.remove(player);
            if (playerBubbles != null) {
                plugin.getPlatformAdapter().removeBubbles(playerBubbles);
            }
        } catch (Exception e) {
            plugin.logError("Error removing bubbles for player " + player.getName() + ": " + e.getMessage());
            // Try to clean up the map entry even if removal fails
            try {
                bubbles.remove(player);
            } catch (Exception cleanupError) {
                plugin.logError("Error cleaning up bubble map for player " + player.getName() + ": "
                        + cleanupError.getMessage());
            }
        }
    }

    private boolean handleBlockedWords(Player player, String message) {
        if (!player.hasPermission("nonchat.antiblockedwords")) {
            // Check if word blocking is enabled
            if (!config.isWordBlockingEnabled()) {
                return false;
            }
            
            WordBlocker wordBlocker = config.getWordBlocker();
            // Check blocked words on the message without color codes
            String messageToCheck = ColorUtil.stripAllColors(message);
            if (!wordBlocker.isMessageAllowed(messageToCheck)) {
                MessageUtil.send(player, ColorUtil.parseComponentCached(messages.getString("blocked-words")));
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the matcher used to extract mention candidates from a message.
     * Both patterns are static and compiled once; candidates are always filtered
     * against currently online players afterward (see {@link #handleMentions} and
     * {@link #processMentionColoring}), so extraction never needs to depend on
     * who is online, avoiding per-message pattern compilation and the inconsistent
     * behavior that came from swapping patterns based on the online player count.
     */
    private Matcher getMentionMatcher(String message) {
        Pattern pattern = config.isMentionWithoutAtEnabled() ? bareMentionPattern : atMentionPattern;
        return pattern.matcher(message);
    }

    private void handleMentions(Player sender, String message) {
        // Find all mentions in the message (strip colors first to avoid false matches)
        String messageToCheck = ColorUtil.stripAllColors(message);
        Matcher mentionMatcher = getMentionMatcher(messageToCheck);

        // Collect all the names found into a list and process with Stream API
        List<String> mentionedNames = new ArrayList<>();
        while (mentionMatcher.find()) {
            mentionedNames.add(mentionMatcher.group(1));
        }

        // Process the mentions using Stream API
        mentionedNames.stream()
                .map(Bukkit::getPlayerExact)
                .filter(java.util.Objects::nonNull)
                .forEach(player -> notifyMentionedPlayer(player, sender));
    }

    private void notifyMentionedPlayer(Player mentioned, Player sender) {
        String mentionMessage = messages.getString("mentioned");

        // Apply PlaceholderAPI to mention message
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                mentionMessage = PlaceholderAPI.setPlaceholders(sender, mentionMessage);
            } catch (Exception e) {
                plugin.logError("Error processing mention message placeholders: " + e.getMessage());
            }
        }

        // Replace {player} with sender name (keeping this for backward compatibility)
        mentionMessage = mentionMessage.replace("{player}", sender.getName());

        MessageUtil.send(mentioned, ColorUtil.parseComponent(mentionMessage));

        // Play mention sound if enabled for mention events
        if (config.isMentionSoundEnabled()) {
            try {
                mentioned.playSound(mentioned.getLocation(),
                        config.getMentionSound(),
                        config.getMentionSoundVolume(), config.getMentionSoundPitch());
            } catch (Exception e) {
                plugin.logError("Error playing mention sound: " + e.getMessage());
            }
        }
    }

    /**
     * Processes mention coloring in a message for the sender's view
     * 
     * @param message The message to process
     * @return The message with colored mentions
     */
    private String processMentionColoring(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String mentionColor = config.getMentionColor();
        boolean bareNamesAllowed = config.isMentionWithoutAtEnabled();
        Matcher mentionMatcher = getMentionMatcher(message);

        // Use StringBuilder for efficient string manipulation
        StringBuilder coloredMessage = new StringBuilder(message.length() + 32); // Add some buffer for color codes
        int lastEnd = 0;

        while (mentionMatcher.find()) {
            // The bare-name pattern matches every word in the message, so unlike the
            // '@'-only pattern it needs an explicit online-player check here to avoid
            // coloring ordinary words.
            if (bareNamesAllowed && Bukkit.getPlayerExact(mentionMatcher.group(1)) == null) {
                continue;
            }
            // Add text before the mention
            coloredMessage.append(message, lastEnd, mentionMatcher.start());
            // Add the colored mention with reset after it
            coloredMessage.append(mentionColor).append(mentionMatcher.group(0)).append("&r");
            lastEnd = mentionMatcher.end();
        }

        // Add remaining text after the last mention
        coloredMessage.append(message.substring(lastEnd));

        return coloredMessage.toString();
    }

    /**
     * Broadcasts a message to all eligible recipients and returns whether it
     * was delivered to any players.
     *
     * @param sender          The player sending the message
     * @param message         The formatted message component
     * @param channel         The channel being used
     * @param originalMessage The original message content
     * @return true if the message was delivered to at least one player, false
     *         otherwise
     */
    private boolean broadcastMessage(Player sender, Component message, Channel channel, String originalMessage) {
        // For console, create a simple message without our color modifications to avoid
        // &f appearing
        String consoleFormat = channel.getFormat().replace("{message}", originalMessage);

        // Apply PlaceholderAPI for console
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                consoleFormat = PlaceholderAPI.setPlaceholders(sender, consoleFormat);
            } catch (Exception e) {
                plugin.logError("Error processing format placeholders for console: " + e.getMessage());
            }
        }

        // Send to console with processed format
        MessageUtil.send(Bukkit.getConsoleSender(), ColorUtil.parseComponent(consoleFormat));

        // Count how many players received the message
        long recipientCount = Bukkit.getOnlinePlayers().stream()
                // Skip players ignoring the sender
                .filter(recipient -> ignoreCommand == null || !ignoreCommand.isIgnoring(recipient, sender))
                // Check for spy.all permission - bypass all restrictions
                .filter(recipient -> recipient.hasPermission("nonchat.spy.all") || 
                    // Normal filtering for players without spy.all permission
                    (channel.canReceive(recipient) && 
                     (channel.isGlobal() || channel.isInRange(sender, recipient))))
                // Send message to filtered recipients and count them
                .peek(recipient -> MessageUtil.send(recipient, message))
                .count();

        // Return true if at least one player (other than sender) received the message
        // We subtract 1 because the sender is also counted in the recipients
        return recipientCount > 1;
    }

    /**
     * Sets a player's active channel.
     *
     * @param player    The player
     * @param channelId The channel ID
     * @return true if successful, false if channel not found or not enabled
     */
    public boolean setPlayerChannel(Player player, String channelId) {
        return channelManager.setPlayerChannel(player, channelId);
    }

    /**
     * Gets a player's active channel.
     *
     * @param player The player
     * @return The player's channel
     */
    public Channel getPlayerChannel(Player player) {
        return channelManager.getPlayerChannel(player);
    }

    /**
     * Gets a channel by ID.
     *
     * @param id The channel ID
     * @return The channel, or null if not found
     */
    public Channel getChannel(String id) {
        return channelManager.getChannel(id);
    }

    /**
     * Gets all available channels.
     *
     * @return Collection of all channels
     */
    public java.util.Collection<Channel> getAllChannels() {
        return channelManager.getAllChannels();
    }

    /**
     * Gets all enabled channels.
     *
     * @return Collection of enabled channels
     */
    public java.util.Collection<Channel> getEnabledChannels() {
        return channelManager.getEnabledChannels();
    }

    /**
     * Reloads channels from config.
     */
    public void reloadChannels() {
        channelManager.loadChannels();
    }

    /**
     * Creates a new channel with the specified properties.
     *
     * @param channelId         The unique channel ID
     * @param displayName       The display name for the channel
     * @param format            The message format for the channel
     * @param character         The trigger character, or null for none
     * @param sendPermission    Permission to send to this channel, or empty for
     *                          everyone
     * @param receivePermission Permission to receive from this channel, or
     *                          empty for everyone
     * @param radius            Radius of the channel in blocks, or -1 for global
     * @param cooldown          Cooldown between messages in seconds
     * @param minLength         Minimum message length
     * @param maxLength         Maximum message length, or -1 for unlimited
     * @param switchMessage     Message to be sent when switching channel
     * @return The created channel, or null if the ID already exists
     */
    public Channel createChannel(String channelId, String displayName, String format,
            Character character, String sendPermission, String receivePermission,
            int radius, int cooldown, int minLength, int maxLength, String switchMessage) {
        String prefix = character != null ? String.valueOf(character) : "";
        return channelManager.createChannel(channelId, displayName, format, prefix,
                sendPermission, receivePermission, radius,
                cooldown, minLength, maxLength, switchMessage);
    }

    /**
     * Updates an existing channel with new properties, including Discord
     * channel ID.
     *
     * @param channelId         The channel ID to update
     * @param displayName       The display name for the channel (null to keep
     *                          existing)
     * @param format            The message format for the channel (null to keep
     *                          existing)
     * @param character         The trigger character (null to keep existing, '\0'
     *                          to
     *                          remove)
     * @param sendPermission    Permission to send to this channel (null to keep
     *                          existing)
     * @param receivePermission Permission to receive from this channel (null to
     *                          keep existing)
     * @param radius            Radius of the channel in blocks (-1 for global, null
     *                          to
     *                          keep existing)
     * @param enabled           Whether the channel is enabled (null to keep
     *                          existing)
     * @param cooldown          Cooldown between messages in seconds (null to keep
     *                          existing)
     * @param minLength         Minimum message length (null to keep existing)
     * @param maxLength         Maximum message length (null to keep existing)
     * @param switchMessage     Message to be sent when switching channel (null to keep
     *                          existing)
     * @return True if the channel was updated, false otherwise
     */
    public boolean updateChannel(String channelId, String displayName, String format,
            Character character, String sendPermission, String receivePermission,
            Integer radius, Boolean enabled, Integer cooldown,
            Integer minLength, Integer maxLength, String switchMessage) {
        String prefix = character != null ? String.valueOf(character) : null;
        return channelManager.updateChannel(channelId, displayName, format, prefix,
                sendPermission, receivePermission, radius, enabled,
                cooldown, minLength, maxLength, switchMessage);
    }

    /**
     * Deletes a channel.
     *
     * @param channelId The channel ID to delete
     * @return True if the channel was deleted, false otherwise
     */
    public boolean deleteChannel(String channelId) {
        return channelManager.deleteChannel(channelId);
    }

    /**
     * Sets a new default channel.
     *
     * @param channelId The channel ID to set as default
     * @return True if successful, false if channel doesn't exist or isn't
     *         enabled
     */
    public boolean setDefaultChannel(String channelId) {
        return channelManager.setDefaultChannel(channelId);
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    /**
     * Sets the ignore command instance.
     *
     * @param ignoreCommand The ignore command instance
     */
    public void setIgnoreCommand(IgnoreCommand ignoreCommand) {
        this.ignoreCommand = ignoreCommand;
    }

    /**
     * Cleanup method to remove all bubbles when plugin is disabled
     */
    public void cleanup() {
        if (plugin.getPlatformAdapter() != null) {
            bubbles.values().forEach(plugin.getPlatformAdapter()::removeBubbles);
        }
        bubbles.clear();
        playerLocks.clear();
    }
}
