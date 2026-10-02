package com.nonxedy.nonchat.util.chat.formatting;

import java.util.ArrayList;
import java.util.List;

import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Renders selections resolved by ChatColor using nonchat's Adventure API.
 * This avoids calling ChatColor's Adventure-4-compiled serializer on Adventure 5.
 * No permission, default or player-data resolution is duplicated here.
 */
public final class ChatColorRenderer {
    private ChatColorRenderer() {}

    public static Component applyTag(String tag, Component message) {
        if (tag == null || tag.isBlank() || "NONE".equalsIgnoreCase(tag)) {
            return message;
        }
        // Public API tags are MiniMessage. Accept the legacy/bare forms that
        // ChatColor's own normalizer also supports for older stored selections.
        String normalized = tag.startsWith("<") || tag.startsWith("&") || tag.startsWith("§")
                ? tag : "<" + tag + ">";
        return ColorUtil.parseComponent(normalized + "<nonchat_color_body>",
                Placeholder.component("nonchat_color_body", message));
    }

    public static Component applyPattern(List<String> colors, Component message) {
        if (colors == null || colors.isEmpty()) {
            return message;
        }
        List<Style> styles = new ArrayList<>(colors.size());
        for (String color : colors) {
            Style style = MentionColoring.firstTextStyle(applyTag(color, Component.text("x")), Style.empty());
            styles.add(style == null ? Style.empty() : style);
        }
        return applyPattern(message, styles, new int[] {0});
    }

    private static Component applyPattern(Component component, List<Style> styles, int[] index) {
        Component result = component.children(List.of());
        List<Component> children = new ArrayList<>();
        if (component instanceof TextComponent text) {
            result = ((TextComponent) result).content("");
            String content = text.content();
            for (int offset = 0; offset < content.length();) {
                int codePoint = content.codePointAt(offset);
                int length = Character.charCount(codePoint);
                Component glyph = Component.text(content.substring(offset, offset + length));
                if (codePoint != ' ') {
                    glyph = glyph.style(styles.get(index[0]++ % styles.size()));
                }
                children.add(glyph);
                offset += length;
            }
        }
        // Traverse visible children only, not hover text. Keep non-text components,
        // decorations and interactivity as components instead of serializing them.
        for (Component child : component.children()) {
            children.add(applyPattern(child, styles, index));
        }
        return result.children(children);
    }
}