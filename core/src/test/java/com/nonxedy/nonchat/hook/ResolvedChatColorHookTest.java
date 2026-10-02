package com.nonxedy.nonchat.hook;

import static com.nonxedy.nonchat.test.ComponentAssertions.assertColor;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nonxedy.nonchat.test.ChatColorFixture;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

class ResolvedChatColorHookTest {
    public static final class Pattern {
        public List<?> colors = List.of("<red>", "<blue>");
        public List<?> getColors() { return colors; }
    }

    public static final class Api {
        public String tag;
        public Pattern pattern;
        public boolean fail;
        public Player lastPlayer;

        public String resolveActiveTag(Player player) {
            lastPlayer = player;
            if (fail) throw new IllegalStateException("resolver failed");
            return tag;
        }

        public Pattern resolveActivePattern(Player player) {
            lastPlayer = player;
            if (fail) throw new IllegalStateException("resolver failed");
            return pattern;
        }

        public Component applyColorToComponent(Player player, Component message) {
            throw new AssertionError("Native rendering must not call the old Adventure renderer");
        }
    }

    private ChatColorFixture f;
    private Api api;

    @BeforeEach
    void setUp() {
        f = new ChatColorFixture();
        api = new Api();
        when(f.dependency.getChatColorAPI()).thenReturn(api);
        f.hook.refresh();
    }

    @Test
    void prefersEffectiveSelectionResolversOverOldComponentRenderer() {
        api.tag = "<red>";
        Component output = f.hook.applyColor(f.player, Component.text("hello"));
        assertColor(output, "hello", NamedTextColor.RED);
        assertSame(f.player, api.lastPlayer);
        assertTrue(f.hook.isEnabled());
    }

    @Test
    void activePatternTakesPrecedenceOverTag() {
        api.tag = "<green>";
        api.pattern = new Pattern();
        Component output = f.hook.applyColor(f.player, Component.text("abc"));
        assertColor(output, "a", NamedTextColor.RED);
        assertColor(output, "b", NamedTextColor.BLUE);
        assertColor(output, "c", NamedTextColor.RED);
    }

    @Test
    void resolvesAgainForEveryMessageWithoutCachingSelection() {
        api.tag = "<red>";
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", NamedTextColor.RED);
        api.tag = "<blue>";
        assertColor(f.hook.applyColor(f.player, Component.text("hello")), "hello", NamedTextColor.BLUE);
        verify(f.dependency, times(1)).getChatColorAPI();
    }

    @Test
    void doesNotInventColorWhenResolversReturnNoSelection() {
        Component input = Component.text("hello");
        assertSame(input, f.hook.applyColor(f.player, input));
        assertTrue(f.hook.isEnabled());
    }

    @Test
    void resolverFailureFallsBackAndCanBeRetriedAfterReload() {
        api.fail = true;
        Component input = Component.text("hello");
        assertSame(input, f.hook.applyColor(f.player, input));
        assertSame(input, f.hook.applyColor(f.player, input));
        assertFalse(f.hook.isEnabled());
        verify(f.logger, times(1)).log(eq(Level.WARNING), anyString(), any(Throwable.class));
        api.fail = false;
        api.tag = "<green>";
        f.hook.refresh();
        assertColor(f.hook.applyColor(f.player, input), "hello", NamedTextColor.GREEN);
    }

    @Test
    void invalidPatternDataFallsBackWithoutCastingErrorsEscaping() {
        api.pattern = new Pattern();
        api.pattern.colors = List.of(123);
        Component input = Component.text("hello");
        assertSame(input, f.hook.applyColor(f.player, input));
        assertFalse(f.hook.isEnabled());
    }
}
