package com.nonxedy.nonchat.util.chat.filters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.config.PluginMessages;

class SpamDetectorTest {

    private MockedStatic<Bukkit> bukkit;
    private PluginConfig config;
    private PluginMessages messages;
    private YamlConfiguration yaml;

    @BeforeEach
    void setUp() {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.isPluginEnabled("PlaceholderAPI")).thenReturn(false);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("test"));

        yaml = new YamlConfiguration();
        for (String type : List.of("repetitive", "similar", "flood")) {
            yaml.set("anti-spam." + type + ".message", "");
            yaml.set("anti-spam." + type + ".notify-message", "");
            yaml.set("anti-spam." + type + ".console-notify", false);
            yaml.set("anti-spam." + type + ".actions", List.of("block"));
        }

        Nonchat plugin = mock(Nonchat.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        config = mock(PluginConfig.class);
        messages = mock(PluginMessages.class);
        when(config.getConfig()).thenReturn(yaml);
        when(config.getPlugin()).thenReturn(plugin);
        when(messages.getString(anyString())).thenReturn("");

        when(config.isAntiSpamEnabled()).thenReturn(true);
        when(config.isAntiSpamRepetitiveEnabled()).thenReturn(true);
        when(config.isAntiSpamSimilarEnabled()).thenReturn(true);
        when(config.isAntiSpamFloodEnabled()).thenReturn(true);

        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(3);
        when(config.getAntiSpamRepetitiveTimeWindow()).thenReturn(60);
        when(config.getAntiSpamSimilarThreshold()).thenReturn(0.85);
        when(config.getAntiSpamSimilarTimeWindow()).thenReturn(60);
        when(config.getAntiSpamFloodMaxMessages()).thenReturn(5);
        when(config.getAntiSpamFloodTimeWindow()).thenReturn(60);

        when(config.getAntiSpamRepetitiveMessage()).thenReturn("");
        when(config.getAntiSpamSimilarMessage()).thenReturn("");
        when(config.getAntiSpamFloodMessage()).thenReturn("");
        when(config.isAntiSpamConsoleNotifyEnabled()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private SpamDetector detector() {
        return new SpamDetector(config, messages);
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");
        when(player.hasPermission("nonchat.spam.bypass")).thenReturn(false);
        return player;
    }

    @Test
    void bypassesPlayersWithPermission() {
        Player player = player();
        when(player.hasPermission("nonchat.spam.bypass")).thenReturn(true);
        SpamDetector detector = detector();

        assertFalse(detector.shouldFilter(player, "greg is a microwave"));
        assertFalse(detector.shouldFilter(player, "greg is a microwave"));
        assertFalse(detector.shouldFilter(player, "greg is a microwave"));
        assertFalse(detector.shouldFilter(player, "greg is a microwave"));
    }

    @Test
    void blocksRepetitiveMessagesAtThreshold() {
        when(config.isAntiSpamSimilarEnabled()).thenReturn(false);
        when(config.isAntiSpamFloodEnabled()).thenReturn(false);
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(3);

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(detector.shouldFilter(player, "who ate my sandwich"));
        assertFalse(detector.shouldFilter(player, "who ate my sandwich"));
        assertFalse(detector.shouldFilter(player, "who ate my sandwich"));
        assertTrue(detector.shouldFilter(player, "who ate my sandwich"));
    }

    @Test
    void similarDetectionIgnoresMentionsAndFormatting() {
        when(config.isAntiSpamRepetitiveEnabled()).thenReturn(false);
        when(config.isAntiSpamFloodEnabled()).thenReturn(false);

        // @Alex/@Alec stay ~0.96 similar raw; without stripping they would trip the 0.85 threshold.
        bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(mock(Player.class));
        bukkit.when(() -> Bukkit.getPlayerExact("Alec")).thenReturn(mock(Player.class));

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(detector.shouldFilter(player, "@Alex i have committed tax evasion"));
        assertFalse(detector.shouldFilter(player, "@Alec i have committed tax evasion"));
        assertFalse(detector.shouldFilter(player, "&ci have committed tax evasion"));
        assertTrue(detector.shouldFilter(player, "i have committed tax evasions"));
    }

    @Test
    void similarDetectionBlocksAtExactThreshold() {
        when(config.isAntiSpamRepetitiveEnabled()).thenReturn(false);
        when(config.isAntiSpamFloodEnabled()).thenReturn(false);
        when(config.getAntiSpamSimilarThreshold()).thenReturn(0.8);

        // One edit in five characters is exactly 0.8 similar.
        Player player = player();
        SpamDetector detector = detector();

        assertFalse(detector.shouldFilter(player, "snack"));
        assertTrue(detector.shouldFilter(player, "snuck"));
    }

    @Test
    void blocksFloodAtLimit() {
        when(config.isAntiSpamRepetitiveEnabled()).thenReturn(false);
        when(config.isAntiSpamSimilarEnabled()).thenReturn(false);
        when(config.getAntiSpamFloodMaxMessages()).thenReturn(3);

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(detector.shouldFilter(player, "cheese"));
        assertFalse(detector.shouldFilter(player, "balls"));
        assertFalse(detector.shouldFilter(player, "the voices are getting louder"));
        assertTrue(detector.shouldFilter(player, "greg is a microwave"));
    }

    @Test
    void prefersRepetitiveDetectionOverFlood() {
        when(config.isAntiSpamSimilarEnabled()).thenReturn(false);
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(2);
        when(config.getAntiSpamFloodMaxMessages()).thenReturn(2);
        yaml.set("anti-spam.flood.actions", List.of("notify-staff"));

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(detector.shouldFilter(player, "cheese"));
        assertFalse(detector.shouldFilter(player, "cheese"));
        assertTrue(detector.shouldFilter(player, "cheese"));
    }

    @Test
    void allowsMessageWhenActionsOmitBlock() {
        when(config.isAntiSpamSimilarEnabled()).thenReturn(false);
        when(config.isAntiSpamFloodEnabled()).thenReturn(false);
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(2);
        yaml.set("anti-spam.repetitive.actions", List.of("notify-staff"));
        yaml.set("anti-spam.repetitive.notify-message", "{message} again?");

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(detector.shouldFilter(player, "who ate my sandwich"));
        assertFalse(detector.shouldFilter(player, "who ate my sandwich"));
        assertFalse(detector.shouldFilter(player, "who ate my sandwich"));

        // Only the third message triggers a staff notification.
        bukkit.verify(() -> Bukkit.getOnlinePlayers(), times(1));
    }

    @Test
    void blockedMessagesStayInHistory() {
        when(config.isAntiSpamSimilarEnabled()).thenReturn(false);
        when(config.isAntiSpamFloodEnabled()).thenReturn(false);
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(2);

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(detector.shouldFilter(player, "the voices are getting louder"));
        assertFalse(detector.shouldFilter(player, "the voices are getting louder"));
        assertTrue(detector.shouldFilter(player, "the voices are getting louder"));

        // Raising the threshold only keeps blocking if the filtered message was recorded.
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(3);
        assertTrue(detector.shouldFilter(player, "the voices are getting louder"));
    }

    @Test
    void disabledSpamDetectionDoesNothing() {
        when(config.isAntiSpamEnabled()).thenReturn(false);

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(detector.shouldFilter(player, "greg is a microwave"));
        assertFalse(detector.shouldFilter(player, "greg is a microwave"));
        assertFalse(detector.shouldFilter(player, "greg is a microwave"));
        assertFalse(detector.shouldFilter(player, "greg is a microwave"));
    }
}
