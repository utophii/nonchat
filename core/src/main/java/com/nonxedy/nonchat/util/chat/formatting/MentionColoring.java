package com.nonxedy.nonchat.util.chat.formatting;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Restores mention styling even when gradients/patterns split text into individual glyphs. */
public final class MentionColoring {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern AT_MENTION = Pattern.compile("@(\\w+)");
    private static final Pattern BARE_MENTION = Pattern.compile("@?\\b(\\w+)\\b");

    private MentionColoring() {}

    public static Component apply(Component message, String color, boolean allowBareNames,
                                  Predicate<String> isOnlinePlayer) {
        if (color == null || color.isEmpty()) {
            return message;
        }

        String visible = PLAIN.serialize(message);
        Matcher matcher = (allowBareNames ? BARE_MENTION : AT_MENTION).matcher(visible);
        BitSet highlights = new BitSet(visible.length());
        while (matcher.find()) {
            if (!allowBareNames || isOnlinePlayer.test(matcher.group(1))) {
                highlights.set(matcher.start(), matcher.end());
            }
        }
        if (highlights.isEmpty()) {
            return message;
        }

        // A color prefix may produce an empty root with styled children. Resolve
        // the effective style of a visible probe, rather than assuming root.color().
        Style style = firstTextStyle(ColorUtil.parseComponent(color + "x"), Style.empty());
        return style == null ? message : recolor(message, highlights, style, new int[] {0});
    }

    static Style firstTextStyle(Component component, Style inherited) {
        Style effective = inherited.merge(component.style(), Style.Merge.Strategy.ALWAYS);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            return effective;
        }
        for (Component child : component.children()) {
            Style found = firstTextStyle(child, effective);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Component recolor(Component component, BitSet highlights, Style style, int[] offset) {
        Component result = component.children(List.of());
        List<Component> children = new ArrayList<>();
        if (component instanceof TextComponent text) {
            String content = text.content();
            int start = 0;
            while (start < content.length()) {
                boolean highlighted = highlights.get(offset[0] + start);
                int end = start + 1;
                while (end < content.length() && highlights.get(offset[0] + end) == highlighted) {
                    end++;
                }
                Component part = Component.text(content.substring(start, end));
                if (highlighted) {
                    part = part.style(style);
                }
                children.add(part);
                start = end;
            }
            result = ((TextComponent) result).content("");
            offset[0] += content.length();
        } else {
            // Advance past visible non-text content without converting it to text
            // (e.g. a translatable item name). Hover contents are not traversed.
            offset[0] += PLAIN.serialize(result).length();
        }
        for (Component child : component.children()) {
            children.add(recolor(child, highlights, style, offset));
        }
        return result.children(children);
    }
}