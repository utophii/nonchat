package com.nonxedy.nonchat.util.chat.filters;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.api.MessageFilter;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;

public class AdDetector implements MessageFilter {
    private static final String DOMAIN =
            "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}";
    private static final String IPV4 = "(?:\\d{1,3}\\.){3}\\d{1,3}";

    /** A URL with a scheme is unambiguous and is detected at every sensitivity. */
    private static final Pattern EXPLICIT_URL_PATTERN = Pattern.compile(
            "(?i)(?<![\\w])(?:https?|ftp)://(?:[^\\s/@]+(?::[^\\s/@]+)?@)?(?:"
                    + DOMAIN + "|" + IPV4
                    + ")(?::\\d{1,5})?(?:[/#?][^\\s]*)?");

    /**
     * A bare domain/IP is more prone to false positives, so it is checked only
     * from the medium sensitivity level upwards. Numeric versions such as
     * 1.21.11 do not match: a domain must end in a letter TLD and an IPv4
     * address must contain four octets.
     */
    private static final Pattern BARE_HOST_PATTERN = Pattern.compile(
            "(?i)(?<![\\w.-])(?:" + DOMAIN + "|" + IPV4
                    + ")(?::\\d{1,5})?(?:[/#?][^\\s]*)?(?![\\w.-])");

    private static final Pattern JOIN_WORD_PATTERN = Pattern.compile("(?i)\\bjoin\\b");
    private static final Pattern SERVER_WORD_PATTERN = Pattern.compile("(?i)\\bserver\\b");
    private static final Pattern IP_WORD_PATTERN = Pattern.compile("(?i)\\bip\\b");
    private static final Pattern PLAY_WORD_PATTERN = Pattern.compile("(?i)\\bplay\\b");

    private static final float BARE_HOST_SENSITIVITY = 0.25f;
    private static final float COMMON_TERMS_SENSITIVITY = 0.5f;

    private final PluginConfig config;
    private final Float fixedSensitivity;
    private final String punishCommand;
    private final boolean staffNotify;
    private final String notifyMessage;

    /**
     * Creates a detector using the configured sensitivity. The value is read
     * for every message, so it also follows a live configuration reload.
     */
    public AdDetector(PluginConfig config, String punishCommand,
                      boolean staffNotify, String notifyMessage) {
        this(config, (Float) null, punishCommand, staffNotify, notifyMessage);
    }

    /**
     * Compatibility constructor for callers that explicitly provide a
     * sensitivity value. Explicit values remain fixed; production code uses
     * the config-backed constructor above.
     */
    public AdDetector(PluginConfig config, float sensitivity, String punishCommand,
                      boolean staffNotify, String notifyMessage) {
        this(config, Float.valueOf(sensitivity), punishCommand, staffNotify, notifyMessage);
    }

    private AdDetector(PluginConfig config, Float fixedSensitivity, String punishCommand,
                       boolean staffNotify, String notifyMessage) {
        this.config = config;
        this.fixedSensitivity = fixedSensitivity;
        this.punishCommand = punishCommand;
        this.staffNotify = staffNotify;
        this.notifyMessage = notifyMessage;
    }

    @Override
    public boolean shouldFilter(Player player, String message) {
        if (player.hasPermission("nonchat.ad.bypass") || message == null || message.isBlank()) {
            return false;
        }

        float sensitivity = getSensitivity();

        // Explicit URLs are certain advertisements regardless of sensitivity.
        if (containsUnwhitelistedMatch(EXPLICIT_URL_PATTERN, message)) {
            notifyStaff(player, message);
            return true;
        }

        // Bare domains and IP addresses are less certain and therefore respect
        // the configured sensitivity. This is what prevents version numbers
        // such as 1.21.11 from being treated as advertisements.
        if (sensitivity >= BARE_HOST_SENSITIVITY
                && containsUnwhitelistedMatch(BARE_HOST_PATTERN, message)) {
            notifyStaff(player, message);
            return true;
        }

        // Textual heuristics are the least certain detection mode and are only
        // enabled at higher sensitivity values.
        if (sensitivity >= COMMON_TERMS_SENSITIVITY && detectCommonAdTerms(message)) {
            notifyStaff(player, message);
            return true;
        }

        return false;
    }

    /**
     * Reads the value from PluginConfig on every check so /nonchat reload
     * immediately applies a changed sensitivity without recreating managers.
     */
    private float getSensitivity() {
        float sensitivity = fixedSensitivity != null
                ? fixedSensitivity
                : config.getAntiAdSensitivity();
        return Math.max(0f, Math.min(1f, sensitivity));
    }

    private boolean containsUnwhitelistedMatch(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            if (!isWhitelisted(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private boolean isWhitelisted(String url) {
        String normalizedUrl = normalizeUrl(url);
        List<String> whitelistedUrls = config.getAntiAdWhitelistedUrls();

        for (String whitelisted : whitelistedUrls) {
            if (normalizedUrl.equals(normalizeUrl(whitelisted))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeUrl(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim()
                .replaceFirst("(?i)^[a-z][a-z0-9+.-]*://", "")
                .replaceFirst("(?i)^www\\.", "");

        // Do not make a URL fail its whitelist entry just because it was
        // followed by ordinary sentence punctuation.
        normalized = normalized.replaceFirst("[.,!?;:)]*$", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private boolean detectCommonAdTerms(String message) {
        return (JOIN_WORD_PATTERN.matcher(message).find()
                    && SERVER_WORD_PATTERN.matcher(message).find())
                || (IP_WORD_PATTERN.matcher(message).find()
                    && PLAY_WORD_PATTERN.matcher(message).find());
    }

    private String resolvePlaceholders(Player player, String text) {
        if (player == null) return text;
        
        // Try PlaceholderAPI first if available
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (ClassNotFoundException e) {
            // Fall back to standard placeholders if PAPI not available
            return text.replace("%player_name%", player.getName())
                      .replace("%player_uuid%", player.getUniqueId().toString());
        }
    }

    private void notifyStaff(Player player, String message) {
        if (staffNotify) {
            String notification = notifyMessage;

            // Resolve PlaceholderAPI placeholders on the template (e.g. %player_name%)
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    notification = PlaceholderAPI.setPlaceholders(player, notification);
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.WARNING, "&#FFAFFB[nonchat] &cError processing notify-message placeholders: {0}", e.getMessage());
                }
            }

            // Insert the flagged message last, so user input never passes through placeholder parsing
            notification = notification.replace("{message}", message);

            Component notificationComponent = ColorUtil.parseComponentCached(notification);
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("nonchat.ad.notify") || p.isOp())
                .forEach(p -> MessageUtil.send(p, notificationComponent));

            // Log to console
            MessageUtil.send(Bukkit.getConsoleSender(), notificationComponent);
        }
        
        // Execute configured punishment command with resolved placeholders
        if (punishCommand != null && !punishCommand.isEmpty()) {
            String resolvedCommand = resolvePlaceholders(player, punishCommand);
            try {
                // Run command sync on main thread
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("nonchat"), () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedCommand);
                });
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().log(Level.WARNING, "&#FFAFFB[nonchat] &cFailed to execute punish command: {0}", e.getMessage());
            }
        }
    }
}
