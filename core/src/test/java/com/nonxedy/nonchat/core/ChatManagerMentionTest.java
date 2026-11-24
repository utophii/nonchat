package com.nonxedy.nonchat.core;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.Channel;
import com.nonxedy.nonchat.chat.channel.ChannelManager;
import com.nonxedy.nonchat.chat.channel.ResolvedChannelMessage;
import com.nonxedy.nonchat.command.impl.IgnoreCommand;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.moderation.SanctionsManager;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

class ChatManagerMentionTest {
    private static final String SOUND = "entity.experience_orb.pickup";
    private final Nonchat plugin = mock(Nonchat.class);
    private final PluginConfig config = mock(PluginConfig.class);
    private final IgnoreCommand ignore = mock(IgnoreCommand.class);
    private final Channel channel = mock(Channel.class);
    private final Player sender = player("Sender");
    private final Player ignoring = player("Ignoring");
    private final Player recipient = player("Recipient");
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ChannelManager> channels;
    private ChatManager manager;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(sender, ignoring, recipient));
        bukkit.when(() -> Bukkit.getPlayerExact("Ignoring")).thenReturn(ignoring);
        bukkit.when(() -> Bukkit.getPlayerExact("Recipient")).thenReturn(recipient);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong()))
                .thenReturn(mock(BukkitTask.class));

        when(plugin.getSanctionsManager()).thenReturn(mock(SanctionsManager.class));
        when(plugin.getIgnoreCommand()).thenReturn(ignore);
        when(ignore.isIgnoring(ignoring, sender)).thenReturn(true);
        when(config.isMentionsEnabled()).thenReturn(true);
        when(config.getMentionMessage()).thenReturn("message:mentioned");
        when(config.isMentionSoundEnabled()).thenReturn(true);
        when(config.getMentionSound()).thenReturn(SOUND);
        when(config.getMentionSoundVolume()).thenReturn(1.0f);
        when(config.getMentionSoundPitch()).thenReturn(1.0f);
        when(channel.getId()).thenReturn("mention-test");
        when(channel.isEnabled()).thenReturn(true);
        when(channel.isGlobal()).thenReturn(true);
        when(channel.canSend(sender)).thenReturn(true);
        when(channel.canReceive(any(Player.class))).thenReturn(true);
        when(channel.getFormat()).thenReturn("{message}");
        when(channel.formatMessage(eq(sender), anyString())).thenAnswer(invocation ->
                Component.text((String) invocation.getArgument(1)));
        channels = mockConstruction(ChannelManager.class, (mock, context) -> {
            when(mock.resolveChannelMessage(anyString(), eq(sender))).thenAnswer(invocation ->
                    new ResolvedChannelMessage(channel, invocation.getArgument(0), false));
            when(mock.canSendMessage(sender, channel)).thenReturn(true);
        });
        manager = new ChatManager(plugin, config, mock(PluginMessages.class));
    }

    @AfterEach
    void tearDown() {
        manager.cleanup();
        channels.close();
        bukkit.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"message:mentioned", "actionbar:mentioned", "title:mentioned", "bossbar:mentioned"})
    void ignoredSenderCannotTriggerAnyNotificationOrSound(String notification) {
        when(config.getMentionMessage()).thenReturn(notification);

        manager.processChat(sender, "hello @Ignoring");

        verifyNoNotificationOrSound(ignoring);
        verify(recipient).sendMessage(Component.text("hello @Ignoring"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mixedMentionsNotifyOnlyPlayersNotIgnoringSender(boolean bareNames) {
        when(config.isMentionWithoutAtEnabled()).thenReturn(bareNames);
        String message = bareNames ? "Ignoring Recipient" : "@Ignoring @Recipient";

        manager.processChat(sender, message);

        verifyNoNotificationOrSound(ignoring);
        verify(recipient).sendMessage(Component.text("mentioned"));
        verify(recipient).sendMessage(Component.text(message));
        verify(recipient).playSound(recipient.getLocation(), SOUND, 1.0f, 1.0f);
    }

    @Test
    void removingIgnoreRestoresMentionsImmediately() {
        manager.processChat(sender, "@Ignoring");
        verifyNoNotificationOrSound(ignoring);
        when(ignore.isIgnoring(ignoring, sender)).thenReturn(false);

        manager.processChat(sender, "@Ignoring");

        verify(ignoring).sendMessage(Component.text("mentioned"));
        verify(ignoring).sendMessage(Component.text("@Ignoring"));
        verify(ignoring).playSound(ignoring.getLocation(), SOUND, 1.0f, 1.0f);
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        return player;
    }

    private static void verifyNoNotificationOrSound(Player player) {
        verify(player, never()).sendMessage(any(Component.class));
        verify(player, never()).sendActionBar(any(Component.class));
        verify(player, never()).showTitle(any(Title.class));
        verify(player, never()).showBossBar(any(BossBar.class));
        verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
    }
}