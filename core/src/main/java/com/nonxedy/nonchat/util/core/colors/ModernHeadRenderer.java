package com.nonxedy.nonchat.util.core.colors;

import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

/**
 * Renders player-head object contents.
 * Compiled against modern Adventure APIs (4.17+) and loaded only when the
 * running server provides them ({@code ObjectContents} exists). Referenced from
 * {@link ColorUtil} exclusively behind {@code MODERN_HEAD_RENDERER_AVAILABLE},
 * so this class must never be touched directly from older-API code paths.
 */
final class ModernHeadRenderer {

    private ModernHeadRenderer() {
    }

    static Component render(String rawTarget, boolean showHat) {
        PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead().hat(showHat);

        UUID uuid = tryParseUuid(rawTarget);
        if (uuid != null) {
            builder.id(uuid);
        } else if (looksLikeTexturePath(rawTarget)) {
            builder.texture(parseTextureKey(rawTarget));
        } else {
            builder.name(rawTarget);
        }

        return Component.object(builder.build());
    }

    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean looksLikeTexturePath(String value) {
        return value.indexOf('/') >= 0 || value.indexOf(':') >= 0;
    }

    private static Key parseTextureKey(String value) {
        return value.indexOf(':') >= 0 ? Key.key(value) : Key.key("minecraft", value);
    }
}
