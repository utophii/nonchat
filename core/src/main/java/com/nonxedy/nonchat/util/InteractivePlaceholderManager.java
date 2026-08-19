package com.nonxedy.nonchat.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.nonxedy.nonchat.api.InteractivePlaceholder;
import com.nonxedy.nonchat.util.chat.filters.LinkDetector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

/**
 * Manager for handling interactive placeholders
 * Processes messages and replaces placeholder patterns with interactive components
 */
public class InteractivePlaceholderManager {

    private final Map<String, InteractivePlaceholder> placeholders = new HashMap<>();
    private final Pattern placeholderPattern;

    // For detecting / preserving trailing color - same logic as BaseChannel.extractTrailingColor()
    private static final Pattern HEX_TRAILING = Pattern.compile(".*(§#[A-Fa-f0-9]{6}|&#[A-Fa-f0-9]{6})(?:[^§&]*?)$");
    private static final Pattern BUNGEE_TRAILING = Pattern.compile(".*(?i)(§x(?:§[0-9a-fA-F]){6}|&x(?:&[0-9a-fA-F]){6})(?:[^§&]*?)$");
    private static final Pattern LEGACY_TRAILING = Pattern.compile(".*(§[0-9a-fklmnor]|&[0-9a-fklmnor])(?:[^§&]*?)$");
    private static final Pattern MINI_TRAILING = Pattern.compile(".*(<#[A-Fa-f0-9]{6}>|<(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>)(?:[^<]*?)$");
    private static final Pattern GRADIENT_TRAILING = Pattern.compile(".*(<gradient:[^>]+>)(?:[^<]*?)$");
    private static final Pattern LEADING_COLOR = Pattern.compile("^(§#[A-Fa-f0-9]{6}|&#[A-Fa-f0-9]{6}|§x(?:§[0-9a-fA-F]){6}|&x(?:&[0-9a-fA-F]){6}|§[0-9a-fklmnor]|&[0-9a-fklmnor]|<#[A-Fa-f0-9]{6}>|<(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>|<gradient:[^>]+>)", Pattern.CASE_INSENSITIVE);

    public InteractivePlaceholderManager() {
        // Pattern to match placeholders like [name], [name:arg1:arg2], etc.
        this.placeholderPattern = Pattern.compile("\\[([\\w]+)(?::([^\\]]*))?\\]", Pattern.CASE_INSENSITIVE);
    }

    /**
     * Registers an interactive placeholder
     *
     * @param placeholder The placeholder to register
     */
    public void registerPlaceholder(@NotNull InteractivePlaceholder placeholder) {
        placeholders.put(placeholder.getPlaceholder().toLowerCase(), placeholder);
    }

    /**
     * Unregisters an interactive placeholder
     *
     * @param placeholderName The name of the placeholder to unregister
     */
    public void unregisterPlaceholder(@NotNull String placeholderName) {
        placeholders.remove(placeholderName.toLowerCase());
    }

    /**
     * Gets a registered placeholder by name
     *
     * @param placeholderName The name of the placeholder
     * @return The placeholder instance or null if not found
     */
    @Nullable
    public InteractivePlaceholder getPlaceholder(@NotNull String placeholderName) {
        return placeholders.get(placeholderName.toLowerCase());
    }

    /**
     * Checks if a placeholder is registered
     *
     * @param placeholderName The name of the placeholder
     * @return true if registered, false otherwise
     */
    public boolean isPlaceholderRegistered(@NotNull String placeholderName) {
        return placeholders.containsKey(placeholderName.toLowerCase());
    }

    @NotNull
    public Component processMessage(@NotNull Player player, @NotNull String message) {
        if (message.isEmpty()) {
            return Component.text(message);
        }

        List<Component> parts = new ArrayList<>();
        Matcher matcher = placeholderPattern.matcher(message);
        int lastEnd = 0;

        // Track the last active color so text after a placeholder re-inherits it.
        // BaseChannel already prepended inheritedColor (e.g. "&c" from LuckPerms suffix)
        // to the start of `message`, so the first text segment contains it.
        String currentColor = "";

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String textBefore = message.substring(lastEnd, matcher.start());
                // On first segment, capture its trailing color as base color
                // On subsequent segments, re-apply currentColor if segment doesn't start with its own color
                String toParse = textBefore;
                if (!currentColor.isEmpty() && !startsWithColor(toParse)) {
                    toParse = currentColor + toParse;
                }
                parts.add(LinkDetector.makeLinksClickable(toParse));
                String trailing = extractTrailingColor(toParse);
                if (!trailing.isEmpty()) {
                    currentColor = trailing;
                } else if (parts.size() == 1) {
                    // fallback: if trailing not detected but toParse started with color, keep it
                    String leading = extractLeadingColor(toParse);
                    if (!leading.isEmpty()) currentColor = leading;
                }
            } else if (parts.isEmpty() && lastEnd == 0 && matcher.start() == 0) {
                // Message starts directly with placeholder but `message` has inheritedColor
                // prepended before it (e.g. "&c[coords]..."): textBefore was empty,
                // so we need to seed currentColor from the prefix before the placeholder.
                // The prefix is message.substring(0, matcher.start()) - empty here,
                // so look at the very start of the original message for a color tag.
                String prefix = message.substring(0, matcher.start());
                if (prefix.isEmpty()) {
                    // Try to extract from start of message (inheritedColor is inside textBefore of next iteration? no)
                    // Actually if message = "&c[coords]", textBefore for first match is "&c", handled above.
                    // This branch only fires if matcher.start()==0 with no prefix, so no color.
                }
            }

