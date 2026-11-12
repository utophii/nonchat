package com.nonxedy.nonchat.moderation;

import java.time.Duration;
import java.util.List;

/**
 * One sanction level of a {@link ViolationRule}
 * @param strikesRequired Number of valid strikes needed for the tier to fire
 * @param expire Inactivity required to reset progression after reaching this tier
 * @param actions Actions to run: 'notify-staff' or console commands ('block' is implied and ignored)
 * @param message Warning override, null inherits the rule's message, empty disables it
 */
public record SanctionTier(int strikesRequired, Duration expire, List<String> actions, String message) {
    public SanctionTier(int strikesRequired, Duration expire, List<String> actions) {
        this(strikesRequired, expire, actions, null);
    }
}
