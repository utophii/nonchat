package com.nonxedy.nonchat.moderation;

import java.util.Optional;

import org.bukkit.entity.Player;

/**
 * Detects a single kind of violation in a chat message.
 * Detectors only detect: blocking, warnings, staff notifications and commands
 * are handled by the {@link SanctionsManager}.
 */
public interface ViolationDetector {

    /**
     * Gets the permission that exempts a player from this detector
     * @return Bypass permission, or null if the detector cannot be bypassed
     */
    String bypassPermission();

    /**
     * Inspects a message sent by a player
     * @param player Player who sent the message
     * @param message The message to check
     * @return The detected violation, or empty if the message is allowed
     */
    Optional<Violation> inspect(Player player, String message);
}
