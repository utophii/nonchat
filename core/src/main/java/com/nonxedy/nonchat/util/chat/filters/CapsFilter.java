package com.nonxedy.nonchat.util.chat.filters;

import java.util.function.Predicate;

import com.nonxedy.nonchat.util.chat.MentionCompletionUtil;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import lombok.Data;

/**
 * Filters excessive capitalization in chat messages
 * Controls and processes uppercase character usage
 */
@Data
public class CapsFilter implements com.nonxedy.nonchat.api.MessageFilter {
    private final boolean enabled;
    private final int maxCapsPercentage;
    private final int minLength;
    private final com.nonxedy.nonchat.config.PluginConfig config;

    public CapsFilter(boolean enabled, int maxCapsPercentage, int minLength) {
        this(enabled, maxCapsPercentage, minLength, null);
    }

    public CapsFilter(boolean enabled, int maxCapsPercentage, int minLength,
            com.nonxedy.nonchat.config.PluginConfig config) {
        this.enabled = enabled;
        this.maxCapsPercentage = maxCapsPercentage;
        this.minLength = minLength;
        this.config = config;
    }

    @Override
    public boolean shouldFilter(org.bukkit.entity.Player player, String message) {
        if (config == null) {
            return player != null && !player.hasPermission("nonchat.caps.bypass") && shouldFilter(message);
        }
        return checkAndHandle(player, message, config, config.getPlugin().getConfigService().getMessages());
    }


    /** Checks and handles a caps violation with the same response policy as other filters. */
    public boolean checkAndHandle(org.bukkit.entity.Player player, String message,
            com.nonxedy.nonchat.config.PluginConfig config,
            com.nonxedy.nonchat.config.PluginMessages messages) {
        if (player == null || player.hasPermission("nonchat.caps.bypass") || !shouldFilter(message)) return false;
        FilterSettings settings = FilterSettings.read(config.getConfig(), "caps-filter",
                java.util.List.of("block"), messages.getString("caps-filter"),
                "&c{player} used excessive caps: {message}", false, "nonchat.caps.notify");
        String warning = settings.message() == null ? "" : settings.message()
                .replace("{percentage}", String.valueOf(maxCapsPercentage));
        return FilterActionHandler.handle(config, player, message, new FilterSettings(
                settings.actions(), warning, settings.notifyMessage(), settings.consoleNotify(), settings.permission()));
    }

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
