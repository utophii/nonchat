package com.nonxedy.nonchat.moderation;

import java.time.Duration;
import java.util.List;

/**
 * One sanction level of a {@link ViolationRule}
 * @param strikesRequired Number of valid strikes needed for the tier to fire
 * @param expire How long a strike keeps counting towards this tier
 * @param actions Actions to run: 'notify-staff' or console commands ('block' is implied and ignored)
 */
public record SanctionTier(int strikesRequired, Duration expire, List<String> actions) {
}
