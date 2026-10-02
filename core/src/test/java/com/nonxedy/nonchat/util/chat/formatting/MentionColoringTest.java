package com.nonxedy.nonchat.util.chat.formatting;

import static com.nonxedy.nonchat.test.ComponentAssertions.assertColor;
import static com.nonxedy.nonchat.test.ComponentAssertions.glyphs;
import static com.nonxedy.nonchat.test.ComponentAssertions.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

class MentionColoringTest {
    @Test
    void highlightsAWholeMentionAcrossGradientComponents() {
        Component input = MiniMessage.miniMessage().deserialize("<gradient:red:blue>hi @Alex end</gradient>");
        Component result = MentionColoring.apply(input, "&#FFAFFB", false, name -> false);
        assertEquals(plain(input), plain(result));
        assertColor(result, "@Alex", TextColor.color(0xffaffb));
        assertEquals(glyphs(input).get(0).style(), glyphs(result).get(0).style());
        int end = plain(result).indexOf("end");
        assertEquals(glyphs(input).get(end).style(), glyphs(result).get(end).style());
    }

    @Test
    void preservesEventsDecorationsAndHoverContent() {
        HoverEvent<Component> hover = HoverEvent.showText(Component.text("@Alex inside hover", NamedTextColor.GREEN));
        ClickEvent click = ClickEvent.suggestCommand("/msg Alex ");
        Component input = Component.text("@Alex", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .hoverEvent(hover).clickEvent(click);
        Component result = MentionColoring.apply(input, "&#FFAFFB", false, name -> true);
        assertColor(result, "@Alex", TextColor.color(0xffaffb));
        for (var glyph : glyphs(result)) {
            assertEquals(click, glyph.style().clickEvent());
            assertEquals(hover, glyph.style().hoverEvent());
            assertEquals(TextDecoration.State.TRUE, glyph.style().decoration(TextDecoration.BOLD));
        }
    }

    @Test
    void bareNamesOnlyHighlightOnlinePlayers() {
        Component input = Component.text("Alex ordinary @Alex offline", NamedTextColor.BLUE);
        Component result = MentionColoring.apply(input, "<red>", true, "Alex"::equals);
        assertColor(result, "Alex", NamedTextColor.RED);
        assertColor(result, "@Alex", NamedTextColor.RED);
        assertColor(result, "ordinary", NamedTextColor.BLUE);
        assertColor(result, "offline", NamedTextColor.BLUE);
    }

    @Test
    void accountsForNonTextComponentsBeforeMentionsWithoutFlatteningThem() {
        Component item = Component.translatable("item.minecraft.diamond").hoverEvent(
                HoverEvent.showText(Component.text("item hover")));
        Component input = item.append(Component.text(" @Alex tail", NamedTextColor.BLUE));
        Component result = MentionColoring.apply(input, "<red>", false, name -> true);
        assertEquals(item.getClass(), result.getClass());
        assertEquals(item.hoverEvent(), result.hoverEvent());
        assertEquals(plain(input), plain(result));
        assertColor(result, "@Alex", NamedTextColor.RED);
        assertColor(result, "tail", NamedTextColor.BLUE);
    }

    @Test
    void keepsRepeatedMentionsAndTextAfterThemCorrectlyStyled() {
        Component input = Component.text("@Alex then @Alex!", NamedTextColor.BLUE);
        Component result = MentionColoring.apply(input, "&c", false, name -> false);
        assertEquals(plain(input), plain(result));
        assertColor(result, "@Alex", NamedTextColor.RED);
        assertColor(result, "then", NamedTextColor.BLUE);
        assertEquals(NamedTextColor.RED, glyphs(result).get(plain(result).lastIndexOf("@Alex")).style().color());
        assertColor(result, "!", NamedTextColor.BLUE);
    }

    @Test
    void returnsOriginalComponentWhenThereIsNoHighlight() {
        Component input = Component.text("hello");
        assertSame(input, MentionColoring.apply(input, "<red>", false, name -> true));
        assertSame(input, MentionColoring.apply(input, "", true, name -> true));
    }
}