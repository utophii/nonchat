package com.nonxedy.nonchat.moderation;

import java.util.List;

/**
 * How the system reacts to one {@link ViolationType}
 * @param type Violation this rule applies to
 * @param warnMessage Warning used when the tier has no message of its own (may be null or empty)
 * @param staffNotifyMessage Template for the 'notify-staff' action; {message} or %message% is replaced
 * @param staffNotifyPermission Permission needed to receive the staff notification (OPs always receive it)
 * @param consoleNotify Whether every violation is logged to the console
 * @param block Whether the message is blocked; false only reports the violation and lets the message through
 * @param tiers Sanction tiers, from lightest to heaviest
 */
public record ViolationRule(
        ViolationType type,
        String warnMessage,
        String staffNotifyMessage,
        String staffNotifyPermission,
        boolean consoleNotify,
        boolean block,
        List<SanctionTier> tiers) {
}
