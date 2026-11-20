package com.nonxedy.nonchat.util.core.colors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class MessageParser {

    private final String   message;
    private Player         player          = null;
    private boolean        allowFormatting = true;
    private boolean        cached          = false;
    private List<TagResolver> resolvers    = new ArrayList<>();

    public MessageParser(@NotNull String message) {
        this.message = message;
    }

    public MessageParser player(@Nullable Player player) {
        this.player = player;
        if (player != null) this.allowFormatting = player.hasPermission("nonchat.color");
        return this;
    }

    public MessageParser allowFormatting(boolean allow) {
        this.allowFormatting = allow;
        return this;
    }

    public MessageParser cached(boolean cached) {
        this.cached = cached;
        return this;
    }

    public MessageParser resolver(TagResolver... resolvers) {
        this.resolvers.addAll(Arrays.asList(resolvers));
        return this;
    }

    public @NotNull Component parse() {
        if (!allowFormatting) {
            return Component.text(ColorUtil.stripFormatting(message));
        }
        if (player != null) {
            return ColorUtil.parseComponent(message, player, resolvers.toArray(new TagResolver[0]));
        }
        if (cached && resolvers.isEmpty()) {
            return ColorUtil.parseComponentCached(message);
        }
        if (!resolvers.isEmpty()) {
            return ColorUtil.parseComponent(message, resolvers.toArray(new TagResolver[0]));
        }
        return ColorUtil.parseComponent(message);
    }
}
