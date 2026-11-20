package com.nonxedy.nonchat.util.core.colors;

import static com.nonxedy.nonchat.test.ComponentAssertions.assertColor;
import static com.nonxedy.nonchat.test.ComponentAssertions.glyphs;
import static com.nonxedy.nonchat.test.ComponentAssertions.plain;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

class MessageFormattingPermissionsTest {
    private final Player sender = mock(Player.class);
    private static final String CLICK = "<click:run_command:'/pay Ajneb 1000'>magic</click>";

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void checksEachInteractiveGroupIndependently(int permissions) {
        when(sender.hasPermission("nonchat.color")).thenReturn(true);
        when(sender.hasPermission("nonchat.format.click")).thenReturn((permissions & 1) != 0);
        when(sender.hasPermission("nonchat.format.hover")).thenReturn((permissions & 2) != 0);
        when(sender.hasPermission("nonchat.format.insertion")).thenReturn((permissions & 4) != 0);
        Component result = ColorUtil.parseComponent("<insert:secret><hover:show_text:'hint'>" + CLICK
                + "</hover></insert>", sender);
        var style = glyphs(result).get(plain(result).indexOf("magic")).style();
        assertEquals((permissions & 1) != 0, style.clickEvent() != null);
        assertEquals((permissions & 2) != 0, style.hoverEvent() != null);
        assertEquals((permissions & 4) != 0, style.insertion() != null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<click:run_command:/op_Ajneb>magic</click>",
            "<CLICK:run_command:'/op Ajneb'>magic</CLICK>",
            "<click:suggest_command:'/op Ajneb'>magic</click>",
            "<click:copy_to_clipboard:secret>magic</click>",
            "<hover:show_text:'<click:run_command:/op>hint</click>'>magic</hover>",
            "<insert:secret>magic</insert>",
            "<font:custom:font>magic</font>",
            "<selector:@a>", "<nbt:entity:'@s':Pos>", "<newline>",
            "\\<click:run_command:'/op Ajneb'>magic</click>",
            "<click:run_command:'/op Ajneb>magic"
    })
    void colorPermissionDoesNotEnableSpecialTags(String input) {
        when(sender.hasPermission("nonchat.color")).thenReturn(true);
        Component result = ColorUtil.parseComponent(input, sender);
        assertEquals(input.replace("\\<", "<"), plain(result));
        for (var glyph : glyphs(result)) {
            assertNull(glyph.style().clickEvent());
            assertNull(glyph.style().hoverEvent());
            assertNull(glyph.style().insertion());
            assertNull(glyph.style().font());
        }
    }

    @Test
    void keepsColorsDecorationsAndGradients() {
        when(sender.hasPermission("nonchat.color")).thenReturn(true);
        Component result = ColorUtil.parseComponent("&a<bold>green</bold> &#123456hex <gradient:red:blue>abcd</gradient>", sender);
        assertEquals("green hex abcd", plain(result));
        assertColor(result, "green", NamedTextColor.GREEN);
        assertEquals(TextDecoration.State.TRUE, glyphs(result).get(0).style().decoration(TextDecoration.BOLD));
        assertNotEquals(glyphs(result).get(10).style().color(), glyphs(result).get(13).style().color());
    }

    @Test
    void interactivePermissionsStillRequireColorPermission() {
        when(sender.hasPermission("nonchat.format.click")).thenReturn(true);
        Component result = ColorUtil.parseComponent("&a<bold>" + CLICK + "</bold>", sender);
        assertEquals("magic", plain(result));
        assertNull(glyphs(result).get(0).style().color());
        assertNull(glyphs(result).get(0).style().clickEvent());
    }

    @Test
    void hoverPermissionDoesNotEnableClickEventsInsideHoverText() {
        when(sender.hasPermission("nonchat.color")).thenReturn(true);
        when(sender.hasPermission("nonchat.format.hover")).thenReturn(true);
        Component result = ColorUtil.parseComponent("<hover:show_text:'<click:run_command:/op>hint</click>'>magic</hover>", sender);
        Component hover = (Component) glyphs(result).get(0).style().hoverEvent().value();
        assertTrue(glyphs(hover).stream().allMatch(g -> g.style().clickEvent() == null));
    }

    @Test
    void permissionsAreNotCachedAcrossPlayersOrPermissionChanges() {
        when(sender.hasPermission("nonchat.color")).thenReturn(true);
        when(sender.hasPermission("nonchat.format.click")).thenReturn(true);
        assertEquals(ClickEvent.runCommand("/pay Ajneb 1000"),
                glyphs(ColorUtil.parseComponent(CLICK, sender)).get(0).style().clickEvent());
        when(sender.hasPermission("nonchat.format.click")).thenReturn(false);
        Component result = ColorUtil.parser(CLICK).player(sender).cached(true).parse();
        assertTrue(glyphs(result).stream().allMatch(g -> g.style().clickEvent() == null));
        result = ColorUtil.parseComponent("<custom>" + CLICK, sender,
                Placeholder.parsed("custom", "<click:run_command:/op>"));
        assertTrue(glyphs(result).stream().allMatch(g -> g.style().clickEvent() == null));
    }

    @Test
    void separatesTrustedTemplateFromPlayerText() {
        when(sender.hasPermission("nonchat.color")).thenReturn(true);
        when(sender.getName()).thenReturn("Sender");
        String format = "<click:suggest_command:/msg>%player%</click> <green>%message%</green> tail";
        Component result = ColorUtil.parseChatFormat(format, sender, "</green>" + CLICK);
        assertEquals(ClickEvent.suggestCommand("/msg"), glyphs(result).get(0).style().clickEvent());
        assertNull(glyphs(result).get(plain(result).indexOf("magic")).style().clickEvent());
        assertColor(result, "magic", NamedTextColor.GREEN);
        assertNull(glyphs(result).get(plain(result).indexOf("tail")).style().clickEvent());
        assertNotNull(glyphs(ColorUtil.parseComponent(CLICK)).get(0).style().clickEvent());
    }
}
