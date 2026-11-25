package com.nonxedy.nonchat.core;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import com.nonxedy.nonchat.api.IPlatformAdapter;
import com.nonxedy.nonchat.chat.channel.ChannelManager;
import com.nonxedy.nonchat.chat.channel.ResolvedChannelMessage;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.moderation.SanctionsManager;

import net.kyori.adventure.text.Component;

class ChatManagerBubbleTest {
    private final Nonchat plugin = mock(Nonchat.class);
    private final PluginConfig config = mock(PluginConfig.class);
    private final IPlatformAdapter adapter = mock(IPlatformAdapter.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final Player sender = mock(Player.class);
    private final Channel channel = mock(Channel.class);
    private final List<List<?>> spawned = new ArrayList<>();
    private final Queue<ScheduledRemoval> removals = new PriorityQueue<>(Comparator.comparingLong(ScheduledRemoval::tick));
    private long currentTick;
    private boolean failNextSchedule;
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ChannelManager> channels;
    private ChatManager manager;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
        Player recipient = mock(Player.class);
        when(recipient.isOnline()).thenReturn(true);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(sender, recipient));
        when(sender.getUniqueId()).thenReturn(UUID.randomUUID());
        when(sender.isOnline()).thenReturn(true);
        when(sender.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(sender.getLocation()).thenAnswer(invocation -> new Location(null, 0, 0, 0));
        when(sender.hasPermission("nonchat.chatbubbles")).thenReturn(true);
        when(plugin.getSanctionsManager()).thenReturn(mock(SanctionsManager.class));
        when(plugin.getPlatformAdapter()).thenReturn(adapter);
        when(config.isChatBubblesEnabled()).thenReturn(true);
        when(config.getChatBubblesDuration()).thenReturn(5);
        when(adapter.supportsChatBubbles()).thenReturn(true);
        when(adapter.spawnMultilineBubble(eq(sender), anyString(), any(Location.class),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), nullable(String.class)))
                .thenAnswer(invocation -> {
                    List<?> bubble = List.of(new Object());
                    spawned.add(bubble);
                    return bubble;
                });
        when(channel.getId()).thenReturn("bubble-test");
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
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            if (failNextSchedule) {
                failNextSchedule = false;
                throw new IllegalArgumentException("Scheduling failed");
            }
            removals.add(new ScheduledRemoval(currentTick + invocation.<Long>getArgument(2), invocation.getArgument(1)));
            return mock(BukkitTask.class);
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
    @ValueSource(booleans = {false, true})
    void replacementKeepsItsFullLifetimeEvenWhenOldTimerUsesFallback(boolean fallback) {
        failNextSchedule = fallback;
        manager.processChat(sender, "first");
        advanceTo(80);
        manager.processChat(sender, "second");
        verify(adapter).removeBubbles(spawned.get(0));

        advanceTo(100);
        verify(adapter, never()).removeBubbles(spawned.get(1));
        advanceTo(179);
        verify(adapter, never()).removeBubbles(spawned.get(1));
        advanceTo(180);
        verify(adapter).removeBubbles(spawned.get(1));
    }

    @Test
    void singleBubbleExpiresAfterConfiguredDuration() {
        manager.processChat(sender, "hello");

        advanceTo(99);
        verify(adapter, never()).removeBubbles(any());
        advanceTo(100);
        verify(adapter).removeBubbles(spawned.getFirst());
    }

    @Test
    void oldTimerDoesNotRemoveBubbleCreatedAfterPlayerCleanup() {
        manager.processChat(sender, "before leaving");
        manager.cleanupPlayer(sender);
        advanceTo(80);
        manager.processChat(sender, "after rejoining");

        advanceTo(100);
        verify(adapter).removeBubbles(spawned.get(0));
        verify(adapter, never()).removeBubbles(spawned.get(1));
        advanceTo(180);
        verify(adapter).removeBubbles(spawned.get(1));
    }

    @Test
    void timerDoesNotRemoveAlreadyCleanedUpBubbleAgain() {
        manager.processChat(sender, "hello");
        manager.cleanupPlayer(sender);

        advanceTo(100);

        verify(adapter).removeBubbles(spawned.getFirst());
    }

    private void advanceTo(long tick) {
        while (!removals.isEmpty() && removals.peek().tick() <= tick) {
            ScheduledRemoval removal = removals.remove();
            currentTick = removal.tick();
            removal.action().run();
        }
        currentTick = tick;
    }

    private record ScheduledRemoval(long tick, Runnable action) {}
}
