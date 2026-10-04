package com.nonxedy.nonchat.util.chat.filters;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.api.MessageFilter;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.util.chat.MentionCompletionUtil;
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
     * address must contain four octets. Matches are additionally validated by
     * {@link TldList} so that ordinary "word.word" chat text like
     * "hello.hello" is not mistaken for a domain.
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
        return shouldFilter(player, message, MentionCompletionUtil::isOnlinePlayerName);
    }

    public boolean shouldFilter(Player player, String message, Predicate<String> onlinePlayerPredicate) {
        if ((player != null && player.hasPermission("nonchat.ad.bypass")) || message == null || message.isBlank()) {
            return false;
        }

        String sanitized = MentionCompletionUtil.stripMentions(
                ColorUtil.stripFormatting(message),
                onlinePlayerPredicate);
        if (sanitized.isBlank()) {
            return false;
        }

        float sensitivity = getSensitivity();

        // Explicit URLs are certain advertisements regardless of sensitivity.
        if (containsUnwhitelistedMatch(EXPLICIT_URL_PATTERN, sanitized, candidate -> true)) {
            notifyStaff(player, message);
            return true;
        }

        // Bare domains and IP addresses are less certain and therefore respect
        // the configured sensitivity. This is what prevents version numbers
        // such as 1.21.11 from being treated as advertisements. The TLD check
        // additionally prevents ordinary "word.word" chat text such as
        // "hello.hello" from being mistaken for a domain.
        if (sensitivity >= BARE_HOST_SENSITIVITY
                && containsUnwhitelistedMatch(BARE_HOST_PATTERN, sanitized,
                        TldList::isPlausibleHost)) {
            notifyStaff(player, message);
            return true;
        }

        // Textual heuristics are the least certain detection mode and are only
        // enabled at higher sensitivity values.
        if (sensitivity >= COMMON_TERMS_SENSITIVITY && detectCommonAdTerms(sanitized)) {
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

    /**
     * Searches the message for plausible matches that are not whitelisted.
     * The plausibility check lets callers discard syntactically valid but
     * semantically bogus matches (such as "hello.hello") before consulting
     * the configured domain rules.
     */
    private boolean containsUnwhitelistedMatch(Pattern pattern, String message,
                                               Predicate<String> plausibilityCheck) {
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (plausibilityCheck.test(candidate) && !isWhitelisted(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWhitelisted(String url) {
        for (String rule : getConfiguredUrlRules()) {
            if (rule != null && matchesConfiguredAddress(url, rule)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getConfiguredUrlRules() {
        if (config == null) {
            return List.of();
        }
        List<String> rules = config.getAntiAdWhitelistedUrls();
        return rules != null ? rules : List.of();
    }

    /**
     * A domain-only rule matches its host and all descendant subdomains.
     * Rules containing a URL path remain exact matches, preserving the
     * existing ability to allow a single invite URL such as discord.gg/code.
     * A leading {@code *.} scopes a whitelist rule to subdomains only.
     */
    private boolean matchesConfiguredAddress(String address, String configuredRule) {
        String normalizedAddress = normalizeUrl(address);
        String normalizedRule = normalizeUrl(configuredRule);

        // Preserve prior behavior where www.example.com and example.com were
        // considered equivalent for exact URL entries.
        if (normalizeForExactMatch(normalizedAddress)
                .equals(normalizeForExactMatch(normalizedRule))) {
            return true;
        }

        ParsedAddress candidate = parseAddress(normalizedAddress);
        ParsedAddress rule = parseAddress(normalizedRule);
        if (candidate == null || rule == null) {
            return false;
        }

        boolean domainOnlyRule = rule.path().isEmpty() || "/".equals(rule.path());
        boolean wildcardPathMatch = rule.subdomainsOnly()
                && rule.path().equals(candidate.path());
        if (!domainOnlyRule && !wildcardPathMatch) {
            return false;
        }
        if (rule.port() != null && !rule.port().equals(candidate.port())) {
            return false;
        }

        if (candidate.host().equals(rule.host())) {
            return !rule.subdomainsOnly();
        }
        return candidate.host().endsWith("." + rule.host());
    }

    private String normalizeForExactMatch(String value) {
        return value.replaceFirst("(?i)^www\\.", "");
    }

    private String normalizeUrl(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim()
                .replaceFirst("(?i)^[a-z][a-z0-9+.-]*://", "");

        // Do not make a URL fail its rule just because it was followed by
        // ordinary sentence punctuation. Keep www. here so wildcard rules can
        // correctly treat it as a subdomain; exact matching handles the legacy
        // www/non-www equivalence separately.
        normalized = normalized.replaceFirst("[.,!?;:)]*$", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private ParsedAddress parseAddress(String normalizedAddress) {
        if (normalizedAddress.isEmpty()) {
            return null;
        }

        int suffixStart = firstSuffixIndex(normalizedAddress);
        String authority = suffixStart >= 0
                ? normalizedAddress.substring(0, suffixStart)
                : normalizedAddress;
        String path = suffixStart >= 0
                ? normalizedAddress.substring(suffixStart)
                : "";

        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }

        boolean subdomainsOnly = authority.startsWith("*.");
        if (subdomainsOnly) {
            authority = authority.substring(2);
        }

        String port = null;
        int colon = authority.lastIndexOf(':');
        if (colon > 0 && authority.substring(colon + 1).matches("\\d+")) {
            port = authority.substring(colon + 1);
            authority = authority.substring(0, colon);
        }

        if (authority.isEmpty()) {
            return null;
        }
        return new ParsedAddress(authority, port, path, subdomainsOnly);
    }

    private int firstSuffixIndex(String value) {
        int index = -1;
        for (char delimiter : new char[] {'/', '?', '#'}) {
            int next = value.indexOf(delimiter);
            if (next >= 0 && (index < 0 || next < index)) {
                index = next;
            }
        }
        return index;
    }

    private record ParsedAddress(String host, String port, String path,
                                 boolean subdomainsOnly) {
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
