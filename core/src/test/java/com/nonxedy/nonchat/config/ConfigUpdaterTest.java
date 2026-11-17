package com.nonxedy.nonchat.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigUpdaterTest {

    private static final String TEMPLATE = String.join("\n",
            "# Header comment",
            "language: en",
            "",
            "private-chat:",
            "  # Enable/disable private messages",
            "  enabled: true",
            "  format: \"§8[{sender}] §f{message}\"",
            "",
            "banned-words:",
            "  words:",
            "    - spam",
            "  exceptions: []",
            "",
            "entity-names:",
            "  custom-type-names: {}",
            "");

    private static YamlConfiguration load(String content) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(content);
        return config;
    }

    @Test
    void addsMissingKeyWithTemplateCommentAndKeepsUserValues() throws Exception {
        String current = String.join("\n",
                "language: ru",
                "private-chat:",
                "  format: \"custom {message}\"",
                "banned-words:",
                "  words:",
                "    - foo",
                "    - bar",
                "  exceptions: []",
                "entity-names:",
                "  custom-type-names: {}",
                "");

        ConfigUpdater.Result result = ConfigUpdater.update(TEMPLATE, current, null);

        assertTrue(result.changed());
        assertEquals(List.of("private-chat.enabled"), result.addedKeys());
        assertTrue(result.content().contains("  # Enable/disable private messages\n  enabled: true\n"));
        assertTrue(result.content().startsWith("# Header comment\n"));

        YamlConfiguration updated = load(result.content());
        assertEquals("ru", updated.getString("language"));
        assertTrue(updated.getBoolean("private-chat.enabled"));
        assertEquals("custom {message}", updated.getString("private-chat.format"));
        assertEquals(List.of("foo", "bar"), updated.getStringList("banned-words.words"));
    }

    @Test
    void reportsNoChangesWhenNothingIsMissing() throws Exception {
        ConfigUpdater.Result result = ConfigUpdater.update(TEMPLATE, TEMPLATE, null);

        assertFalse(result.changed());
        assertNull(result.content());
    }

    @Test
    void keepsUserListWhereTemplateHasInlineEmptyList() throws Exception {
        String current = String.join("\n",
                "banned-words:",
                "  words: []",
                "  exceptions:",
                "    - class",
                "    - glass",
                "");

        YamlConfiguration updated = load(ConfigUpdater.update(TEMPLATE, current, null).content());

        assertTrue(updated.isList("banned-words.exceptions"));
        assertEquals(List.of("class", "glass"), updated.getStringList("banned-words.exceptions"));
        assertTrue(updated.isList("banned-words.words"));
        assertTrue(updated.getStringList("banned-words.words").isEmpty());
    }

    @Test
    void keepsUserSectionWhereTemplateHasInlineEmptyMap() throws Exception {
        String current = String.join("\n",
                "entity-names:",
                "  custom-type-names:",
                "    ZOMBIE: Walker",
                "    SKELETON: Bones",
                "");

        YamlConfiguration updated = load(ConfigUpdater.update(TEMPLATE, current, null).content());

        assertEquals("Walker", updated.getString("entity-names.custom-type-names.ZOMBIE"));
        assertEquals("Bones", updated.getString("entity-names.custom-type-names.SKELETON"));
    }

    @Test
    void preservesCustomKeysUnderTemplateSectionsAndAtRoot() throws Exception {
        String current = String.join("\n",
                "private-chat:",
                "  extra-option: 5",
                "my-addon:",
                "  nested:",
                "    value: \"x\"",
                "");

        ConfigUpdater.Result result = ConfigUpdater.update(TEMPLATE, current, null);
        YamlConfiguration updated = load(result.content());

        assertEquals(5, updated.getInt("private-chat.extra-option"));
        assertEquals("x", updated.getString("my-addon.nested.value"));
        assertTrue(result.preservedKeys().contains("private-chat.extra-option"));
        assertTrue(result.preservedKeys().contains("my-addon"));
    }

    @Test
    void roundTripsStringsWithSpecialCharacters() throws Exception {
        String tricky = "say \"hi\" \\ C:\\path §a&#ff00ff: #not-a-comment привет";
        YamlConfiguration current = new YamlConfiguration();
        current.set("private-chat.format", tricky);

        YamlConfiguration updated = load(ConfigUpdater.update(TEMPLATE, current.saveToString(), null).content());

        assertEquals(tricky, updated.getString("private-chat.format"));
    }

    @Test
    void rejectsInvalidUserYamlInsteadOfResettingIt() {
        String broken = "language: en\nprivate-chat:\n  enabled: [unclosed\n";

        assertThrows(InvalidConfigurationException.class, () -> ConfigUpdater.update(TEMPLATE, broken, null));
    }

    @Test
    void rewritesWhenOnlyMigrationChangedSomething() throws Exception {
        ConfigUpdater.Result result = ConfigUpdater.update(TEMPLATE, TEMPLATE, config -> {
            config.set("language", "es");
            return true;
        });

        assertTrue(result.changed());
        assertEquals("es", load(result.content()).getString("language"));
    }

    @Test
    void keepsUserListsOfMappingsIntact() throws Exception {
        String template = String.join("\n",
                "advertising:",
                "  tiers:",
                "    - strikes: 1",
                "      expire: 30m",
                "      actions:",
                "        - notify-staff",
                "  console-notify: true",
                "");
        String current = String.join("\n",
                "advertising:",
                "  tiers:",
                "    - strikes: 3",
                "      expire: 1h",
                "      actions: [\"kick %player_name%\", notify-staff]",
                "    - strikes: 5",
                "      expire: 24h",
                "      actions: []",
                "");

        ConfigUpdater.Result result = ConfigUpdater.update(template, current, null);

        assertTrue(result.changed());
        YamlConfiguration updated = load(result.content());
        assertEquals(2, updated.getMapList("advertising.tiers").size());
        assertEquals(3, updated.getMapList("advertising.tiers").get(0).get("strikes"));
        assertEquals(List.of("kick %player_name%", "notify-staff"),
                updated.getMapList("advertising.tiers").get(0).get("actions"));
        assertEquals(List.of(), updated.getMapList("advertising.tiers").get(1).get("actions"));
        assertTrue(updated.getBoolean("advertising.console-notify"));
    }
}
