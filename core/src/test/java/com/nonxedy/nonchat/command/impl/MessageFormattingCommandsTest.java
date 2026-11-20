package com.nonxedy.nonchat.command.impl;

import static com.nonxedy.nonchat.test.ComponentAssertions.glyphs;
import static com.nonxedy.nonchat.test.ComponentAssertions.plain;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

class MessageFormattingCommandsTest {
    private final Nonchat plugin = mock(Nonchat.class);
    private final Server server = mock(Server.class);
    private final PluginConfig config = mock(PluginConfig.class);
    private final PluginMessages messages = mock(PluginMessages.class);
    private final Player sender = mock(Player.class);
    private final Command command = mock(Command.class);
    private static final String INPUT = "<click:run_command:'/pay Ajneb 1000'>magic</click>";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void meCommandRespectsSenderFormattingPermissions(boolean allowed) {
        when(plugin.getServer()).thenReturn(server);
        when(config.isMeCommandEnabled()).thenReturn(true);
        when(config.getMeFormat()).thenReturn("<gray>action: {message}</gray>");
        when(sender.hasPermission("nonchat.me")).thenReturn(true);
        when(sender.hasPermission("nonchat.color")).thenReturn(true);
        when(sender.hasPermission("nonchat.format.click")).thenReturn(allowed);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            new MeCommand(plugin, config, messages).onCommand(sender, command, "me", INPUT.split(" "));
        }
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(server).broadcast(captor.capture());
        Component result = captor.getValue();
        var click = glyphs(result).get(plain(result).indexOf("magic")).style().clickEvent();
        assertEquals(allowed ? ClickEvent.runCommand("/pay Ajneb 1000") : null, click);
    }

    @Test
    void spyCopiesNeverReparseRawMessageTags() {
        when(plugin.getServer()).thenReturn(server);
        when(config.getSpyFormat()).thenReturn("<gray>{sender} -> {target}: {message}</gray>");
        when(messages.getString(anyString())).thenReturn("enabled");
        when(sender.getName()).thenReturn("Sender");
        Player target = mock(Player.class);
        when(target.getName()).thenReturn("Target");
        Player spy = mock(Player.class);
        UUID spyId = UUID.randomUUID();
        when(spy.getUniqueId()).thenReturn(spyId);
        when(spy.hasPermission("nonchat.spy")).thenReturn(true);
        when(spy.hasPermission("nonchat.spy.all")).thenReturn(true);
        when(server.getPlayer(spyId)).thenReturn(spy);
        SpyCommand handler = new SpyCommand(plugin, messages, config);
        handler.onCommand(spy, command, "spy", new String[0]);
        clearInvocations(spy);
        handler.onPrivateMessage(sender, target, Component.text(INPUT));
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(spy).sendMessage(captor.capture());
        assertEquals("Sender -> Target: " + INPUT, plain(captor.getValue()));
        assertTrue(glyphs(captor.getValue()).stream().allMatch(g -> g.style().clickEvent() == null));
    }
}
