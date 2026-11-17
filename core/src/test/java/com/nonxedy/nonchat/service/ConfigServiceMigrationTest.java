package com.nonxedy.nonchat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.util.AsyncConfigSaver;

import io.papermc.paper.plugin.configuration.PluginMeta;

class ConfigServiceMigrationTest {

    private static final String[] LEGACY_SECTIONS = {"banned-words", "caps-filter", "anti-ad", "anti-spam"};

    @TempDir
    Path dataFolder;

    private final Nonchat plugin = mock(Nonchat.class);
    private MockedStatic<Bukkit> bukkit;
    private File configFile;
    private String legacy;

    @BeforeEach
    void setUp() throws IOException {
        configFile = dataFolder.resolve("config.yml").toFile();
        legacy = Files.readString(Path.of("src/test/resources/legacy-config.yml")).replace("debug: false", "debug: true");
        // The bundled language files are not on core's test classpath
        Path langs = Files.createDirectory(dataFolder.resolve("langs"));
        for (String lang : new String[] {"en", "es", "ru"}) {
            Files.copy(Path.of("../plugin/src/main/resources/langs/messages_" + lang + ".yml"),
                    langs.resolve("messages_" + lang + ".yml"));
        }

        PluginMeta meta = mock(PluginMeta.class);
        when(meta.getVersion()).thenReturn("test");
        when(plugin.getPluginMeta()).thenReturn(meta);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getResource(anyString())).thenAnswer(invocation -> {
            File bundled = new File("../plugin/src/main/resources", invocation.getArgument(0, String.class));
            return bundled.exists() ? new FileInputStream(bundled) : null;
        });

        bukkit = Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void savingAfterMigrationDoesNotBringBackLegacySections() throws IOException {
        Files.writeString(configFile.toPath(), legacy);

        ConfigService configService = new ConfigService(plugin);

        assertSaveKeepsConfigClean(configService);
    }

    @Test
    void savingAfterReloadMigrationDoesNotBringBackLegacySections() throws IOException {
        Files.writeString(configFile.toPath(), "language: en\n");
        ConfigService configService = new ConfigService(plugin);

        Files.writeString(configFile.toPath(), legacy);
        configService.reload();

        assertSaveKeepsConfigClean(configService);
    }

    private void assertSaveKeepsConfigClean(ConfigService configService) throws IOException {
        when(plugin.getConfigService()).thenReturn(configService);
        AsyncConfigSaver saver = new AsyncConfigSaver(plugin, configService.getConfig());
        try {
            saver.saveAsync("default-channel", "global");
            saver.saveNow();

            YamlConfiguration saved = YamlConfiguration.loadConfiguration(configFile);
            for (String section : LEGACY_SECTIONS) {
                assertFalse(saved.contains(section), section);
            }
            assertEquals("global", saved.getString("default-channel"));
            assertTrue(saved.getBoolean("debug"));
            assertTrue(saved.contains("channels"));
            assertTrue(saved.contains("hover-text"));
            assertTrue(dataFolder.resolve("moderation.yml").toFile().exists());
            assertEquals(legacy, Files.readString(dataFolder.resolve("config.yml.pre-moderation.bak")));
        } finally {
            saver.shutdown();
        }
    }
}
