package com.nonxedy.nonchat.util.chat.filters;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nonxedy.nonchat.api.MessageFilter;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.util.chat.MentionCompletionUtil;
import com.nonxedy.nonchat.util.chat.filters.MessageHistory.MessageEntry;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;


/**
 * Intelligent spam detection filter
 * Detects repetitive messages, similar messages, and message flooding
 */
public class SpamDetector implements MessageFilter {
    
    private final PluginConfig config;
    private final PluginMessages messages;
    private final Cache<UUID, MessageHistory> messageHistoryCache;

    public SpamDetector(PluginConfig config, PluginMessages messages) {
        this.config = config;
        this.messages = messages;
        
        // Initialize cache with expiration based on maximum time window
        // Use the largest time window from all detection types
        int maxTimeWindow = Math.max(
            Math.max(
                config.getAntiSpamRepetitiveTimeWindow(),
                config.getAntiSpamSimilarTimeWindow()
            ),
            config.getAntiSpamFloodTimeWindow()
        );
        
        // Cache expires after max time window + some buffer (in seconds)
        this.messageHistoryCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(maxTimeWindow + 60, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public boolean shouldFilter(Player player, String message) {
        // Check if spam filter is enabled
        if (player == null || message == null || !config.isAntiSpamEnabled()) {
            return false;
        }

        // Check bypass permission
        if (player.hasPermission("nonchat.spam.bypass")) {
            return false;
        }

        // Get or create message history for player
        MessageHistory history = messageHistoryCache.get(
            player.getUniqueId(),
            uuid -> new MessageHistory(uuid)
        );

        long currentTime = System.currentTimeMillis();
        
        // Check for repetitive messages (before adding current message)
        if (config.isAntiSpamRepetitiveEnabled()) {
            if (detectRepetitiveSpam(history, message)) {
                // Add message to history for tracking, but block it
                history.addMessage(message, currentTime);
                return handleSpamDetection(player, message, "repetitive");
            }
        }

        // Check for similar messages (before adding current message)
        if (config.isAntiSpamSimilarEnabled()) {
            if (detectSimilarSpam(history, message)) {
                // Add message to history for tracking, but block it
                history.addMessage(message, currentTime);
                return handleSpamDetection(player, message, "similar");
            }
        }

        // Check for flood (before adding current message)
        if (config.isAntiSpamFloodEnabled()) {
            if (detectFlood(history)) {
                // Add message to history for tracking, but block it
                history.addMessage(message, currentTime);
                return handleSpamDetection(player, message, "flood");
            }
        }

        // Message passed all checks, add it to history
        history.addMessage(message, currentTime);

        // Cleanup old messages periodically
        cleanupOldMessages(history);

        return false;
    }

    /**
     * Detects if a message is repetitive (exact duplicate)
     * @param history Message history for the player
     * @param message Current message (not yet added to history)
     * @return true if repetitive spam detected
     */
    private boolean detectRepetitiveSpam(MessageHistory history, String message) {
        if (message == null) {
            return false;
        }
        
        int threshold = config.getAntiSpamRepetitiveThreshold();
        int timeWindow = config.getAntiSpamRepetitiveTimeWindow();
        
        List<MessageEntry> recentMessages = history.getMessagesInTimeWindow(timeWindow);
        
        // Count how many times this exact message appears in recent history
        // If count >= threshold, the current message would exceed the limit
        int count = 0;
        for (MessageEntry entry : recentMessages) {
            if (entry != null && entry.getMessage() != null && entry.getMessage().equals(message)) {
                count++;
            }
        }
        
        // If we already have threshold or more identical messages, block this one
        return count >= threshold;
    }

    /**
     * Detects if a message is similar to previous messages
     * @param history Message history for the player
     * @param message Current message
     * @return true if similar spam detected
     */
    private boolean detectSimilarSpam(MessageHistory history, String message) {
        if (message == null) {
            return false;
        }

        String sanitizedMessage = MentionCompletionUtil.stripMentions(ColorUtil.stripFormatting(message));
        if (sanitizedMessage.isEmpty()) {
            return false;
        }
        
        double threshold = config.getAntiSpamSimilarThreshold();
        int timeWindow = config.getAntiSpamSimilarTimeWindow();
        
        List<MessageEntry> recentMessages = history.getMessagesInTimeWindow(timeWindow);
        
        // Check similarity with recent messages
        for (MessageEntry entry : recentMessages) {
            if (entry == null || entry.getMessage() == null) {
                continue;
            }
            
            // Don't compare with itself
            if (entry.getMessage().equals(message)) {
                continue;
            }

            String sanitizedEntry = MentionCompletionUtil.stripMentions(
                    ColorUtil.stripFormatting(entry.getMessage()));
            if (sanitizedEntry.isEmpty() || sanitizedEntry.equals(sanitizedMessage)) {
                continue;
            }
            
            double similarity = TextSimilarityUtil.calculateSimilarity(sanitizedMessage, sanitizedEntry);
            if (similarity >= threshold) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Detects if player is flooding messages
     * @param history Message history for the player
     * @return true if flood detected
     */
    private boolean detectFlood(MessageHistory history) {
        int maxMessages = config.getAntiSpamFloodMaxMessages();
        int timeWindow = config.getAntiSpamFloodTimeWindow();
        
        // Count existing messages in time window
        // If count >= maxMessages, adding current message would exceed the limit
        int messageCount = history.getMessageCountInTimeWindow(timeWindow);
        return messageCount >= maxMessages;
    }

    /**
     * Handles spam detection by executing configured actions
     * @param player The player who sent the spam
     * @param message The spam message
     * @param spamType Type of spam detected (repetitive, similar, flood)
     */
    private boolean handleSpamDetection(Player player, String message, String spamType) {
        return FilterActionHandler.handle(config, player, message,
                FilterSettings.read(config.getConfig(), "anti-spam." + spamType,
                        getActionsForSpamType(spamType), getMessageForSpamType(spamType),
                        messages.getString("spam-detected-" + spamType),
                        config.isAntiSpamConsoleNotifyEnabled(), "nonchat.spam.notify"));
    }

    /**
     * Gets actions list for a specific spam type
     * @param spamType Type of spam (repetitive, similar, flood)
     * @return List of actions to execute
     */
    private List<String> getActionsForSpamType(String spamType) {
        switch (spamType) {
            case "repetitive":
                return config.getAntiSpamRepetitiveActions();
            case "similar":
                return config.getAntiSpamSimilarActions();
            case "flood":
                return config.getAntiSpamFloodActions();
            default:
                return List.of("block");
        }
    }

    /**
     * Gets warning message for a specific spam type
     * @param spamType Type of spam (repetitive, similar, flood)
     * @return Warning message
     */
    private String getMessageForSpamType(String spamType) {
        switch (spamType) {
            case "repetitive":
                return config.getAntiSpamRepetitiveMessage();
            case "similar":
                return config.getAntiSpamSimilarMessage();
            case "flood":
                return config.getAntiSpamFloodMessage();
            default:
                return "";
        }
    }

    /**
     * Cleans up old messages from history
     * @param history Message history to clean
     */
    private void cleanupOldMessages(MessageHistory history) {
        // Clean up messages older than the maximum time window
        int maxTimeWindow = Math.max(
            Math.max(
                config.getAntiSpamRepetitiveTimeWindow(),
                config.getAntiSpamSimilarTimeWindow()
            ),
            config.getAntiSpamFloodTimeWindow()
        );
        
        history.cleanupOldMessages(maxTimeWindow);
    }
}

