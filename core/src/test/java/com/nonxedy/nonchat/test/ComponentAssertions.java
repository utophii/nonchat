package com.nonxedy.nonchat.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ComponentAssertions {
    private ComponentAssertions() {}

    public record Glyph(char character, Style style) {}

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static List<Glyph> glyphs(Component component) {
        List<Glyph> result = new ArrayList<>();
        collect(component, Style.empty(), result);
        return result;
    }

    public static void assertColor(Component message, String token, TextColor expected) {
        int start = plain(message).indexOf(token);
        assertTrue(start >= 0, "Token not present: " + token);
        List<Glyph> glyphs = glyphs(message);
        for (int i = start; i < start + token.length(); i++) {
            assertEquals(expected, glyphs.get(i).style().color(), "Color at index " + i + " in " + plain(message));
        }
    }

    private static void collect(Component component, Style inherited, List<Glyph> result) {
        Style effective = inherited.merge(component.style(), Style.Merge.Strategy.ALWAYS);
        String own = component instanceof TextComponent text ? text.content() : plain(component.children(List.of()));
        for (char character : own.toCharArray()) {
            result.add(new Glyph(character, effective));
        }
        for (Component child : component.children()) {
            collect(child, effective, result);
        }
    }
}