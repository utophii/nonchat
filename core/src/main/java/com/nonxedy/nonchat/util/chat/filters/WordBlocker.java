package com.nonxedy.nonchat.util.chat.filters;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.bukkit.entity.Player;

import com.nonxedy.nonchat.api.MessageFilter;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.util.chat.MentionCompletionUtil;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;


/**
 * Handles message filtering by checking for banned words and regex patterns.
 * When a violation is detected, executes the actions configured under
 * 'banned-words.actions' (special actions 'block' and 'notify-staff', plus
 * arbitrary console commands) and sends the configured warning message to the player.
 */
public class WordBlocker implements MessageFilter {

    @Override
    public boolean shouldFilter(Player player, String message) {
        return checkAndHandle(player, message);
    }

    private final PluginConfig config;
    private final PluginMessages messages;
    private final List<String> bannedWords;
    private final List<String> bannedPatterns;

    /**
     * Creates a fully-featured word blocker that executes configured actions on detection.
     * @param config Plugin configuration
     * @param messages Plugin messages
     */
    public WordBlocker(PluginConfig config, PluginMessages messages) {
        this.config = config;
        this.messages = messages;
        this.bannedWords = config.getBannedWords();
        this.bannedPatterns = config.getBannedPatterns();
    }

    /**
     * Creates a matcher-only word blocker without action handling (used in unit tests).
     * @param bannedWords List of banned words
     * @param bannedPatterns List of banned regex patterns
     */
    public WordBlocker(List<String> bannedWords, List<String> bannedPatterns) {
        this.config = null;
        this.messages = null;
        this.bannedWords = bannedWords;
        this.bannedPatterns = bannedPatterns;
    }

    /**
     * Checks a message and, when it contains banned content, executes the configured actions.
     * Requires the blocker to be created with the PluginConfig/PluginMessages constructor.
     * @param player Player who sent the message
     * @param message Original message as typed by the player
     * @return true if the message is blocked, false if it is allowed
     */
    public boolean checkAndHandle(Player player, String message) {
        if (config == null || messages == null) {
            throw new IllegalStateException("checkAndHandle requires the PluginConfig/PluginMessages constructor");
        }
        if (!config.isWordBlockingEnabled()) {
            return false;
        }
        if (player == null || player.hasPermission("nonchat.antiblockedwords")) {
            return false;
        }

        // Check blocked words on the message without color codes
        if (isMessageAllowed(ColorUtil.stripFormatting(message))) {
            return false;
        }

        return FilterActionHandler.handle(config, player, message,
                FilterSettings.read(config.getConfig(), "banned-words", config.getBannedWordsActions(),
                        config.getBannedWordsMessage(), messages.getString("banned-words-detected"),
                        config.isBannedWordsConsoleNotifyEnabled(), "nonchat.wordblocker.notify"));
    }

    /**
     * Checks if a message is allowed by scanning for banned words and patterns
     * @param message The message to check
     * @return true if message is allowed, false if it contains banned content
     */
    public boolean isMessageAllowed(String message) {
        return isMessageAllowed(message, MentionCompletionUtil::isOnlinePlayerName);
    }

    /**
     * Checks if a message is allowed by scanning for banned words and patterns,
     * ignoring mentions of players matched by {@code onlinePlayerPredicate}.
     *
     * @param message The message to check
     * @param onlinePlayerPredicate Predicate identifying online player names
     * @return true if message is allowed, false if it contains banned content
     */
    public boolean isMessageAllowed(String message, Predicate<String> onlinePlayerPredicate) {
        if (message == null || message.isEmpty()) {
            return true;
        }

        String sanitized = MentionCompletionUtil.stripMentions(
                ColorUtil.stripFormatting(message),
                onlinePlayerPredicate);
        if (sanitized.isEmpty()) {
            return true;
        }

        String lowerMessage = sanitized.toLowerCase();

        // Check banned words (case-insensitive)
        for (String word : bannedWords) {
            if (lowerMessage.contains(word.toLowerCase())) {
                return false;
            }
        }

        // Check regex patterns (case-insensitive)
        for (String pattern : bannedPatterns) {
            try {
                Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                if (regex.matcher(sanitized).find()) {
                    return false;
                }
            } catch (PatternSyntaxException e) {
                // Log invalid regex pattern but don't crash
                System.err.println("Invalid regex pattern in banned patterns: " + pattern);
            }
        }

        return true;
    }
}
