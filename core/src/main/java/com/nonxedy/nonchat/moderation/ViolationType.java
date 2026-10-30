package com.nonxedy.nonchat.moderation;

/**
 * Kinds of chat violations the moderation system can report.
 * Each type has its own {@link ViolationRule} (warning, staff notification and sanction tiers).
 */
public enum ViolationType {
    BANNED_WORD,
    CAPS,
    SPAM_REPETITIVE,
    SPAM_SIMILAR,
    SPAM_FLOOD,
    ADVERTISING
}
