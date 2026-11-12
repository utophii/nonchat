package com.nonxedy.nonchat.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.event.NonchatPrivateMessageEvent;
import com.nonxedy.nonchat.api.event.NonchatPrivateMessageSentEvent;
import com.nonxedy.nonchat.command.impl.IgnoreCommand;
import com.nonxedy.nonchat.command.impl.SpyCommand;
import com.nonxedy.nonchat.config.ModerationConfig;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.moderation.SanctionsManager;
import com.nonxedy.nonchat.service.ConfigService;

import io.papermc.paper.plugin.configuration.PluginMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class MessageManagerTest {

    @TempDir
    Path dataFolder;

    private final Nonchat plugin = mock(Nonchat.class);
    private final PluginMessages messages = mock(PluginMessages.class);
    private final SpyCommand spy = mock(SpyCommand.class);
    private final IgnoreCommand ignore = mock(IgnoreCommand.class);
    private final PluginManager pluginManager = mock(PluginManager.class);
    private final List<Event> firedEvents = new ArrayList<>();
    private MockedStatic<Bukkit> bukkit;
    private MessageManager manager;

    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("test"));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        doAnswer(invocation -> firedEvents.add(invocation.getArgument(0)))
                .when(pluginManager).callEvent(any(Event.class));

        PluginMeta meta = mock(PluginMeta.class);
        when(meta.getVersion()).thenReturn("test");
        when(plugin.getPluginMeta()).thenReturn(meta);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        ConfigService configService = mock(ConfigService.class);
        when(configService.getMessages()).thenReturn(messages);
        when(plugin.getConfigService()).thenReturn(configService);

        // Messages resolve to their own key so tests can assert which one was sent
        when(messages.getString(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        alice = player("Alice", "world");
        bob = player("Bob", "world");
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private void start(Consumer<YamlConfiguration> configure) throws Exception {
        start(configure, moderation -> {});
    }

    /** Creates the manager with a moderation config where every chat filter is off unless the test turns it on. */
    private void start(Consumer<YamlConfiguration> configure, Consumer<YamlConfiguration> configureModeration)
            throws Exception {
        YamlConfiguration moderationYaml = new YamlConfiguration();
        moderationYaml.set("spam.enabled", false);
        moderationYaml.set("advertising.enabled", false);
        moderationYaml.set("caps.enabled", false);
        moderationYaml.set("banned-words.enabled", false);
        configureModeration.accept(moderationYaml);
        File moderationFile = new File(dataFolder.toFile(), "moderation.yml");
        moderationYaml.save(moderationFile);
        // The bundled template is the file itself, so the updater has nothing to add
        when(plugin.getResource("moderation.yml")).thenAnswer(invocation -> new FileInputStream(moderationFile));
        ModerationConfig moderation = new ModerationConfig(plugin, messages);
        moderation.load();
        when(plugin.getSanctionsManager()).thenReturn(SanctionsManager.createDefault(moderation));

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("private-chat.sound", "");
        configure.accept(yaml);
        File configFile = new File(dataFolder.toFile(), "config.yml");
        yaml.save(configFile);
        when(plugin.getResource("config.yml")).thenAnswer(invocation -> new FileInputStream(configFile));

        PluginConfig config = new PluginConfig(plugin);
        manager = new MessageManager(plugin, config, messages, spy);
        manager.setIgnoreCommand(ignore);
    }

    private void start() throws Exception {
        start(yaml -> {});
    }

    private static Player player(String name, String worldName) {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        moveTo(player, worldName);
        return player;
    }

    private static void moveTo(Player player, String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        when(player.getWorld()).thenReturn(world);
    }

    private static List<String> received(CommandSender recipient) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(recipient, atLeast(0)).sendMessage(captor.capture());
        return captor.getAllValues().stream()
                .map(component -> PlainTextComponentSerializer.plainText().serialize(component))
                .toList();
    }

    private <T extends Event> List<T> fired(Class<T> type) {
        return firedEvents.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void deliversMessageToBothSidesAndUpdatesReplyTargets() throws Exception {
        start();

        manager.sendPrivateMessage(alice, bob, "hello", false);

        assertEquals(1, received(alice).size());
        assertTrue(received(alice).get(0).contains("hello"));
        assertEquals(1, received(bob).size());
        assertTrue(received(bob).get(0).contains("hello"));

        assertEquals(1, fired(NonchatPrivateMessageEvent.class).size());
        assertEquals(1, fired(NonchatPrivateMessageSentEvent.class).size());
        verify(spy).onPrivateMessage(eq(alice), eq(bob), any(Component.class));

        assertEquals(bob.getUniqueId(), manager.getLastMessageSender().get(alice.getUniqueId()));
        assertEquals(alice.getUniqueId(), manager.getLastMessageSender().get(bob.getUniqueId()));
    }

    @Test
    void refusesWhenPrivateChatIsDisabled() throws Exception {
        start(yaml -> yaml.set("private-chat.enabled", false));

        manager.sendPrivateMessage(alice, bob, "hello", false);

        assertEquals(List.of("command-disabled"), received(alice));
        assertTrue(received(bob).isEmpty());
        assertTrue(firedEvents.isEmpty());
    }

    @Test
    void hidesPlayersOutsideTheWorldScope() throws Exception {
        start(yaml -> yaml.set("private-chat.world-separation.enabled", true));
        moveTo(bob, "world_nether");

        manager.sendPrivateMessage(alice, bob, "hello", false);

        assertEquals(List.of("player-not-found"), received(alice));
        assertTrue(received(bob).isEmpty());
        assertTrue(firedEvents.isEmpty());
    }

    @Test
    void deliversBetweenWorldsOfTheSameGroup() throws Exception {
        start(yaml -> {
            yaml.set("private-chat.world-separation.enabled", true);
            yaml.set("private-chat.world-separation.groups.main", List.of("world", "world_nether"));
        });
        moveTo(bob, "WORLD_NETHER");

        manager.sendPrivateMessage(alice, bob, "hello", false);

        assertEquals(1, received(bob).size());
    }

    @Test
    void refusesWhenReceiverIgnoresSender() throws Exception {
        start();
        when(ignore.isIgnoring(bob, alice)).thenReturn(true);

        manager.sendPrivateMessage(alice, bob, "hello", false);

        assertEquals(List.of("ignored-by-target"), received(alice));
        assertTrue(received(bob).isEmpty());
    }

    @Test
    void refusesWhenSenderIgnoresReceiver() throws Exception {
        start();
        when(ignore.isIgnoring(alice, bob)).thenReturn(true);

        manager.sendPrivateMessage(alice, bob, "hello", false);

        assertEquals(List.of("you-are-ignoring-player"), received(alice));
        assertTrue(received(bob).isEmpty());
    }

    @Test
    void cancelledEventStopsDelivery() throws Exception {
        start();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof NonchatPrivateMessageEvent event) {
                event.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        manager.sendPrivateMessage(alice, bob, "hello", false);

        assertTrue(received(alice).isEmpty());
        assertTrue(received(bob).isEmpty());
        assertNull(manager.getLastMessageSender().get(alice.getUniqueId()));
    }

    @Test
    void eventCanRedirectAndRewriteMessage() throws Exception {
        start();
        Player carol = player("Carol", "world");
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof NonchatPrivateMessageEvent event) {
                event.setReceiver(carol);
                event.setMessage("rewritten");
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        manager.sendPrivateMessage(alice, bob, "hello", false);

        assertTrue(received(bob).isEmpty());
        assertEquals(1, received(carol).size());
        assertTrue(received(carol).get(0).contains("rewritten"));
    }

    @Test
    void blockedWordStopsDelivery() throws Exception {
        start(yaml -> {}, moderation -> {
            moderation.set("banned-words.enabled", true);
            moderation.set("banned-words.words", List.of("badword"));
            moderation.set("banned-words.console-notify", false);
        });

        manager.sendPrivateMessage(alice, bob, "this is a badword", false);

        assertTrue(received(bob).isEmpty());
        assertTrue(fired(NonchatPrivateMessageSentEvent.class).isEmpty());
    }

    @Test
    void stripsColorsWithoutPermission() throws Exception {
        start();

        manager.sendPrivateMessage(alice, bob, "&cred text", false);

        String text = received(bob).get(0);
        assertTrue(text.contains("red text"));
        assertFalse(text.contains("&c"));
        assertEquals("red text", fired(NonchatPrivateMessageSentEvent.class).get(0).getMessage());
    }

    @Test
    void consoleSenderDeliversWithoutReplyTargets() throws Exception {
        start();
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.getName()).thenReturn("CONSOLE");

        manager.sendPrivateMessage(console, bob, "hello", false);

        assertEquals(1, received(bob).size());
        assertEquals(1, received(console).size());
        assertTrue(manager.getLastMessageSender().isEmpty());
        verifyNoInteractions(spy);
    }

    @Test
    void notifiesSenderWhenReceiverIsOffline() throws Exception {
        start();
        when(bob.isOnline()).thenReturn(false);

        manager.sendPrivateMessage(alice, bob, "hello", false);

        assertEquals(List.of("message-not-delivered"), received(alice));
        assertTrue(received(bob).isEmpty());
    }

    @Test
    void replyWithoutTargetIsRefused() throws Exception {
        start();

        manager.replyToLastMessage(alice, "hi");

        assertEquals(List.of("no-reply-target"), received(alice));
    }

    @Test
    void replyGoesBackToLastConversationPartner() throws Exception {
        start();
        bukkit.when(() -> Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);

        manager.sendPrivateMessage(alice, bob, "hello", false);
        manager.replyToLastMessage(bob, "hi back");

        assertEquals(2, received(alice).size());
        assertTrue(received(alice).get(1).contains("hi back"));
        List<NonchatPrivateMessageEvent> events = fired(NonchatPrivateMessageEvent.class);
        assertFalse(events.get(0).isReply());
        assertTrue(events.get(1).isReply());
    }

    @Test
    void replyToOfflinePlayerIsRefused() throws Exception {
        start();

        manager.sendPrivateMessage(alice, bob, "hello", false);
        // Bukkit.getPlayer returns null: Alice has left the server
        manager.replyToLastMessage(bob, "hi back");

        assertEquals(List.of("player-offline"), received(bob).subList(1, 2));
    }

    @Test
    void replyRechecksWorldScopeAfterPlayerMoved() throws Exception {
        start(yaml -> yaml.set("private-chat.world-separation.enabled", true));
        bukkit.when(() -> Bukkit.getPlayer(alice.getUniqueId())).thenReturn(alice);

        manager.sendPrivateMessage(alice, bob, "hello", false);
        moveTo(alice, "world_nether");
        manager.replyToLastMessage(bob, "hi back");

        assertEquals("player-not-found", received(bob).get(1));
        assertEquals(1, received(alice).size());
    }
}