            String placeholderName = matcher.group(1).toLowerCase();
            String arguments = matcher.group(2);
            InteractivePlaceholder placeholder = getPlaceholder(placeholderName);

            if (placeholder != null && placeholder.isEnabled()) {
                String permission = placeholder.getPermission();
                if (permission == null || permission.isEmpty()
                        || player.hasPermission(permission)) {
                    String[] args = arguments != null
                            ? arguments.split(":")
                            : new String[0];
                    parts.add(placeholder.process(player, args));
                    // Do NOT update currentColor from placeholder's internal formatting.
                    // The placeholder is self-contained (e.g. white "[📍]") and
                    // the following text must resume with currentColor (suffix color).
                } else {
                    // No permission — render as plain text but preserve color
                    String raw = matcher.group();
                    String toParse = !currentColor.isEmpty() && !startsWithColor(raw) ? currentColor + raw : raw;
                    parts.add(LinkDetector.makeLinksClickable(toParse));
                }
            } else {
                String raw = matcher.group();
                String toParse = !currentColor.isEmpty() && !startsWithColor(raw) ? currentColor + raw : raw;
                parts.add(LinkDetector.makeLinksClickable(toParse));
            }

            lastEnd = matcher.end();

            // Seed currentColor if still empty (e.g. first segment was placeholder)
            if (currentColor.isEmpty() && !parts.isEmpty()) {
                // Try to infer from the beginning of message
                String head = message.substring(0, Math.min(message.length(), 32));
                String c = extractTrailingColor(head);
                if (!c.isEmpty()) currentColor = c;
                else {
                    c = extractLeadingColor(head);
                    if (!c.isEmpty()) currentColor = c;
                }
            }
        }

        if (lastEnd < message.length()) {
            String tail = message.substring(lastEnd);
            String toParse = tail;
            if (!currentColor.isEmpty() && !startsWithColor(tail)) {
                toParse = currentColor + tail;
            }
            parts.add(LinkDetector.makeLinksClickable(toParse));
        } else if (parts.isEmpty()) {
            // No placeholder found at all — just parse whole message (already has inheritedColor)
            return LinkDetector.makeLinksClickable(message);
        }

        if (parts.isEmpty()) {
            return Component.text(message);
        }

        return Component.join(
            JoinConfiguration.noSeparators(),
            parts
        );
    }

    private static String extractTrailingColor(String formatPart) {
        if (formatPart == null || formatPart.isEmpty()) return "";
        Matcher m;
        m = HEX_TRAILING.matcher(formatPart);
        if (m.find()) return m.group(1);
        m = BUNGEE_TRAILING.matcher(formatPart);
        if (m.find()) return m.group(1);
        m = LEGACY_TRAILING.matcher(formatPart);
        if (m.find()) return m.group(1);
        m = MINI_TRAILING.matcher(formatPart);
        if (m.find()) return m.group(1);
        m = GRADIENT_TRAILING.matcher(formatPart);
        if (m.find()) return m.group(1);
        return "";
    }

    private static String extractLeadingColor(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher m = LEADING_COLOR.matcher(text);
        if (m.find()) return m.group(1);
        return "";
    }

    private static boolean startsWithColor(String text) {
        if (text == null || text.isEmpty()) return false;
        String trimmed = text.stripLeading();
        return LEADING_COLOR.matcher(trimmed).find();
    }

    @NotNull
    public Map<String, InteractivePlaceholder> getAllPlaceholders() {
        return new HashMap<>(placeholders);
    }

    /**
     * Clears all registered placeholders
     */
    public void clearPlaceholders() {
        placeholders.clear();
    }
}
