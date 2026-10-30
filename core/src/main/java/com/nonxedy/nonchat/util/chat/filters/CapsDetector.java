package com.nonxedy.nonchat.util.chat.filters;

import java.util.Optional;

import org.bukkit.entity.Player;

import com.nonxedy.nonchat.config.ModerationConfig;
import com.nonxedy.nonchat.moderation.Violation;
import com.nonxedy.nonchat.moderation.ViolationDetector;
import com.nonxedy.nonchat.moderation.ViolationType;

/**
 * Detects excessive capitalization using the {@link CapsFilter} built from the current config.
 * The filter is read on every message, so config reloads apply immediately.
 */
public class CapsDetector implements ViolationDetector {

    private final ModerationConfig config;

    public CapsDetector(ModerationConfig config) {
        this.config = config;
    }

    @Override
    public String bypassPermission() {
        return "nonchat.caps.bypass";
    }

    @Override
    public Optional<Violation> inspect(Player player, String message) {
        if (!config.getCapsFilter().shouldFilter(message)) {
            return Optional.empty();
        }
        return Optional.of(new Violation(ViolationType.CAPS, player.getUniqueId(), message));
    }
}
