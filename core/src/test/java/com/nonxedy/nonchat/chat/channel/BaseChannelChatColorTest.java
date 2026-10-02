package com.nonxedy.nonchat.chat.channel;

import static com.nonxedy.nonchat.test.ComponentAssertions.assertColor;
import static com.nonxedy.nonchat.test.ComponentAssertions.glyphs;
import static com.nonxedy.nonchat.test.ComponentAssertions.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.nonxedy.nonchat.api.InteractivePlaceholder;
import com.nonxedy.nonchat.test.ChatColorFixture;
import com.nonxedy.nonchat.util.chat.formatting.HoverTextUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

class BaseChannelChatColorTest {
    private ChatColorFixture f;
    private MockedStatic<Bukkit> bukkit;
    private HoverTextUtil hover;
    private static final String FORMAT = "&8[L] &aSender &8» &f{message}&6!";

    @BeforeEach
    void setUp() {
        f = new ChatColorFixture();
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(f.manager);
        hover = mock(HoverTextUtil.class);
        when(hover.createHoverableText(anyString(), any(Player.class))).thenAnswer(invocation ->
                Component.text((String) invocation.getArgument(0))
                        .hoverEvent(HoverEvent.showText(Component.text("sender info")))
                        .clickEvent(ClickEvent.suggestCommand("/msg Sender ")));
        when(hover.addHoverToComponent(any(Component.class), any(Player.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        f.hook.refresh();
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private BaseChannel channel(String format) {
        return new BaseChannel("local", "Local", format, "", "", "", 100, true, hover, 0, 1, 256);
    }

    @Test
    void appliesToBodyOnlyAndDoesNotLetFormatWhiteMaskSelection() {
        f.api.tag("<red>");
        Component result = channel(FORMAT).formatMessage(f.player, "hello");
        assertEquals("[L] Sender » hello!", plain(result));
        assertColor(result, "hello", NamedTextColor.RED);
        assertColor(result, "[L]", NamedTextColor.DARK_GRAY);
        assertColor(result, "Sender", NamedTextColor.GREEN);
        assertColor(result, "!", NamedTextColor.GOLD);
        assertEquals("hello", plain(f.api.lastInput));
        assertNull(f.api.lastInput.color(), "The format's inherited white must not enter the API");
        assertEquals(1, f.api.calls.get());
    }

    @Test
    void preservesFormatColorWhenNoSelectionOrDefaultApplies() {
        Component result = channel(FORMAT).formatMessage(f.player, "hello");
        assertColor(result, "hello", NamedTextColor.WHITE);
    }

    @Test
    void supportsExactHexGradients() {
        f.api.tag("<gradient:#ff0000:#0000ff>");
        Component result = channel(FORMAT).formatMessage(f.player, "abc");
        assertColor(result, "a", TextColor.color(0xff0000));
        assertColor(result, "c", TextColor.color(0x0000ff));
        assertColor(result, "Sender", NamedTextColor.GREEN);
    }

    @Test
    void supportsPatternsThroughTheSameApi() {
        pattern();
        Component result = channel(FORMAT).formatMessage(f.player, "abc");
        assertColor(result, "a", NamedTextColor.RED);
        assertColor(result, "b", NamedTextColor.BLUE);
        assertColor(result, "c", NamedTextColor.RED);
    }

    @Test
    void appliesSelectionInsideSpanningFormatGradientWithoutColoringPrefixOrSuffix() {
        f.api.tag("<red>");
        Component result = channel("<gradient:#00ff00:#0000ff>Sender: {message}!</gradient>")
                .formatMessage(f.player, "abc");
        assertEquals("Sender: abc!", plain(result));
        assertColor(result, "abc", NamedTextColor.RED);
        assertColor(result, "S", TextColor.color(0x00ff00));
        assertColor(result, "!", TextColor.color(0x0000ff));
    }

    @Test
    void preservesSpanningFormatGradientWhenNoChatColorSelectionApplies() {
        Component result = channel("<gradient:#ff0000:#0000ff>{message}</gradient>").formatMessage(f.player, "abc");
        assertColor(result, "a", TextColor.color(0xff0000));
        assertColor(result, "c", TextColor.color(0x0000ff));
    }

    @Test
    void keepsManualFormattingRestrictedButStillAppliesSelection() {
        f.api.tag("<red>");
        Component result = channel(FORMAT).formatMessage(f.player, "&a<bold>hello</bold>");
        assertEquals("[L] Sender » hello!", plain(result));
        assertColor(result, "hello", NamedTextColor.RED);
        int index = plain(result).indexOf("hello");
        assertTrue(glyphs(result).get(index).style().decoration(TextDecoration.BOLD) != TextDecoration.State.TRUE);
    }

    @Test
    void preservesExplicitColorsAndDecorationsForPermittedPlayers() {
        when(f.player.hasPermission("nonchat.color")).thenReturn(true);
        f.api.tag("<red>");
        Component result = channel(FORMAT).formatMessage(f.player, "hello <green><bold>custom</bold></green>");
        assertColor(result, "hello", NamedTextColor.RED);
        assertColor(result, "custom", NamedTextColor.GREEN);
        int index = plain(result).indexOf("custom");
        assertEquals(TextDecoration.State.TRUE, glyphs(result).get(index).style().decoration(TextDecoration.BOLD));
    }

    @Test
    void retainsLinkClickAndHoverEvents() {
        f.api.tag("<red>");
        Component result = channel(FORMAT).formatMessage(f.player, "visit https://example.com");
        int index = plain(result).indexOf("https://example.com");
        assertEquals(ClickEvent.openUrl("https://example.com"), glyphs(result).get(index).style().clickEvent());
        assertNotNull(glyphs(result).get(index).style().hoverEvent());
        assertColor(result, "https://example.com", NamedTextColor.RED);
    }

    @Test
    void preservesInteractivePlaceholderComponents() {
        f.config.set("interactive-placeholders.enabled", true);
        InteractivePlaceholder placeholder = mock(InteractivePlaceholder.class);
        when(placeholder.getPlaceholder()).thenReturn("loc");
        when(placeholder.isEnabled()).thenReturn(true);
        Component interactive = Component.text("[coords]", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("coordinates")))
                .clickEvent(ClickEvent.suggestCommand("/tp 1 2 3"));
        when(placeholder.process(eq(f.player), any(String[].class))).thenReturn(interactive);
        f.nonchat.getPlaceholderManager().registerPlaceholder(placeholder);
        f.api.tag("<red>");
        Component result = channel(FORMAT).formatMessage(f.player, "here [loc] end");
        assertEquals("[L] Sender » here [coords] end!", plain(result));
        assertColor(result, "here", NamedTextColor.RED);
        assertColor(result, " end", NamedTextColor.RED);
        assertColor(result, "[coords]", NamedTextColor.YELLOW);
        int index = plain(result).indexOf("[coords]");
        assertEquals(interactive.clickEvent(), glyphs(result).get(index).style().clickEvent());
        assertEquals(interactive.hoverEvent(), glyphs(result).get(index).style().hoverEvent());
    }

    @Test
    void restoresMentionHighlightAcrossPatternGlyphsForBothPermissionStates() {
        f.config.set("mention-colors.enabled", true);
        f.config.set("mention-colors.color", "&#FFAFFB");
        pattern();
        for (boolean permitted : List.of(false, true)) {
            when(f.player.hasPermission("nonchat.color")).thenReturn(permitted);
            Component result = channel(FORMAT).formatMessage(f.player, "hi @Alex end");
            assertColor(result, "@Alex", TextColor.color(0xffaffb));
            assertTrue(!TextColor.color(0xffaffb).equals(glyphs(result).get(plain(result).indexOf("end")).style().color()));
            assertEquals("[L] Sender » hi @Alex end!", plain(result));
        }
    }

    @Test
    void attachesSenderHoverToFormatNameNotANameInTheBody() {
        f.api.tag("<red>");
        Component result = channel(FORMAT).formatMessage(f.player, "Sender says hello");
        int nameIndex = plain(result).indexOf("Sender");
        int bodyIndex = plain(result).indexOf("Sender", nameIndex + 1);
        assertNotNull(glyphs(result).get(nameIndex).style().hoverEvent());
        assertNull(glyphs(result).get(bodyIndex).style().hoverEvent());
        assertColor(result, "says", NamedTextColor.RED);
    }

    @Test
    void supportsRepeatedMessagePlaceholdersWithoutRepeatedApiCalls() {
        f.api.tag("<red>");
        Component result = channel("&7{message} &8/ &f{message}").formatMessage(f.player, "hello");
        assertEquals("hello / hello", plain(result));
        assertEquals(NamedTextColor.RED, glyphs(result).get(plain(result).lastIndexOf("hello")).style().color());
        assertEquals(1, f.api.calls.get());
    }

    @Test
    void usesNormalFormattingIfApiFailsOrIntegrationIsDisabled() {
        f.api.colorizer = (player, message) -> { throw new IllegalStateException("failed"); };
        Component result = channel(FORMAT).formatMessage(f.player, "hello");
        assertEquals("[L] Sender » hello!", plain(result));
        assertColor(result, "hello", NamedTextColor.WHITE);
        f.api.tag("<red>");
        f.hook.refresh();
        f.config.set("integrations.chatcolor.enabled", false);
        result = channel(FORMAT).formatMessage(f.player, "hello");
        assertColor(result, "hello", NamedTextColor.WHITE);
    }

    @Test
    void stillIncludesMessageWhenOldFormatHasNoMessagePlaceholder() {
        f.api.tag("<red>");
        Component result = channel("&aSender: &f").formatMessage(f.player, "hello");
        assertEquals("Sender: hello", plain(result));
        assertColor(result, "hello", NamedTextColor.RED);
    }

    private void pattern() {
        f.api.colorizer = (player, component) -> {
            AtomicInteger index = new AtomicInteger();
            return component.replaceText(TextReplacementConfig.builder().match(".|\\s")
                    .replacement((match, builder) -> Component.text(match.group(),
                            index.getAndIncrement() % 2 == 0 ? NamedTextColor.RED : NamedTextColor.BLUE)).build());
        };
    }
}