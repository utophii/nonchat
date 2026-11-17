package com.nonxedy.nonchat.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class SanctionActionRunnerTest {

    @Test
    void bothMessageTokensAreLiteralAndNotRecursivelyExpanded() {
        String message = "{message} %message% " + INJECTION;
        Component rendered = SanctionActionRunner.render("{message} / %message%", message);
        assertEquals(message + " / " + message, PlainTextComponentSerializer.plainText().serialize(rendered));
        assertTrue(clickEvents(rendered).isEmpty());
    }

    @Test
    void playerPlaceholdersAreDelegatedToPapi() {
        var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var plugins = org.mockito.Mockito.mock(org.bukkit.plugin.PluginManager.class);
        var id = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(player.getName()).thenReturn("Alice");
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(id);
        org.mockito.Mockito.when(plugins.getPlugin("PlaceholderAPI"))
                .thenReturn(org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class));
        try (var bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class);
             var papi = org.mockito.Mockito.mockStatic(me.clip.placeholderapi.PlaceholderAPI.class)) {
            bukkit.when(org.bukkit.Bukkit::getPluginManager).thenReturn(plugins);
            String template = "%player_name% %player_uuid% %vault_prefix%";
            papi.when(() -> me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, template))
                    .thenReturn("Alice " + id + " VIP");
            assertEquals("Alice " + id + " VIP", new SanctionActionRunner(line -> {}).resolvePlaceholders(
                    player, template));
            org.mockito.Mockito.when(plugins.getPlugin("PlaceholderAPI")).thenReturn(null);
            assertEquals(template, new SanctionActionRunner(line -> {}).resolvePlaceholders(player, template));
        }
    }

    private static final String INJECTION = "<click:run_command:'/op Attacker'>badword</click>";

    @Test
    void flaggedMessageIsInsertedAsLiteralText() {
        Component component = SanctionActionRunner.render(
                "&#FFAFFB[nonchat] &fAttacker sent banned words: &#ff0000{message}", INJECTION);

        assertTrue(clickEvents(component).isEmpty());
        assertEquals("[nonchat] Attacker sent banned words: " + INJECTION,
                PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void templateFormattingIsStillParsed() {
        Component component = SanctionActionRunner.render("&cBlocked: %message%", "hello");

        assertEquals("Blocked: hello", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void missingMessageIsShownAsEmpty() {
        Component component = SanctionActionRunner.render("Blocked: {message}", null);

        assertEquals("Blocked: [empty]", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void leadingSlashIsStrippedFromCommands() {
        assertEquals("kick Alice", SanctionActionRunner.toConsoleCommand("/kick Alice"));
        assertEquals("kick Alice", SanctionActionRunner.toConsoleCommand("  / kick Alice "));
        assertEquals("ban Alice", SanctionActionRunner.toConsoleCommand("ban Alice"));
        assertEquals("", SanctionActionRunner.toConsoleCommand("  "));
        assertEquals("", SanctionActionRunner.toConsoleCommand(null));
    }

    private static List<Object> clickEvents(Component component) {
        List<Object> events = new ArrayList<>();
        if (component.clickEvent() != null) {
            events.add(component.clickEvent());
        }
        component.children().forEach(child -> events.addAll(clickEvents(child)));
        return events;
    }
}
