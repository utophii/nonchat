package com.nonxedy.nonchat.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.nonxedy.nonchat.Nonchat;

class ModerationConfigRecoveryTest {
    @Test
    void brokenStartupAndReloadKeepUsableSettingsAndOriginalFile(@TempDir Path dir) throws Exception {
        Nonchat plugin = mock(Nonchat.class);
        when(plugin.getDataFolder()).thenReturn(dir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getResource("moderation.yml")).thenAnswer(call ->
                Files.newInputStream(Path.of("../plugin/src/main/resources/moderation.yml")));
        Path file = dir.resolve("moderation.yml");
        String broken = "banned-words: [\n";
        Files.writeString(file, broken);
        ModerationConfig config = new ModerationConfig(plugin, null);
        config.load();
        assertTrue(config.getBannedWords().contains("badword"));
        assertEquals(broken, Files.readString(file));
        Files.writeString(file, "banned-words:\n  words: [custom]\n");
        config.reload();
        assertEquals(List.of("custom"), config.getBannedWords());
        Files.writeString(file, broken);
        config.reload();
        assertEquals(List.of("custom"), config.getBannedWords());
        assertEquals(broken, Files.readString(file));
    }
}
