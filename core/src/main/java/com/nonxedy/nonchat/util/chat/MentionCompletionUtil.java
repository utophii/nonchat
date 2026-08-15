package com.nonxedy.nonchat.util.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility methods for completing player mentions in chat-like input.
 */
public final class MentionCompletionUtil {
    private MentionCompletionUtil() {
    }

    /**
     * Returns @mention suggestions for the token currently being typed.
     *
     * @param sender sender requesting completions
     * @param lastToken current token/argument being completed
     * @return visible online player names prefixed with @, or an empty list when the token is not a mention
     */
    public static List<String> getMentionSuggestions(CommandSender sender, String lastToken) {
        return getMentionSuggestions(sender, lastToken, player -> true);
    }

    /**
     * Returns @mention suggestions restricted by an additional player filter
     *
     * @param sender sender requesting completions
     * @param lastToken current token/argument being completed
     * @param playerFilter additional visibility/scope filter
     * @return matching visible online player names prefixed with @
     */
    public static List<String> getMentionSuggestions(CommandSender sender, String lastToken,
                                                       Predicate<Player> playerFilter) {
        if (lastToken == null || !lastToken.startsWith("@")) {
            return List.of();
        }

        String partialName = lastToken.substring(1).toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player player && !player.canSee(onlinePlayer)) {
                continue;
            }

            if (!playerFilter.test(onlinePlayer)) {
                continue;
            }

            if (!onlinePlayer.getName().toLowerCase(Locale.ROOT).startsWith(partialName)) {
                continue;
            }

            suggestions.add("@" + onlinePlayer.getName());
        }

        return suggestions;
    }

    /**
     * Extracts the last whitespace-delimited token from an input buffer.
     *
     * @param buffer input buffer
     * @return last token, or an empty string for empty buffers
     */
    public static String extractLastToken(String buffer) {
        if (buffer == null || buffer.isEmpty()) {
            return "";
        }

        int lastSpace = buffer.lastIndexOf(' ');
        return lastSpace >= 0 ? buffer.substring(lastSpace + 1) : buffer;
    }
}
