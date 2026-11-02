package com.nonxedy.nonchat.util.chat.filters;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nonxedy.nonchat.config.ModerationConfig;
import com.nonxedy.nonchat.moderation.Violation;
import com.nonxedy.nonchat.moderation.ViolationDetector;
import com.nonxedy.nonchat.moderation.ViolationType;
import com.nonxedy.nonchat.util.chat.MentionCompletionUtil;
import com.nonxedy.nonchat.util.chat.filters.MessageHistory.MessageEntry;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;


/**
 * Intelligent spam detection filter
 * Detects repetitive messages, similar messages, and message flooding
 */
public class SpamDetector implements ViolationDetector {
    
    private final ModerationConfig config;
    private final Cache<UUID, MessageHistory> messageHistoryCache;

    public SpamDetector(ModerationConfig config) {
        this.config = config;
        
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
    public String bypassPermission() {
        return "nonchat.spam.bypass";
    }

    @Override
    public Optional<Violation> inspect(Player player, String message) {
        // Check if spam filter is enabled
        if (message == null || !config.isAntiSpamEnabled()) {
            return Optional.empty();
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
                return violation(player, message, ViolationType.SPAM_REPETITIVE);
            }
        }

        // Check for similar messages (before adding current message)
        if (config.isAntiSpamSimilarEnabled()) {
            if (detectSimilarSpam(history, message)) {
                // Add message to history for tracking, but block it
                history.addMessage(message, currentTime);
                return violation(player, message, ViolationType.SPAM_SIMILAR);
            }
        }

        // Check for flood (before adding current message)
        if (config.isAntiSpamFloodEnabled()) {
            if (detectFlood(history)) {
                // Add message to history for tracking, but block it
                history.addMessage(message, currentTime);
                return violation(player, message, ViolationType.SPAM_FLOOD);
            }
        }

        // Message passed all checks, add it to history
        history.addMessage(message, currentTime);

        // Cleanup old messages periodically
        cleanupOldMessages(history);

        return Optional.empty();
    }

    private Optional<Violation> violation(Player player, String message, ViolationType type) {
        return Optional.of(new Violation(type, player.getUniqueId(), message));
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

