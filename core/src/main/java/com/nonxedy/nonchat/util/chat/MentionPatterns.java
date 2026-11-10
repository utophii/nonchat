package com.nonxedy.nonchat.util.chat;

import java.util.regex.Pattern;

/**
 * Shared patterns used to extract player mention candidates from a message.
 *
 * <p>Bedrock players joining through Geyser/Floodgate get a name prefix such as
 * {@code .} or {@code +}, which {@code \w} does not match. The prefix is configured
 * in {@code mentions.bedrock-prefix} and applied through {@link #setBedrockPrefix}
 * whenever the config is loaded. Candidates are always checked against online
 * players afterward.</p>
 */
public final class MentionPatterns {
    private static volatile Patterns current = build("");

    private MentionPatterns() {}

    /**
     * Sets the Bedrock name prefix and rebuilds the patterns.
     *
     * @param prefix Floodgate/Geyser username prefix, empty or null for none
     */
    public static void setBedrockPrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim();
        if (!normalized.equals(current.prefix())) {
            current = build(normalized);
        }
    }

    /** Matches {@code @name}; group 1 is the name. */
    public static Pattern atMention() {
        return current.atMention();
    }

    /** Matches {@code name} with an optional leading {@code @}; group 1 is the name. */
    public static Pattern bareMention() {
        return current.bareMention();
    }

    /** Regex fragment matching a player name, optionally with the Bedrock prefix; no capturing group. */
    public static String nameRegex() {
        return current.name();
    }

    private static Patterns build(String prefix) {
        String name = prefix.isEmpty() ? "\\w+" : "(?:" + Pattern.quote(prefix) + ")?\\w+";
        return new Patterns(prefix, name,
                Pattern.compile("@(" + name + ")"),
                Pattern.compile("@?(?<!\\w)(" + name + ")\\b"));
    }

    private record Patterns(String prefix, String name, Pattern atMention, Pattern bareMention) {}
}
