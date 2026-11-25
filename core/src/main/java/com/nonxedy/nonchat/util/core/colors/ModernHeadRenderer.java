package com.nonxedy.nonchat.util.core.colors;

import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

/** Used when the server provides {@link Component#object(ObjectContents)}. */
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
