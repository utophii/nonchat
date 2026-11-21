package com.nonxedy.nonchat.chat.channel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.api.Channel;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.util.AsyncConfigSaver;

class ChannelManagerTest {
    @TempDir
    Path dataFolder;

    private final Nonchat plugin = mock(Nonchat.class);
    private final PluginConfig config = mock(PluginConfig.class);
    private final Player player = mock(Player.class);
    private final YamlConfiguration yaml = new YamlConfiguration();
    private MockedConstruction<AsyncConfigSaver> savers;
    private ChannelManager manager;

    @BeforeEach
    void setUp() {
        // Configuration persistence is independent of active channel resolution.
        savers = mockConstruction(AsyncConfigSaver.class);
        yaml.set("default-channel", "global");
        yaml.set("channels.global.character", "!");
        yaml.set("channels.global.radius", -1);
        yaml.set("channels.staff.character", "@");
        yaml.set("channels.staff.radius", -1);
        when(config.getConfigurationSection(anyString())).thenAnswer(invocation ->
                yaml.getConfigurationSection(invocation.getArgument(0)));
        when(config.getString(anyString(), anyString())).thenAnswer(invocation ->
                yaml.getString(invocation.getArgument(0), invocation.getArgument(1)));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        manager = new ChannelManager(plugin, config);
        assertTrue(manager.setPlayerChannel(player, "staff"));
    }

    @AfterEach
    void tearDown() {
        savers.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void activeChannelUsesDisabledStateAfterReloadOrUpdate(boolean reload) {
        if (reload) {
            yaml.set("channels.staff.enabled", false);
            manager.loadChannels();
        } else {
            assertTrue(manager.updateChannel("staff", null, null, null, null, null,
                    null, false, null, null, null, null));
        }

        Channel channel = resolveUnprefixedMessage();
        assertEquals("staff", channel.getId());
        assertFalse(channel.isEnabled());
        assertSame(manager.getChannel("staff"), channel);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void activeChannelUsesNewPermissionsAndLimitsAfterReloadOrUpdate(boolean reload) {
        manager.recordMessageSent(player);
        if (reload) {
            yaml.set("channels.staff.send-permission", "staff.send");
            yaml.set("channels.staff.receive-permission", "staff.receive");
            yaml.set("channels.staff.radius", 12);
            yaml.set("channels.staff.cooldown", 60);
            yaml.set("channels.staff.min-length", 5);
            yaml.set("channels.staff.max-length", 20);
            yaml.set("channels.staff.format", "staff: {message}");
            manager.loadChannels();
        } else {
            assertTrue(manager.updateChannel("staff", null, "staff: {message}", null,
                    "staff.send", "staff.receive", 12, null, 60, 5, 20, null));
        }

        Channel channel = resolveUnprefixedMessage();
        assertFalse(channel.canSend(player));
        assertFalse(channel.canReceive(player));
        when(player.hasPermission("staff.send")).thenReturn(true);
        when(player.hasPermission("staff.receive")).thenReturn(true);
        assertTrue(channel.canSend(player));
        assertTrue(channel.canReceive(player));
        assertFalse(manager.canSendMessage(player, channel));
        assertEquals(5, channel.getMinLength());
        assertEquals(20, channel.getMaxLength());
        assertEquals("staff: {message}", channel.getFormat());

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Player recipient = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(recipient.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(recipient.getLocation()).thenReturn(new Location(world, 24, 0, 0));
        assertFalse(channel.isInRange(player, recipient));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removedActiveChannelFallsBackToDefault(boolean reload) {
        if (reload) {
            yaml.set("channels.staff", null);
            manager.loadChannels();
        } else {
            assertTrue(manager.deleteChannel("staff"));
        }

        assertSame(manager.getDefaultChannel(), manager.getPlayerChannel(player));
        assertSame(manager.getDefaultChannel(), resolveUnprefixedMessage());
    }

    @Test
    void reloadPreservesExplicitSelectionWhenDefaultChanges() {
        assertTrue(manager.setPlayerChannel(player, "global"));
        yaml.set("default-channel", "staff");

        manager.loadChannels();

        assertEquals("staff", manager.getDefaultChannel().getId());
        assertSame(manager.getChannel("global"), manager.getPlayerChannel(player));
    }

    @Test
    void cleanupResetsActiveChannelToDefault() {
        manager.cleanupPlayer(player);

        assertSame(manager.getDefaultChannel(), manager.getPlayerChannel(player));
    }

    @ParameterizedTest
    @ValueSource(strings = {"&aВы вошли в канал: staff", ""})
    void updatedSwitchMessageSurvivesReload(String switchMessage) throws Exception {
        enablePersistence();
        yaml.set("channels.staff.switch-message", "old message");
        manager.loadChannels();

        assertTrue(manager.updateChannel("staff", null, null, null, null, null,
                null, null, null, null, null, switchMessage));
        reloadSavedChannels();

        assertEquals(switchMessage, manager.getChannel("staff").getSwitchMessage());
        assertEquals(!switchMessage.isEmpty(), manager.getChannel("staff").hasSwitchMessage());
    }

    @Test
    void updatingOtherSettingsPreservesSwitchMessage() throws Exception {
        enablePersistence();
        yaml.set("channels.staff.switch-message", "existing message");
        manager.loadChannels();

        assertTrue(manager.updateChannel("staff", "Staff channel", null, null, null, null,
                null, null, null, null, null, null));
        reloadSavedChannels();

        assertEquals("existing message", manager.getChannel("staff").getSwitchMessage());
        assertEquals("Staff channel", manager.getChannel("staff").getDisplayName());
    }

    @Test
    void createdChannelSwitchMessageSurvivesReload() throws Exception {
        enablePersistence();

        assertNotNull(manager.createChannel("trade", "Trade", "{message}", "$", "", "",
                -1, 0, 0, 256, "&aTrading channel"));
        reloadSavedChannels();

        assertEquals("&aTrading channel", manager.getChannel("trade").getSwitchMessage());
    }

    private void enablePersistence() {
        // apply the writes requested by ChannelManager and persist real YAML without a background thread
        AsyncConfigSaver saver = savers.constructed().getFirst();
        doAnswer(invocation -> {
            yaml.set(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(saver).saveAsync(anyString(), any());
        doAnswer(invocation -> {
            yaml.save(dataFolder.resolve("config.yml").toFile());
            return null;
        }).when(saver).saveNow();
    }

    private void reloadSavedChannels() throws Exception {
        yaml.load(dataFolder.resolve("config.yml").toFile());
        manager.loadChannels();
    }

    private Channel resolveUnprefixedMessage() {
        ResolvedChannelMessage resolved = manager.resolveChannelMessage("hello", player);
        assertNotNull(resolved);
        assertEquals("hello", resolved.message());
        return resolved.channel();
    }
}
