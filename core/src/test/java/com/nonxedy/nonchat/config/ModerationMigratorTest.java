package com.nonxedy.nonchat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModerationMigratorTest {

    // config.yml of 1.6.6 and of 1.6.5
    private static final String LEGACY = "/legacy-config.yml";
    private static final String LEGACY_1_6_5 = "/legacy-config-1.6.5.yml";

    private YamlConfiguration legacy() {
        return legacy(LEGACY);
    }

    private YamlConfiguration legacy(String resource) {
        return YamlConfiguration.loadConfiguration(resource(resource).toFile());
    }

    // getFile() keeps the URL escaping, so a path like "programaci%C3%B3n" would not exist on disk
    private Path resource(String name) {
        try {
            return Path.of(getClass().getResource(name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private String template() throws IOException {
        return Files.readString(Path.of("../plugin/src/main/resources/moderation.yml"));
    }

    @Test
    void convertsOldSectionsIntoOneTierPerRule() {
        PluginMessages messages = null;
        YamlConfiguration out = ModerationMigrator.convert(legacy(), messages);

        assertTrue(out.getStringList("banned-words.words").contains("badword"));
        assertEquals(70, out.getInt("caps.max-caps-percentage"));
        assertEquals(0.7, out.getDouble("advertising.sensitivity"));
        assertEquals(1, out.getMapList("advertising.tiers").get(0).get("strikes"));
        assertEquals(java.util.List.of("notify-staff", "ban %player_name% advertising"),
                out.getMapList("advertising.tiers").get(0).get("actions"));
        assertTrue(out.getBoolean("advertising.update-tld-list"));
        assertEquals(false, out.getBoolean("banned-words.whole-words"));
        assertEquals(java.util.List.of("notify-staff", "tell %player_name% Slow down!"),
                out.getMapList("spam.flood.tiers").get(0).get("actions"));
        assertEquals(5, out.getInt("spam.flood.max-messages"));
        assertTrue(out.getBoolean("advertising.block"));
        assertTrue(out.getBoolean("spam.flood.block"));
    }

    @Test
    void buildsAdActionsFromTheOldSeparateOptions() {
        YamlConfiguration out = ModerationMigrator.convert(legacy(LEGACY_1_6_5), null);

        assertEquals(java.util.List.of("notify-staff", "ban %player_name% advertising"),
                out.getMapList("advertising.tiers").get(0).get("actions"));
        assertEquals(true, out.getBoolean("spam.flood.console-notify"));
        // 1.6.5 had no action lists and always blocked
        assertTrue(out.getBoolean("advertising.block"));
        assertTrue(out.getBoolean("banned-words.block"));
    }

    @Test
    void ruleWithoutBlockActionOnlyReports() {
        YamlConfiguration old = new YamlConfiguration();
        old.set("caps-filter.actions", java.util.List.of("notify-staff"));
        old.set("anti-spam.flood.actions", java.util.List.of("BLOCK", "notify-staff"));

        YamlConfiguration out = ModerationMigrator.convert(old, null);

        assertFalse(out.getBoolean("caps.block"));
        assertTrue(out.getBoolean("spam.flood.block"));
        assertEquals(java.util.List.of("notify-staff"), out.getMapList("spam.flood.tiers").get(0).get("actions"));
    }

    @Test
    void migratesFileAndRemovesOnlyLegacySections(@TempDir Path dir) throws IOException {
        File config = dir.resolve("config.yml").toFile();
        Files.copy(resource(LEGACY), config.toPath());
        File target = dir.resolve("moderation.yml").toFile();

        assertTrue(ModerationMigrator.migrate(config, target, null, template()));

        String left = Files.readString(config.toPath());
        for (String key : new String[] {"banned-words:", "caps-filter:", "anti-ad:", "anti-spam:"}) {
            assertFalse(left.contains("\n" + key), key);
        }
        YamlConfiguration remaining = YamlConfiguration.loadConfiguration(config);
        assertTrue(remaining.contains("hover-text"));
        assertTrue(remaining.contains("channels"));
        assertTrue(left.contains("# "), "comments of other sections are kept");
        assertTrue(YamlConfiguration.loadConfiguration(target).contains("spam.repetitive.tiers"));
        assertTrue(new File(dir.toFile(), "config.yml.pre-moderation.bak").exists());

        // Same comments as a fresh moderation.yml, with the migrated values
        String written = Files.readString(target.toPath());
        assertTrue(written.startsWith(template().lines().findFirst().get()), "header comes from the bundled file");
        assertTrue(written.contains("# Warning sent to the player."));
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(target);
        assertEquals(java.util.List.of("notify-staff", "ban %player_name% advertising"),
                migrated.getMapList("advertising.tiers").get(0).get("actions"));
        assertEquals(5, migrated.getInt("spam.flood.max-messages"));
    }

    @Test
    void nothingToMigrateWithoutLegacySections(@TempDir Path dir) throws IOException {
        File config = dir.resolve("config.yml").toFile();
        Files.writeString(config.toPath(), "language: en\n");
        assertFalse(ModerationMigrator.migrate(config, dir.resolve("moderation.yml").toFile(), null, null));
    }
}
