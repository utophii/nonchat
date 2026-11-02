package com.nonxedy.nonchat.util.chat.filters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.nonxedy.nonchat.config.ModerationConfig;
import com.nonxedy.nonchat.moderation.ViolationType;

class SpamDetectorTest {

    private MockedStatic<Bukkit> bukkit;
    private ModerationConfig config;

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

        config = mock(ModerationConfig.class);
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

    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private SpamDetector detector() {
        return new SpamDetector(config);
    }

    private static boolean flagged(SpamDetector detector, Player player, String message) {
        return detector.inspect(player, message).isPresent();
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");
        when(player.hasPermission("nonchat.spam.bypass")).thenReturn(false);
        return player;
    }

    @Test
    void exposesBypassPermission() {
        // The SanctionsManager skips the detector for players with this permission
        assertEquals("nonchat.spam.bypass", detector().bypassPermission());
    }

    @Test
    void blocksRepetitiveMessagesAtThreshold() {
        when(config.isAntiSpamSimilarEnabled()).thenReturn(false);
        when(config.isAntiSpamFloodEnabled()).thenReturn(false);
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(3);

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(flagged(detector, player, "who ate my sandwich"));
        assertFalse(flagged(detector, player, "who ate my sandwich"));
        assertFalse(flagged(detector, player, "who ate my sandwich"));
        assertTrue(flagged(detector, player, "who ate my sandwich"));
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

        assertFalse(flagged(detector, player, "@Alex i have committed tax evasion"));
        assertFalse(flagged(detector, player, "@Alec i have committed tax evasion"));
        assertFalse(flagged(detector, player, "&ci have committed tax evasion"));
        assertTrue(flagged(detector, player, "i have committed tax evasions"));
    }

    @Test
    void similarDetectionBlocksAtExactThreshold() {
        when(config.isAntiSpamRepetitiveEnabled()).thenReturn(false);
        when(config.isAntiSpamFloodEnabled()).thenReturn(false);
        when(config.getAntiSpamSimilarThreshold()).thenReturn(0.8);

        // One edit in five characters is exactly 0.8 similar.
        Player player = player();
        SpamDetector detector = detector();

        assertFalse(flagged(detector, player, "snack"));
        assertTrue(flagged(detector, player, "snuck"));
    }

    @Test
    void blocksFloodAtLimit() {
        when(config.isAntiSpamRepetitiveEnabled()).thenReturn(false);
        when(config.isAntiSpamSimilarEnabled()).thenReturn(false);
        when(config.getAntiSpamFloodMaxMessages()).thenReturn(3);

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(flagged(detector, player, "cheese"));
        assertFalse(flagged(detector, player, "balls"));
        assertFalse(flagged(detector, player, "the voices are getting louder"));
        assertTrue(flagged(detector, player, "greg is a microwave"));
    }

    @Test
    void prefersRepetitiveDetectionOverFlood() {
        when(config.isAntiSpamSimilarEnabled()).thenReturn(false);
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(2);
        when(config.getAntiSpamFloodMaxMessages()).thenReturn(2);

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(flagged(detector, player, "cheese"));
        assertFalse(flagged(detector, player, "cheese"));
        assertEquals(ViolationType.SPAM_REPETITIVE, detector.inspect(player, "cheese").orElseThrow().type());
    }

    @Test
    void blockedMessagesStayInHistory() {
        when(config.isAntiSpamSimilarEnabled()).thenReturn(false);
        when(config.isAntiSpamFloodEnabled()).thenReturn(false);
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(2);

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(flagged(detector, player, "the voices are getting louder"));
        assertFalse(flagged(detector, player, "the voices are getting louder"));
        assertTrue(flagged(detector, player, "the voices are getting louder"));

        // Raising the threshold only keeps blocking if the filtered message was recorded.
        when(config.getAntiSpamRepetitiveThreshold()).thenReturn(3);
        assertTrue(flagged(detector, player, "the voices are getting louder"));
    }

    @Test
    void disabledSpamDetectionDoesNothing() {
        when(config.isAntiSpamEnabled()).thenReturn(false);

        Player player = player();
        SpamDetector detector = detector();

        assertFalse(flagged(detector, player, "greg is a microwave"));
        assertFalse(flagged(detector, player, "greg is a microwave"));
        assertFalse(flagged(detector, player, "greg is a microwave"));
        assertFalse(flagged(detector, player, "greg is a microwave"));
    }
}
