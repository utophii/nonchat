package com.nonxedy.nonchat.util.chat.filters;

import java.util.function.Predicate;

import com.nonxedy.nonchat.util.chat.MentionCompletionUtil;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Filters excessive capitalization in chat messages
 * Controls and processes uppercase character usage
 */
@Data
@AllArgsConstructor
public class CapsFilter {
    private final boolean enabled;
    private final int maxCapsPercentage;
    private final int minLength;

    /**
     * Determines if a message should be filtered for excessive caps
     * @param message The message to check
     * @return true if message exceeds caps limit, false otherwise
     */
    public boolean shouldFilter(String message) {
        return shouldFilter(message, MentionCompletionUtil::isOnlinePlayerName);
    }

    /**
     * Determines if a message should be filtered for excessive caps, ignoring
     * mentions of players matched by {@code onlinePlayerPredicate}.
     *
     * @param message The message to check
     * @param onlinePlayerPredicate Predicate identifying online player names
     * @return true if message exceeds caps limit, false otherwise
     */
    public boolean shouldFilter(String message, Predicate<String> onlinePlayerPredicate) {
        // First strictly check if filter is disabled
        if (!this.enabled || message == null) {
            return false;
        }

        String sanitized = MentionCompletionUtil.stripMentions(
                ColorUtil.stripFormatting(message),
                onlinePlayerPredicate);

        // Then check message length
        if (sanitized.length() < this.minLength) {
            return false;
        }

        int capsCount = 0;
        for (char c : sanitized.toCharArray()) {
            if (Character.isUpperCase(c)) {
                capsCount++;
            }
        }

        double percentage = (double) capsCount / sanitized.length() * 100;
        return percentage > this.maxCapsPercentage;
    }

    /**
     * Converts a message to lowercase to reduce capitalization
     * @param message The message to filter
     * @return Filtered message in lowercase
     */
    public String filterMessage(String message) {
        return message.toLowerCase();
    }

    /**
     * Checks if caps filtering is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the maximum percentage of caps allowed
     * @return Maximum caps percentage
     */
    public int getMaxCapsPercentage() {
        return maxCapsPercentage;
    }
    
    /**
     * Gets the minimum message length for caps checking
     * @return Minimum message length
     */
    public int getMinLength() {
        return minLength;
    }
}
