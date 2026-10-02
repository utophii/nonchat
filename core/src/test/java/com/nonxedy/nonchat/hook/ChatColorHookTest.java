package com.nonxedy.nonchat.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.nonxedy.nonchat.test.ChatColorFixture;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

class ChatColorHookTest {
    private ChatColorFixture f;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        f = new ChatColorFixture();
        bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void worksWithoutChatColorOnTheClasspathOrServer() {
        when(f.manager.getPlugin("ChatColor")).thenReturn(null);
        f.hook.refresh();
        Component input = Component.text("hello");
        assertFalse(f.hook.isEnabled());
        assertSame(input, f.hook.applyColor(f.player, input));
    }

    @Test
    void doesNotBindDisabledPlugins() {
        when(f.dependency.isEnabled()).thenReturn(false);
        f.hook.refresh();
        assertFalse(f.hook.isEnabled());
        verify(f.dependency, times(0)).getChatColorAPI();
    }

    @Test
    void rejectsAnotherPluginAlsoNamedChatColor() {
        when(f.dependency.getDescription()).thenReturn(new PluginDescriptionFile("ChatColor", "1", "other.ChatColor"));
        f.hook.refresh();
        assertFalse(f.hook.isEnabled());
        verify(f.dependency, times(0)).getChatColorAPI();
    }

    @Test
    void registersLifecycleListenerAndUsesOnlyThePublicApi() {
        f.hook.register();
        assertTrue(f.hook.isEnabled());
        verify(f.manager).registerEvents(f.hook, f.nonchat);
        Component input = Component.text("hello");
        assertSame(input, f.hook.applyColor(f.player, input));
        assertSame(input, f.api.lastInput);
    }

    @Test
    void appliesAuthorizedSelectionWithoutNonchatColorPermission() {
        f.api.tag("<red>");
        f.hook.refresh();
        assertFalse(f.player.hasPermission("nonchat.color"));
        Component output = f.hook.applyColor(f.player, Component.text("hello"));
        assertEquals(NamedTextColor.RED, output.color());
    }

    @Test
    void preservesComponentsInsteadOfFlatteningToLegacyOrPlainText() {
        f.api.tag("<red>");
        f.hook.refresh();
        Component input = Component.text("link").decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl("https://example.com"))
                .hoverEvent(HoverEvent.showText(Component.text("hover")));
        Component output = f.hook.applyColor(f.player, input);
        assertEquals(input.clickEvent(), output.clickEvent());
        assertEquals(input.hoverEvent(), output.hoverEvent());
        assertEquals(TextDecoration.State.TRUE, output.decoration(TextDecoration.BOLD));
    }

    @Test
    void doesNotCachePlayerSelectionsOrRenderedMessages() {
        f.hook.refresh();
        f.api.tag("<red>");
        assertEquals(NamedTextColor.RED, f.hook.applyColor(f.player, Component.text("same")).color());
        f.api.tag("<blue>");
        assertEquals(NamedTextColor.BLUE, f.hook.applyColor(f.player, Component.text("same")).color());
        assertEquals(2, f.api.calls.get());
        verify(f.dependency, times(1)).getChatColorAPI();
    }

    @Test
    void respectsBothPluginsConfigurationAndCanRebindAfterReload() {
        f.config.set("integrations.chatcolor.enabled", false);
        f.hook.refresh();
        assertFalse(f.hook.isEnabled());
        f.config.set("integrations.chatcolor.enabled", true);
        f.hook.refresh();
        assertTrue(f.hook.isEnabled());
        f.dependencyConfig.set("settings.apply-to-message", false);
        Component input = Component.text("hello");
        assertSame(input, f.hook.applyColor(f.player, input));
        assertFalse(f.hook.isEnabled());
        f.dependencyConfig.set("settings.apply-to-message", true);
        assertTrue(f.hook.isEnabled());
        f.config.set("integrations.chatcolor.enabled", false);
        assertFalse(f.hook.isEnabled());
    }

    @Test
    void clearsOnDisableAndRebindsOnEnable() {
        f.hook.refresh();
        f.hook.onPluginDisable(new PluginDisableEvent(f.dependency));
        assertFalse(f.hook.isEnabled());
        f.hook.onPluginEnable(new PluginEnableEvent(f.dependency));
        assertTrue(f.hook.isEnabled());
    }

    @Test
    void ignoresLifecycleEventsOfOtherPlugins() {
        f.hook.refresh();
        Plugin other = mock(Plugin.class);
        when(other.getName()).thenReturn("Other");
        f.hook.onPluginDisable(new PluginDisableEvent(other));
        f.hook.onPluginEnable(new PluginEnableEvent(other));
        assertTrue(f.hook.isEnabled());
        verify(f.dependency, times(1)).getChatColorAPI();
    }

    @Test
    void fallsBackAndLogsOnceWhenApiThrows() {
        f.api.colorizer = (player, message) -> { throw new IllegalStateException("broken API"); };
        f.hook.refresh();
        Component input = Component.text("hello");
        assertSame(input, f.hook.applyColor(f.player, input));
        assertSame(input, f.hook.applyColor(f.player, input));
        assertFalse(f.hook.isEnabled());
        assertEquals(1, f.api.calls.get());
        verify(f.logger, times(1)).log(eq(Level.WARNING), anyString(), any(Throwable.class));
        f.api.tag("<green>");
        f.hook.refresh();
        assertEquals(NamedTextColor.GREEN, f.hook.applyColor(f.player, input).color());
    }

    @Test
    void fallsBackOnNullResultAndLinkageErrors() {
        f.hook.refresh();
        f.api.colorizer = (player, message) -> null;
        Component input = Component.text("hello");
        assertSame(input, f.hook.applyColor(f.player, input));
        assertFalse(f.hook.isEnabled());
        f.hook.refresh();
        f.api.colorizer = (player, message) -> { throw new NoClassDefFoundError("missing dependency"); };
        assertSame(input, f.hook.applyColor(f.player, input));
        assertFalse(f.hook.isEnabled());
    }

    @Test
    void refusesMissingOrIncompatibleApiWithoutDisablingNonchat() {
        when(f.dependency.getChatColorAPI()).thenReturn(new Object());
        f.hook.refresh();
        assertFalse(f.hook.isEnabled());
        when(f.dependency.getChatColorAPI()).thenReturn(new WrongApi());
        f.hook.refresh();
        assertFalse(f.hook.isEnabled());
        when(f.dependency.getChatColorAPI()).thenReturn(null);
        f.hook.refresh();
        assertFalse(f.hook.isEnabled());
    }

    @Test
    void clearsBindingOnNonchatShutdown() {
        f.hook.refresh();
        f.hook.close();
        assertFalse(f.hook.isEnabled());
        Component input = Component.text("hello");
        assertSame(input, f.hook.applyColor(f.player, input));
    }

    @Test
    void doesNotSwallowFatalErrorsThrownThroughReflection() {
        f.api.colorizer = (player, message) -> { throw new AssertionError("fatal"); };
        f.hook.refresh();
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
                () -> f.hook.applyColor(f.player, Component.text("hello")));
    }

    public static final class WrongApi {
        public String applyColorToComponent(Player player, Component component) {
            return "wrong return type";
        }
    }
}