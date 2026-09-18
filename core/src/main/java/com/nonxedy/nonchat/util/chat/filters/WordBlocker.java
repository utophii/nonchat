package com.nonxedy.nonchat.util.chat.filters;

import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;

/**
 * Handles message filtering by checking for banned words and regex patterns.
 * When a violation is detected, executes the actions configured under
 * 'banned-words.actions' (special actions 'block' and 'notify-staff', plus
 * arbitrary console commands) and sends the configured warning message to the player.
 */
public class WordBlocker {

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

        handleDetection(player, message);
        return true;
    }

    /**
     * Checks if a message is allowed by scanning for banned words and patterns
     * @param message The message to check
     * @return true if message is allowed, false if it contains banned content
     */
    public boolean isMessageAllowed(String message) {
        String lowerMessage = message.toLowerCase();

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
                if (regex.matcher(message).find()) {
                    return false;
                }
            } catch (PatternSyntaxException e) {
                // Log invalid regex pattern but don't crash
                System.err.println("Invalid regex pattern in banned patterns: " + pattern);
            }
        }

        return true;
    }

    /**
     * Executes the configured actions for a detected violation
     * @param player The player who sent the message
     * @param message The original message
     */
    private void handleDetection(Player player, String message) {
        for (String action : config.getBannedWordsActions()) {
            if (action.equalsIgnoreCase("block")) {
                // Blocking is implied by the return value of checkAndHandle
                continue;
            }
            if (action.equalsIgnoreCase("notify-staff")) {
                notifyStaff(player, message);
            } else {
                // Execute as command
                executeCommand(player, action);
            }
        }

        // Send warning message to player if configured
        String warnMessage = config.getBannedWordsMessage();
        if (warnMessage != null && !warnMessage.isEmpty()) {
            // Resolve placeholders on the template first, then insert the flagged message,
            // so user input never passes through placeholder parsing
            String resolved = resolvePlaceholders(player, warnMessage)
                .replace("%message%", message != null ? message : "[empty]");
            MessageUtil.send(player, ColorUtil.parseComponentCached(resolved));
        }
    }

    /**
     * Notifies staff members about a banned word detection
     * @param player The player who sent the message
     * @param message The original message
     */
    private void notifyStaff(Player player, String message) {
        String template = messages.getString("banned-words-detected");
        if (template == null || template.isEmpty()) {
            return;
        }

        Component notificationComponent = ColorUtil.parseComponentCached(
            template.replace("{player}", player.getName())
                    .replace("{message}", message != null ? message : "[empty]"));

        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("nonchat.wordblocker.notify") || p.isOp())
            .forEach(p -> MessageUtil.send(p, notificationComponent));

        // Log to console if enabled
        if (config.isBannedWordsConsoleNotifyEnabled()) {
            MessageUtil.send(Bukkit.getConsoleSender(), notificationComponent);
        }
    }

    /**
     * Executes a command with placeholders resolved
     * @param player The player to use for placeholders
     * @param command The command to execute
     */
    private void executeCommand(Player player, String command) {
        if (command == null || command.isEmpty()) {
            return;
        }

        String resolvedCommand = resolvePlaceholders(player, command);
        try {
            // Run command sync on main thread
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("nonchat"), () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedCommand);
            });
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().log(Level.WARNING, "&#FFAFFB[nonchat] &cFailed to execute banned-words action command: {0}", e.getMessage());
        }
    }

    /**
     * Resolves placeholders in text using PlaceholderAPI when available
     * @param player The player to use for placeholders
     * @param text The text with placeholders
     * @return Text with placeholders resolved
     */
    private String resolvePlaceholders(Player player, String text) {
        if (player == null || text == null) return text;

        // Check if PlaceholderAPI is loaded
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }

        // Fall back to standard placeholders if PAPI not available
        return text.replace("%player_name%", player.getName())
                  .replace("%player_uuid%", player.getUniqueId().toString());
    }
}
