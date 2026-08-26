package com.nonxedy.nonchat.util.core.colors;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.ChatColor;

// ─────────────────────────────────────────────────────────────────────────────
// ConcurrentLRUCache
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Thread-safe LRU cache backed by a {@link LinkedHashMap} with
 * a {@link ReentrantReadWriteLock}. Allows concurrent reads while
 * write operations (put / clear) are exclusive.
 *
 * <p>Also tracks hit / miss counts for diagnostics via {@link #stats()}.
 */
class ConcurrentLRUCache<K, V> {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<K, V> cache;
    private final int maxSize;

    private long hits   = 0;
    private long misses = 0;

    public ConcurrentLRUCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache   = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > ConcurrentLRUCache.this.maxSize;
            }
        };
    }

    public @Nullable V get(@NotNull K key) {
        lock.readLock().lock();
        try {
            V value = cache.get(key);
            if (value != null) hits++; else misses++;
            return value;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(@NotNull K key, @NotNull V value) {
        lock.writeLock().lock();
        try {
            cache.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(@NotNull K key) {
        lock.writeLock().lock();
        try {
            cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try { return cache.size(); }
        finally { lock.readLock().unlock(); }
    }

    public CacheStats.Entry stats() {
        return new CacheStats.Entry(hits, misses, size());
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ColorUtil
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Central utility for parsing and converting all Minecraft color / formatting
 * formats into Adventure {@link Component} objects.
 *
 * <h3>Supported input formats:</h3>
 * <ul>
 *   <li>Legacy ampersand — {@code &a}, {@code &l}, etc.</li>
 *   <li>Legacy section   — {@code §a}, {@code §l}, etc.</li>
 *   <li>Ampersand HEX   — {@code &#RRGGBB}</li>
 *   <li>BungeeCord HEX  — {@code &x&R&G&B&R&G&B} and {@code §x§R§G§B§R§G§B}</li>
 *   <li>MiniMessage      — {@code <red>}, {@code <#FFFFFF>}, {@code <gradient:...>}, etc.</li>
 * </ul>
 *
 * <h3>Caching:</h3>
 * <p>Two separate {@link ConcurrentLRUCache} instances are used:
 * <ul>
 *   <li>{@code NORMALIZE_CACHE}  — normalized MiniMessage strings  (500 entries)</li>
 *   <li>{@code COMPONENT_CACHE}  — parsed {@link Component} objects (1000 entries)</li>
 * </ul>
 * Use {@link #parseComponentCached(String)} for static config strings and
 * {@link #parseComponent(String)} for dynamic messages with placeholders.
 */
public final class ColorUtil {

    private ColorUtil() {}

    // ── Patterns ────────────────────────────────────────────────────────────────

    static final Pattern HEX_PATTERN =
            Pattern.compile("&#([A-Fa-f0-9]{6})");

    static final Pattern LEGACY_COLOR_PATTERN =
            Pattern.compile("(?i)&[0-9A-FK-OR]");

    static final Pattern SECTION_COLOR_PATTERN =
            Pattern.compile("(?i)§[0-9A-FK-OR]");

    static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile(
            "(?i)&x&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])");

    static final Pattern BUNGEE_HEX_PATTERN = Pattern.compile(
            "§x§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])");

    static final Pattern MINIMESSAGE_TAG_PATTERN =
            Pattern.compile("</?[a-zA-Z][a-zA-Z0-9_:-]*(?::[^<>\\r\\n]*)? >|<#[0-9a-fA-F]{6}>");

    private static final Pattern GRADIENT_PATTERN =
            Pattern.compile("(?i)<gradient:[^>]+>");

    // ── MiniMessage instance ─────────────────────────────────────────────────────

    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .editTags(b -> b.resolver(createHeadTagResolver()))
            .build();

    // ── Caches ───────────────────────────────────────────────────────────────────

    /** Caches raw-string → normalised-MiniMessage-string conversions. */
    private static final ConcurrentLRUCache<String, String>    NORMALIZE_CACHE  = new ConcurrentLRUCache<>(500);

    /** Caches raw-string → parsed Component. Only for static (placeholder-free) strings. */
    private static final ConcurrentLRUCache<String, Component> COMPONENT_CACHE = new ConcurrentLRUCache<>(1000);

    /** Legacy parseColor string cache (for old BungeeCord / string paths). */
    private static final ConcurrentLRUCache<String, String>    COLOR_CACHE      = new ConcurrentLRUCache<>(1000);

    // ════════════════════════════════════════════════════════════════════════════
    // PUBLIC API — Parsing
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Parses all color formats (Legacy, BungeeCord HEX, Ampersand HEX, MiniMessage)
     * into an Adventure {@link Component}.
     *
     * @param message the raw message string
     * @return parsed component, never {@code null}
     */
    public static @NotNull Component parseComponent(@NotNull String message) {
        if (message.isEmpty()) return Component.empty();

        try {
            String normalized = normalizeToMiniMessage(message);
            return MINI_MESSAGE.deserialize(normalized);
        } catch (Exception e) {
            return fallbackParse(message);
        }
    }

    /**
     * Parses the message with additional {@link TagResolver}s (e.g. PlaceholderAPI).
     *
     * @param message   the raw message string
     * @param resolvers extra resolvers to apply during parsing
     * @return parsed component, never {@code null}
     */
    public static @NotNull Component parseComponent(@NotNull String message, TagResolver... resolvers) {
        if (message.isEmpty()) return Component.empty();

        try {
            String   normalized = normalizeToMiniMessage(message);
            TagResolver combined = TagResolver.resolver(resolvers);
            return MINI_MESSAGE.deserialize(normalized, combined);
        } catch (Exception e) {
            return fallbackParse(message);
        }
    }

    /**
     * Parses the message respecting the player's {@code nonchat.color} permission.
     * If the player lacks the permission, all formatting is stripped.
     *
     * @param message the raw message string
     * @param player  the sender, may be {@code null} (permission check skipped)
     * @return parsed component, never {@code null}
     */
    public static @NotNull Component parseComponent(@NotNull String message, @Nullable Player player) {
        if (player != null && !player.hasPermission("nonchat.color")) {
            return Component.text(stripFormatting(message));
        }
        return parseComponent(message);
    }

    /**
     * Parses the message with a player permission check and extra resolvers.
     *
     * @param message   the raw message string
     * @param player    the sender (nullable)
     * @param resolvers extra resolvers
     * @return parsed component, never {@code null}
     */
    public static @NotNull Component parseComponent(
            @NotNull String message,
            @Nullable Player player,
            TagResolver... resolvers) {
        if (player != null && !player.hasPermission("nonchat.color")) {
            return Component.text(stripFormatting(message));
        }
        return parseComponent(message, resolvers);
    }

    /**
     * Cached variant of {@link #parseComponent(String)}.
     *
     * <p><b>Only use this for static strings without runtime placeholders.</b>
     * The result is stored in a {@link ConcurrentLRUCache} (1000 entries).
     *
     * @param message the raw message string
     * @return parsed component (possibly from cache), never {@code null}
     */
    public static @NotNull Component parseComponentCached(@NotNull String message) {
        return parseComponentCached(message, false);
    }

    /**
     * Cached variant with explicit placeholder control.
     *
     * <p>When {@code hasPlaceholders} is {@code true} the cache is bypassed so that
     * placeholder values are always resolved fresh. Use {@code false} (default) for
     * static config strings such as prefix or format lines.
     *
     * @param message         the raw message string
     * @param hasPlaceholders if {@code true}, skip the cache entirely
     * @return parsed component, never {@code null}
     */
    public static @NotNull Component parseComponentCached(@NotNull String message, boolean hasPlaceholders) {
        if (message.isEmpty()) return Component.empty();
        if (hasPlaceholders)   return parseComponent(message);

        Component cached = COMPONENT_CACHE.get(message);
        if (cached != null) return cached;

        Component result = parseComponent(message);
        COMPONENT_CACHE.put(message, result);
        return result;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PUBLIC API — Chat format
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Parses a chat format template, substituting {@code %player%} and
     * {@code %message%} placeholders.
     *
     * <p>Formatting permission is derived from the sender's {@code nonchat.color}
     * permission node.
     *
     * @param format  the format string (e.g. {@code "<gray>[<gold>%player%</gold>]</gray> %message%"})
     * @param sender  the sending player
     * @param message the chat message
     * @return parsed component, never {@code null}
     */
    public static @NotNull Component parseChatFormat(
            @NotNull String format,
            @NotNull Player sender,
            @NotNull String message) {
        return parseChatFormat(format, sender.getName(), message,
                sender.hasPermission("nonchat.color"));
    }

    public static @NotNull Component parseChatFormat(
            @NotNull String format,
            @NotNull String playerName,
            @NotNull String message) {
        return parseChatFormat(format, playerName, message, true);
    }

    public static @NotNull Component parseChatFormat(
            @NotNull String format,
            @NotNull String playerName,
            @NotNull String message,
            boolean allowFormatting,
            TagResolver... extra) {

        if (format.isEmpty()) {
            return allowFormatting ? parseComponent(message) : Component.text(stripFormatting(message));
        }

        try {
            String normalizedFormat = normalizeToMiniMessage(format)
                    .replace("%player%", "<player>")
                    .replace("%message%", "<message>");

            TagResolver playerResolver  = Placeholder.unparsed("player", playerName);
            TagResolver messageResolver = allowFormatting
                    ? Placeholder.parsed("message", normalizeToMiniMessage(message))
                    : Placeholder.unparsed("message", stripFormatting(message));

            TagResolver combined = extra.length > 0
                    ? TagResolver.resolver(playerResolver, messageResolver, TagResolver.resolver(extra))
                    : TagResolver.resolver(playerResolver, messageResolver);

            return MINI_MESSAGE.deserialize(normalizedFormat, combined);

        } catch (Exception e) {
            String fallback = format
                    .replace("%player%", playerName)
                    .replace("%message%", allowFormatting ? message : stripFormatting(message));
            return parseComponent(fallback);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PUBLIC API — Stripping
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Removes all color and formatting codes, returning plain visible text.
     *
     * @param message the raw message string
     * @return plain-text string without any formatting
     */
    public static @NotNull String stripFormatting(@NotNull String message) {
        if (message.isEmpty()) return message;
        try {
            return PlainTextComponentSerializer.plainText().serialize(parseComponent(message));
        } catch (Exception e) {
            return stripFormattingFallback(message);
        }
    }

    /**
     * Removes only the specified formatting formats from the string,
     * leaving other formats intact.
     *
     * <p>Example — remove only legacy codes, keep MiniMessage tags:
     * <pre>{@code
     * ColorUtil.stripFormatting(msg, ColorFormat.LEGACY, ColorFormat.AMPERSAND_HEX);
     * }</pre>
     *
     * @param message the raw message string
     * @param formats the formats to strip (empty = strip all)
     * @return string with specified formats removed
     */
    public static @NotNull String stripFormatting(@NotNull String message, ColorFormat... formats) {
        if (message.isEmpty() || formats.length == 0) return stripFormatting(message);
        String result = message;
        for (ColorFormat fmt : formats) result = fmt.strip(result);
        return result;
    }

    /**
     * @deprecated Use {@link #stripFormatting(String)} instead.
     */
    @Deprecated
    public static @NotNull String stripAllColors(@NotNull String message) {
        return stripFormatting(message);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PUBLIC API — Detection
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Detects the primary color format present in the message.
     *
     * @param message the string to inspect
     * @return detected {@link ColorFormat}, or {@link ColorFormat#NONE}
     */
    public static ColorFormat detectFormat(@NotNull String message) {
        return message.isEmpty() ? ColorFormat.NONE : ColorFormat.detect(message);
    }

    /**
     * Returns all color formats present in the message.
     *
     * @param message the string to inspect
     * @return set of detected formats, or {@code {NONE}} if none found
     */
    public static Set<ColorFormat> detectAllFormats(@NotNull String message) {
        return message.isEmpty()
                ? EnumSet.of(ColorFormat.NONE)
                : ColorFormat.detectAll(message);
    }

    public static boolean hasColorCodes(@NotNull String message) {
        return !message.isEmpty() && detectFormat(message) != ColorFormat.NONE;
    }

    public static boolean containsMiniMessageTags(@NotNull String message) {
        return !message.isEmpty() && MINIMESSAGE_TAG_PATTERN.matcher(message).find();
    }

    public static boolean containsGradient(@NotNull String message) {
        return !message.isEmpty() && GRADIENT_PATTERN.matcher(message).find();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PUBLIC API — Fluent builder
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Returns a fluent {@link MessageParser} for the given message.
     *
     * @param message the raw message string
     * @return a new {@link MessageParser} instance
     */
    public static MessageParser parser(@NotNull String message) {
        return new MessageParser(message);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PUBLIC API — Bukkit Color
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Parses a hex color string into a Bukkit {@link Color}.
     *
     * Supported formats (with or without leading {@code #}):
     * <ul>
     *   <li>{@code #RGB}       -> expanded to RRGGBB, full opacity</li>
     *   <li>{@code #RRGGBB}   -> full opacity</li>
     *   <li>{@code #RRGGBBAA} -> RRGGBB + alpha channel (00 = transparent, FF = opaque)</li>
     * </ul>
     *
     * @param hexColor hex string with or without leading {@code #}
     * @return {@link Color}, or {@link Color#BLACK} on parse failure
     */
    public static @NotNull Color parseHexColor(@NotNull String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) return Color.BLACK;
        try {
            String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;

            // #RGB -> #RRGGBB
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                        + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            }

            int r, g, b;

            if (hex.length() == 6) {
                // #RRGGBB — full opacity
                r = Integer.parseInt(hex.substring(0, 2), 16);
                g = Integer.parseInt(hex.substring(2, 4), 16);
                b = Integer.parseInt(hex.substring(4, 6), 16);
                return Color.fromRGB(r, g, b);
            } else {
                return Color.BLACK;
            }
        } catch (IllegalArgumentException e) {
            return Color.BLACK;
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PUBLIC API — Legacy string path
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Converts legacy {@code &} and {@code &#RRGGBB} codes into section-coded
     * BungeeCord strings. Cached via an LRU cache.
     *
     * <p>Prefer {@link #parseComponent(String)} unless you need a raw string.
     *
     * @param message the raw message
     * @return section-coded string
     */
    public static @NotNull String parseColor(@NotNull String message) {
        if (message.isEmpty()) return message;

        String cached = COLOR_CACHE.get(message);
        if (cached != null) return cached;

        // &x&R&G&B&R&G&B → §x§R§G§B§R§G§B
        Matcher ampMatcher = AMPERSAND_HEX_PATTERN.matcher(message);
        StringBuffer ampBuffer = new StringBuffer(message.length());
        while (ampMatcher.find()) {
            String bungee = "§x§" + ampMatcher.group(1) + "§" + ampMatcher.group(2)
                    + "§" + ampMatcher.group(3) + "§" + ampMatcher.group(4)
                    + "§" + ampMatcher.group(5) + "§" + ampMatcher.group(6);
            ampMatcher.appendReplacement(ampBuffer, Matcher.quoteReplacement(bungee));
        }
        ampMatcher.appendTail(ampBuffer);
        String preProcessed = ampBuffer.toString();

        String withTranslated = ChatColor.translateAlternateColorCodes('&', preProcessed);

        // &#RRGGBB → §x§R§G§B§R§G§B
        Matcher hexMatcher = HEX_PATTERN.matcher(withTranslated);
        StringBuilder buffer = new StringBuilder(withTranslated.length() + 32);
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            String bungeeHex = "§x§" + hex.charAt(0) + "§" + hex.charAt(1)
                    + "§" + hex.charAt(2) + "§" + hex.charAt(3)
                    + "§" + hex.charAt(4) + "§" + hex.charAt(5);
            hexMatcher.appendReplacement(buffer, Matcher.quoteReplacement(bungeeHex));
        }
        hexMatcher.appendTail(buffer);

        String result = buffer.toString();
        COLOR_CACHE.put(message, result);
        return result;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PUBLIC API — Cache management
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Clears all caches. Call this on plugin reload or language change.
     */
    public static void invalidateCache() {
        NORMALIZE_CACHE.clear();
        COMPONENT_CACHE.clear();
        COLOR_CACHE.clear();
    }

    /**
     * Removes a specific key from all caches.
     *
     * @param key the exact raw string that was previously cached
     */
    public static void invalidateCache(@NotNull String key) {
        NORMALIZE_CACHE.remove(key);
        COMPONENT_CACHE.remove(key);
        COLOR_CACHE.remove(key);
    }

    /**
     * Returns a snapshot of current cache statistics for diagnostics.
     *
     * @return {@link CacheStats} record
     */
    public static @NotNull CacheStats getCacheStats() {
        return new CacheStats(
                NORMALIZE_CACHE.stats(),
                COMPONENT_CACHE.stats(),
                COLOR_CACHE.stats()
        );
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Config / server messages shorthand
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Shorthand for config/server strings. Automatically uses the component
     * cache since config values are static.
     */
    public static @NotNull Component parseConfigComponent(@NotNull String message) {
        return parseComponentCached(message);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PRIVATE — Normalisation
    // ════════════════════════════════════════════════════════════════════════════

    private static @NotNull String normalizeToMiniMessage(@NotNull String message) {
        if (message.isEmpty()) return message;

        String cached = NORMALIZE_CACHE.get(message);
        if (cached != null) return cached;

        String result = message;

        // &x&R&G&B&R&G&B → <#RRGGBB>
        Matcher ampMatcher = AMPERSAND_HEX_PATTERN.matcher(result);
        StringBuffer ampBuffer = new StringBuffer(result.length());
        while (ampMatcher.find()) {
            String hex = ampMatcher.group(1) + ampMatcher.group(2)
                    + ampMatcher.group(3) + ampMatcher.group(4)
                    + ampMatcher.group(5) + ampMatcher.group(6);
            ampMatcher.appendReplacement(ampBuffer, Matcher.quoteReplacement("<#" + hex + ">"));
        }
        ampMatcher.appendTail(ampBuffer);
        result = ampBuffer.toString();

        // §x§R§G§B§R§G§B → <#RRGGBB>
        Matcher bungeeMatcher = BUNGEE_HEX_PATTERN.matcher(result);
        StringBuffer bungeeBuffer = new StringBuffer(result.length());
        while (bungeeMatcher.find()) {
            String hex = bungeeMatcher.group(1) + bungeeMatcher.group(2)
                    + bungeeMatcher.group(3) + bungeeMatcher.group(4)
                    + bungeeMatcher.group(5) + bungeeMatcher.group(6);
            bungeeMatcher.appendReplacement(bungeeBuffer, Matcher.quoteReplacement("<#" + hex + ">"));
        }
        bungeeMatcher.appendTail(bungeeBuffer);
        result = bungeeBuffer.toString();

        // &#FFFFFF → <#FFFFFF>
        Matcher hexMatcher = HEX_PATTERN.matcher(result);
        StringBuffer hexBuffer = new StringBuffer(result.length() + 32);
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexBuffer, "<#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(hexBuffer);
        result = hexBuffer.toString();

        // &a / §a → <green>, &l → <bold>, etc.
        result = convertLegacyCodesToMiniMessage(result);

        NORMALIZE_CACHE.put(message, result);
        return result;
    }

    private static @NotNull String convertLegacyCodesToMiniMessage(@NotNull String message) {
        if (message.isEmpty()) return message;

        StringBuilder result = new StringBuilder(message.length() + 16);
        int i   = 0;
        int len = message.length();

        while (i < len) {
            char current = message.charAt(i);

            // Skip MiniMessage tags intact
            if (current == '<') {
                int endTag = message.indexOf('>', i);
                if (endTag != -1) {
                    result.append(message, i, endTag + 1);
                    i = endTag + 1;
                    continue;
                }
            }

            // Convert &x and §x codes
            if (i < len - 1 && (current == '&' || current == '§')) {
                char code = message.charAt(i + 1);
                String tag = legacyCodeToMiniMessageTag(code);
                if (tag != null) {
                    result.append(tag);
                    i += 2;
                    continue;
                }
            }

            result.append(current);
            i++;
        }

        return result.toString();
    }

    private static @Nullable String legacyCodeToMiniMessageTag(char code) {
        return switch (code) {
            case '0'       -> "<reset><black>";
            case '1'       -> "<reset><dark_blue>";
            case '2'       -> "<reset><dark_green>";
            case '3'       -> "<reset><dark_aqua>";
            case '4'       -> "<reset><dark_red>";
            case '5'       -> "<reset><dark_purple>";
            case '6'       -> "<reset><gold>";
            case '7'       -> "<reset><gray>";
            case '8'       -> "<reset><dark_gray>";
            case '9'       -> "<reset><blue>";
            case 'a', 'A' -> "<reset><green>";
            case 'b', 'B' -> "<reset><aqua>";
            case 'c', 'C' -> "<reset><red>";
            case 'd', 'D' -> "<reset><light_purple>";
            case 'e', 'E' -> "<reset><yellow>";
            case 'f', 'F' -> "<reset><white>";
            case 'k', 'K' -> "<obfuscated>";
            case 'l', 'L' -> "<bold>";
            case 'm', 'M' -> "<strikethrough>";
            case 'n', 'N' -> "<underlined>";
            case 'o', 'O' -> "<italic>";
            case 'r', 'R' -> "<reset>";
            default        -> null;
        };
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PRIVATE — Helpers
    // ════════════════════════════════════════════════════════════════════════════

    private static @NotNull Component fallbackParse(@NotNull String message) {
        try {
            return LegacyComponentSerializer.legacySection().deserialize(parseColor(message));
        } catch (Exception ignored) {
            return Component.text(stripFormattingFallback(message));
        }
    }

    private static @NotNull String stripFormattingFallback(@NotNull String message) {
        String result = AMPERSAND_HEX_PATTERN.matcher(message).replaceAll("");
        result = HEX_PATTERN.matcher(result).replaceAll("");
        result = LEGACY_COLOR_PATTERN.matcher(result).replaceAll("");
        result = SECTION_COLOR_PATTERN.matcher(result).replaceAll("");
        result = result.replaceAll("<[^>]+>", "");
        return result;
    }

    private static @NotNull TagResolver createHeadTagResolver() {
        return TagResolver.resolver("head", (args, context) -> {
            String target = args.popOr("The <head> tag requires a player name, UUID, or texture path").value();
            boolean showHat = true;
            if (args.hasNext()) {
                String raw = args.pop().value();
                if ("true".equalsIgnoreCase(raw))       showHat = true;
                else if ("false".equalsIgnoreCase(raw)) showHat = false;
                else throw context.newException("The <head> tag hat argument must be true or false", null);
            }
            return Tag.inserting(createHeadComponent(target, showHat));
        });
    }

    /** Available only on Adventure 4.17+ (Paper 1.21+); falls back gracefully otherwise. */
    private static final boolean MODERN_HEAD_RENDERER_AVAILABLE = detectHeadRenderer();

    private static boolean detectHeadRenderer() {
        try {
            Class.forName("net.kyori.adventure.text.object.ObjectContents");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static @NotNull Component createHeadComponent(@NotNull String rawTarget, boolean showHat) {
        String target = rawTarget.trim();
        if (target.isEmpty()) return Component.empty();

        if (!MODERN_HEAD_RENDERER_AVAILABLE) {
            // Player-head object contents require Adventure 4.17+ (Paper 1.21+).
            // On older servers render a graceful text fallback instead of crashing.
            return Component.text("[" + target + "]", NamedTextColor.GOLD);
        }
        return ModernHeadRenderer.render(target, showHat);
    }

    private static @Nullable UUID tryParseUuid(@NotNull String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static boolean looksLikeTexturePath(@NotNull String value) {
        return value.indexOf('/') >= 0 || value.indexOf(':') >= 0;
    }

    private static @NotNull Key parseTextureKey(@NotNull String value) {
        return value.indexOf(':') >= 0 ? Key.key(value) : Key.key("minecraft", value);
    }
}
