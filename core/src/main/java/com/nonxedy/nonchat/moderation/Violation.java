package com.nonxedy.nonchat.moderation;

import java.util.UUID;

/**
 * A violation reported by a {@link ViolationDetector} for a single message
 * @param type Kind of violation
 * @param playerId Player who sent the message
 * @param message The message as typed by the player
 */
public record Violation(ViolationType type, UUID playerId, String message) {
}
