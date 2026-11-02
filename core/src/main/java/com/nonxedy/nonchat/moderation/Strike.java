package com.nonxedy.nonchat.moderation;

import java.time.Instant;
import java.util.UUID;

/**
 * A warning issued to a player for a violation
 * @param playerId Player who was warned
 * @param type Kind of violation the strike was issued for
 * @param issuedAt When the strike was issued
 * @param expiresAt When the strike stops counting
 */
public record Strike(UUID playerId, ViolationType type, Instant issuedAt, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
