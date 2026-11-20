package com.nonxedy.nonchat.command.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.moderation.SanctionsManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class MeCommandTest {
    private final Nonchat plugin = mock(Nonchat.class);
    private final Server server = mock(Server.class);
    private final PluginConfig config = mock(PluginConfig.class);
    private final PluginMessages messages = mock(PluginMessages.class);
    private final SanctionsManager sanctions = mock(SanctionsManager.class);
    private final IgnoreCommand ignore = mock(IgnoreCommand.class);
    private final Player sender = mock(Player.class);
    private final Player recipient = mock(Player.class);
    private final Player ignoring = mock(Player.class);
    private final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    private final Command command = mock(Command.class);

    @BeforeEach
    void setUp() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getSanctionsManager()).thenReturn(sanctions);
        when(plugin.getIgnoreCommand()).thenReturn(ignore);
        when(server.getConsoleSender()).thenReturn(console);
        doReturn(List.of(sender, recipient, ignoring)).when(server).getOnlinePlayers();
        when(config.isMeCommandEnabled()).thenReturn(true);
        when(config.getMeFormat()).thenReturn("<gray>action: {message}</gray>");
        when(sender.hasPermission("nonchat.me")).thenReturn(true);
        when(ignore.isIgnoring(ignoring, sender)).thenReturn(true);
    }

    @Test
    void blockedMessageIsNotDeliveredToPlayersOrConsole() {
        when(sanctions.process(sender, "blocked message")).thenReturn(true);

        execute("blocked", "message");

        verify(sanctions).process(sender, "blocked message");
        verify(sender, never()).sendMessage(any(Component.class));
        verifyNoInteractions(recipient, ignoring, console, ignore);
        verify(server, never()).broadcast(any(Component.class));
    }

    @Test
    void allowedMessageReachesSenderConsoleAndOnlyRecipientsNotIgnoringSender() {
        execute("waves", "hello");

        verify(sanctions).process(sender, "waves hello");
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(recipient).sendMessage(captor.capture());
        Component message = captor.getValue();
        assertEquals("action: waves hello", PlainTextComponentSerializer.plainText().serialize(message));
        verify(sender).sendMessage(message);
        verify(console).sendMessage(message);
        verify(ignoring, never()).sendMessage(any(Component.class));
        verify(server, never()).broadcast(any(Component.class));
    }

    @Test
    void emptyCommandDoesNotRunModerationOrDeliverAnAction() {
        when(messages.getString("invalid-usage-me")).thenReturn("usage");

        execute();

        verifyNoInteractions(sanctions, recipient, ignoring, console, ignore);
        verify(sender).sendMessage(any(Component.class));
        verify(server, never()).broadcast(any(Component.class));
    }

    private void execute(String... args) {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            new MeCommand(plugin, config, messages).onCommand(sender, command, "me", args);
        }
    }
}
