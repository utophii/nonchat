package com.nonxedy.nonchat.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.ChannelAPI;
import com.nonxedy.nonchat.api.IPlatformAdapter;
import com.nonxedy.nonchat.api.event.NonchatChannelMessageEvent;
import com.nonxedy.nonchat.chat.channel.BaseChannel;
import com.nonxedy.nonchat.chat.channel.ChannelManager;
import com.nonxedy.nonchat.chat.channel.ResolvedChannelMessage;
import com.nonxedy.nonchat.command.impl.IgnoreCommand;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.hook.ChatColorHook;
import com.nonxedy.nonchat.moderation.SanctionsManager;

import net.kyori.adventure.text.Component;

class ChatManagerEventTest {
    private static final String CHANNEL = "event-test";
    private static final String SOUND = "entity.experience_orb.pickup";
    private final Nonchat plugin = mock(Nonchat.class);
    private final PluginConfig config = mock(PluginConfig.class);
    private final PluginMessages messages = mock(PluginMessages.class);
    private final PluginManager plugins = mock(PluginManager.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    private final SanctionsManager sanctions = mock(SanctionsManager.class);
    private final IgnoreCommand ignore = mock(IgnoreCommand.class);
    private final IPlatformAdapter adapter = mock(IPlatformAdapter.class);
    private final BaseChannel channel = mock(BaseChannel.class);
    private final Player sender = player("Sender");
    private final Player recipient = player("Recipient");
    private final Player other = player("Other");
    private final List<NonchatChannelMessageEvent> events = new ArrayList<>();
    private Consumer<NonchatChannelMessageEvent> listener = event -> {};
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ChannelManager> channels;
    private ChatManager manager;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(sender, recipient, other));
        bukkit.when(() -> Bukkit.getPlayerExact("Recipient")).thenReturn(recipient);
        bukkit.when(() -> Bukkit.getPlayerExact("Other")).thenReturn(other);
        doAnswer(invocation -> {
            NonchatChannelMessageEvent event = invocation.getArgument(0);
            events.add(event);
            listener.accept(event);
            return null;
        }).when(plugins).callEvent(any(NonchatChannelMessageEvent.class));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        });
        when(plugin.getSanctionsManager()).thenReturn(sanctions);
        when(plugin.getIgnoreCommand()).thenReturn(ignore);
        when(plugin.getPlatformAdapter()).thenReturn(adapter);
        when(messages.getString(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(config.isMentionsEnabled()).thenReturn(true);
        when(config.getMentionMessage()).thenReturn("actionbar:mentioned");
        when(config.isMentionSoundEnabled()).thenReturn(true);
        when(config.getMentionSound()).thenReturn(SOUND);
        when(config.getMentionSoundVolume()).thenReturn(1.0f);
        when(config.getMentionSoundPitch()).thenReturn(1.0f);
        when(config.isChatBubblesEnabled()).thenReturn(true);
        when(config.getChatBubblesDuration()).thenReturn(5);
        when(config.isUndeliveredMessageNotificationEnabled()).thenReturn(true);
        when(sender.hasPermission("nonchat.chatbubbles")).thenReturn(true);
        when(adapter.supportsChatBubbles()).thenReturn(true);
        doReturn(List.of(new Object())).when(adapter).spawnMultilineBubble(eq(sender), anyString(),
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), anyDouble(), nullable(String.class));
        when(channel.getId()).thenReturn(CHANNEL);
        when(channel.getDisplayName()).thenReturn("Event test");
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
        manager = new ChatManager(plugin, config, messages);
    }

    @AfterEach
    void tearDown() {
        manager.cleanup();
        ChannelAPI.cleanupChannel(CHANNEL);
        channels.close();
        bukkit.close();
    }

    @Test
    void unchangedEventPreservesDeliveryMentionsAndBubbles() {
        manager.processChat(sender, "hello @Recipient");

        assertEquals(1, events.size());
        NonchatChannelMessageEvent event = events.getFirst();
        assertSame(sender, event.getPlayer());
        assertSame(channel, event.getChannel());
        assertEquals("hello @Recipient", event.getMessage());
        assertEquals(Set.of(sender, recipient, other), event.getRecipients());
        verify(sender).sendMessage(Component.text("hello @Recipient"));
        verify(recipient).sendMessage(Component.text("hello @Recipient"));
        verify(other).sendMessage(Component.text("hello @Recipient"));
        verify(console).sendMessage(Component.text("hello @Recipient"));
        verify(recipient).sendActionBar(Component.text("mentioned"));
        verify(recipient).playSound(recipient.getLocation(), SOUND, 1.0f, 1.0f);
        verifyBubble("hello @Recipient");
    }

    @Test
    void cancellationStopsChatConsoleMentionsAndBubbles() {
        listener = event -> event.setCancelled(true);

        manager.processChat(sender, "@Recipient");

        assertEquals(1, events.size());
        verify(sender, never()).sendMessage(any(Component.class));
        verify(recipient, never()).sendMessage(any(Component.class));
        verify(other, never()).sendMessage(any(Component.class));
        verifyNoInteractions(console, adapter);
        verifyNoMention(recipient);
        verify(channel, never()).formatMessage(any(), anyString());
    }

    @Test
    void eventReceivesProcessorOutputAndReplacementReachesAllOutputs() {
        ChannelAPI.registerMessageProcessor(CHANNEL, (player, message) -> "processed @Other");
        listener = event -> {
            assertEquals("processed @Other", event.getMessage());
            verify(sanctions).process(sender, "original");
            verifyNoInteractions(console);
            verifyNoMention(other);
            event.setMessage("edited @Recipient");
        };

        manager.processChat(sender, "original");

        assertEquals(1, events.size());
        verify(recipient).sendMessage(Component.text("edited @Recipient"));
        verify(other).sendMessage(Component.text("edited @Recipient"));
        verify(console).sendMessage(Component.text("edited @Recipient"));
        verify(recipient).sendActionBar(Component.text("mentioned"));
        verifyNoMention(other);
        verifyBubble("edited @Recipient");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eventEditsAreColoredAfterDispatchAndChatColorStillGetsPlainBody(boolean chatColor) {
        when(config.isMentionColoringEnabled()).thenReturn(true);
        when(config.getMentionColor()).thenReturn("&a");
        ChatColorHook hook = mock(ChatColorHook.class);
        when(plugin.getChatColorHook()).thenReturn(hook);
        when(hook.isEnabled()).thenReturn(chatColor);
        listener = event -> {
            assertEquals("@Other", event.getMessage());
            event.setMessage("@Recipient");
        };

        manager.processChat(sender, "@Other");

        assertEquals(1, events.size());
        verify(channel).formatMessage(sender, chatColor ? "@Recipient" : "&a@Recipient&r");
        verify(console).sendMessage(Component.text("@Recipient"));
        verifyNoMention(other);
    }

    @Test
    void initialRecipientsPreserveRangePermissionsIgnoreAndSpyRules() {
        Player spy = player("Spy");
        Player ignoringSpy = player("IgnoringSpy");
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(sender, recipient, other, spy, ignoringSpy));
        when(channel.isGlobal()).thenReturn(false);
        when(channel.isInRange(sender, sender)).thenReturn(true);
        when(channel.isInRange(sender, other)).thenReturn(true);
        when(channel.canReceive(other)).thenReturn(false);
        when(spy.hasPermission("nonchat.spy.all")).thenReturn(true);
        when(ignoringSpy.hasPermission("nonchat.spy.all")).thenReturn(true);
        when(ignore.isIgnoring(ignoringSpy, sender)).thenReturn(true);

        manager.processChat(sender, "hello");

        assertEquals(1, events.size());
        assertEquals(Set.of(sender, spy), events.getFirst().getRecipients());
        verify(spy).sendMessage(Component.text("hello"));
        verify(recipient, never()).sendMessage(any(Component.class));
        verify(other, never()).sendMessage(any(Component.class));
        verify(ignoringSpy, never()).sendMessage(any(Component.class));
    }

    @Test
    void removedRecipientsGetNeitherChatNorMentionNotifications() {
        listener = event -> event.getRecipients().remove(recipient);

        manager.processChat(sender, "@Recipient @Other");

        assertEquals(1, events.size());
        verify(recipient, never()).sendMessage(any(Component.class));
        verifyNoMention(recipient);
        verify(other).sendMessage(Component.text("@Recipient @Other"));
        verify(other).sendActionBar(Component.text("mentioned"));
    }

    @Test
    void explicitlyAddedRecipientReceivesMessageOutsideNormalChannelRestrictions() {
        when(channel.canReceive(other)).thenReturn(false);
        listener = event -> {
            assertFalse(event.getRecipients().contains(other));
            event.getRecipients().add(other);
        };

        manager.processChat(sender, "hello");

        assertEquals(1, events.size());
        verify(other).sendMessage(Component.text("hello"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyOrSenderOnlyRecipientsSuppressBubblesAndReportNonDelivery(boolean keepSender) {
        listener = event -> {
            event.getRecipients().clear();
            if (keepSender) event.getRecipients().add(sender);
        };

        manager.processChat(sender, "@Recipient");

        assertEquals(1, events.size());
        verify(sender).sendMessage(Component.text("message-not-delivered"));
        verify(recipient, never()).sendMessage(any(Component.class));
        verifyNoMention(recipient);
        verifyNoInteractions(adapter);
        verify(console).sendMessage(Component.text("@Recipient"));
    }

    @Test
    void singleRecipientCountsAsDeliveredWhenSenderIsRemoved() {
        listener = event -> {
            event.getRecipients().clear();
            event.getRecipients().add(recipient);
        };

        manager.processChat(sender, "hello");

        assertEquals(1, events.size());
        verify(recipient).sendMessage(Component.text("hello"));
        verify(sender, never()).sendMessage(any(Component.class));
        verifyBubble("hello");
    }

    @Test
    void nullAndOfflineRecipientsAreSkipped() {
        when(other.isOnline()).thenReturn(false);
        listener = event -> event.getRecipients().add(null);

        manager.processChat(sender, "hello");

        assertEquals(1, events.size());
        verify(recipient).sendMessage(Component.text("hello"));
        verify(other, never()).sendMessage(any(Component.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"moderation", "disabled", "permission", "cooldown", "short", "long", "filter", "processor"})
    void blockedMessagesNeverReachEvent(String reason) {
        switch (reason) {
            case "moderation" -> when(sanctions.process(sender, "hello")).thenReturn(true);
            case "disabled" -> when(channel.isEnabled()).thenReturn(false);
            case "permission" -> when(channel.canSend(sender)).thenReturn(false);
            case "cooldown" -> when(channels.constructed().getFirst().canSendMessage(sender, channel)).thenReturn(false);
            case "short" -> when(channel.getMinLength()).thenReturn(6);
            case "long" -> when(channel.getMaxLength()).thenReturn(4);
            case "filter" -> ChannelAPI.registerMessageFilter(CHANNEL, (player, message) -> true);
            case "processor" -> ChannelAPI.registerMessageProcessor(CHANNEL, (player, message) -> null);
            default -> fail("Unexpected case: " + reason);
        }

        manager.processChat(sender, "hello");

        assertTrue(events.isEmpty());
        verify(recipient, never()).sendMessage(any(Component.class));
        verifyNoInteractions(console, adapter);
    }

    @Test
    void asynchronousChatDispatchesSynchronousEventOnServerThread() {
        List<Runnable> tasks = new ArrayList<>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(config.isChatBubblesEnabled()).thenReturn(false);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

        manager.processChat(sender, "hello");

        assertTrue(events.isEmpty());
        assertEquals(1, tasks.size());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        tasks.getFirst().run();
        assertEquals(1, events.size());
        assertFalse(events.getFirst().isAsynchronous());
        verify(recipient).sendMessage(Component.text("hello"));
    }

    private void verifyBubble(String message) {
        verify(adapter).spawnMultilineBubble(eq(sender), eq(message), any(Location.class),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), nullable(String.class));
    }

    private static void verifyNoMention(Player player) {
        verify(player, never()).sendActionBar(any(Component.class));
        verify(player, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenAnswer(invocation -> new Location(null, 0, 0, 0));
        return player;
    }
}