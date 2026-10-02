package com.nonxedy.nonchat.util.chat.formatting;

import static com.nonxedy.nonchat.test.ComponentAssertions.assertColor;
import static com.nonxedy.nonchat.test.ComponentAssertions.glyphs;
import static com.nonxedy.nonchat.test.ComponentAssertions.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

class ChatColorRendererTest {
    @Test
    void noSelectionDoesNotChangeInput() {
        Component input = Component.text("hello");
        assertSame(input, ChatColorRenderer.applyTag(null, input));
        assertSame(input, ChatColorRenderer.applyTag("NONE", input));
        assertSame(input, ChatColorRenderer.applyPattern(List.of(), input));
    }

    @Test
    void appliesSolidAndLegacyTagsWithoutReparsingUserText() {
        Component input = Component.text("<blue>literal</blue>");
        Component output = ChatColorRenderer.applyTag("red", input);
        assertEquals(plain(input), plain(output));
        assertColor(output, "literal", NamedTextColor.RED);
        assertColor(ChatColorRenderer.applyTag("&c", Component.text("hello")), "hello", NamedTextColor.RED);
        assertColor(ChatColorRenderer.applyTag("#12ab34", Component.text("hello")), "hello", TextColor.color(0x12ab34));
    }

    @Test
    void rendersGradientAcrossChildrenAndPreservesExplicitColors() {
        Component input = Component.text("a").append(Component.text("b")).append(Component.text("c"));
        Component output = ChatColorRenderer.applyTag("<gradient:#ff0000:#0000ff>", input);
        assertColor(output, "a", TextColor.color(0xff0000));
        assertColor(output, "c", TextColor.color(0x0000ff));
        Component protectedInput = Component.text("plain ").append(Component.text("protected", NamedTextColor.GREEN));
        output = ChatColorRenderer.applyTag("<red>", protectedInput);
        assertColor(output, "plain", NamedTextColor.RED);
        assertColor(output, "protected", NamedTextColor.GREEN);
    }

    @Test
    void patternContinuesAcrossChildrenAndSkipsSpaces() {
        Component input = Component.text("a ").append(Component.text("bc"));
        Component output = ChatColorRenderer.applyPattern(List.of("<red>", "<blue>"), input);
        assertEquals("a bc", plain(output));
        assertColor(output, "a", NamedTextColor.RED);
        assertColor(output, "b", NamedTextColor.BLUE);
        assertColor(output, "c", NamedTextColor.RED);
    }

    @Test
    void patternKeepsUnicodeCodePointsIntact() {
        Component output = ChatColorRenderer.applyPattern(List.of("<red>", "<blue>"), Component.text("a😀b"));
        assertEquals("a😀b", plain(output));
        assertColor(output, "😀", NamedTextColor.BLUE);
        assertColor(output, "b", NamedTextColor.RED);
    }

    @Test
    void patternKeepsEventsAndDecorationsAndDoesNotColorHoverText() {
        HoverEvent<Component> hover = HoverEvent.showText(Component.text("do not color this", NamedTextColor.GREEN));
        ClickEvent click = ClickEvent.openUrl("https://example.com");
        Component input = Component.text("abc", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                .hoverEvent(hover).clickEvent(click);
        Component output = ChatColorRenderer.applyPattern(List.of("<red>", "<blue>"), input);
        assertColor(output, "a", NamedTextColor.RED);
        assertColor(output, "b", NamedTextColor.BLUE);
        for (var glyph : glyphs(output)) {
            assertEquals(hover, glyph.style().hoverEvent());
            assertEquals(click, glyph.style().clickEvent());
            assertEquals(TextDecoration.State.TRUE, glyph.style().decoration(TextDecoration.BOLD));
        }
    }

    @Test
    void keepsNonTextComponentsAsComponents() {
        Component input = Component.translatable("item.minecraft.diamond")
                .hoverEvent(HoverEvent.showText(Component.text("item")))
                .append(Component.text("body"));
        Component output = ChatColorRenderer.applyPattern(List.of("<red>"), input);
        assertEquals(input.getClass(), output.getClass());
        assertEquals(input.hoverEvent(), output.hoverEvent());
        assertEquals(plain(input), plain(output));
        assertColor(output, "body", NamedTextColor.RED);
    }
}