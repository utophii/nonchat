package com.nonxedy.nonchat.util.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility methods for completing and sanitizing player mentions in chat-like input.
 */
public final class MentionCompletionUtil {
    private static final Pattern MENTION_TOKEN_PATTERN = Pattern.compile(
            "(?<!\\w)@(\\w+)\\b(?!(?:\\.[a-zA-Z0-9]|://|:\\d|/\\w))"
                    + "|(?<![\\w@./:-])\\b(\\w+)\\b(?!(?:\\.[a-zA-Z0-9]|://|:\\d|/\\w))");
    private static final Pattern MULTI_WHITESPACE_PATTERN = Pattern.compile("\\s{2,}");

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

    /**
     * Removes mentions of online players from a message so player names do not
     * trigger chat filters such as caps, banned words, or anti-ad checks.
     *
     * @param message message text to sanitize for filtering
     * @return message with online player mentions removed
     */
    public static String stripMentions(String message) {
        return stripMentions(message, MentionCompletionUtil::isOnlinePlayerName);
    }

    /**
     * Removes mentions of players matching {@code onlinePlayerPredicate} from a message.
     *
     * @param message message text to sanitize for filtering
     * @param onlinePlayerPredicate predicate that returns true when a candidate name belongs to an online player
     * @return message with matching player mentions removed
     */
    public static String stripMentions(String message, Predicate<String> onlinePlayerPredicate) {
        if (message == null || message.isEmpty() || onlinePlayerPredicate == null) {
            return message;
        }

        Matcher matcher = MENTION_TOKEN_PATTERN.matcher(message);
        StringBuilder stripped = null;
        int lastEnd = 0;

        while (matcher.find()) {
            String candidateName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (candidateName == null || !onlinePlayerPredicate.test(candidateName)) {
                continue;
            }

            if (stripped == null) {
                stripped = new StringBuilder(message.length());
            }
            stripped.append(message, lastEnd, matcher.start());
            lastEnd = matcher.end();
        }

        if (stripped == null) {
            return message;
        }

        stripped.append(message, lastEnd, message.length());
        return MULTI_WHITESPACE_PATTERN.matcher(stripped.toString()).replaceAll(" ").trim();
    }

    /**
     * Checks whether the given name matches an online player on the server.
     *
     * @param name candidate player name
     * @return true if an online player with that exact name exists
     */
    public static boolean isOnlinePlayerName(String name) {
        if (name == null || name.isEmpty() || Bukkit.getServer() == null) {
            return false;
        }
        return Bukkit.getPlayerExact(name) != null;
    }
}
