package com.nonxedy.nonchat.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class SanctionActionRunnerTest {

    private static final String INJECTION = "<click:run_command:'/op Attacker'>badword</click>";

    @Test
    void flaggedMessageIsInsertedAsLiteralText() {
        Component component = SanctionActionRunner.render(
                "&#FFAFFB[nonchat] &fAttacker sent banned words: &#ff0000{message}", "{message}", INJECTION);

        assertTrue(clickEvents(component).isEmpty());
        assertEquals("[nonchat] Attacker sent banned words: " + INJECTION,
                PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void templateFormattingIsStillParsed() {
        Component component = SanctionActionRunner.render("&cBlocked: %message%", "%message%", "hello");

        assertEquals("Blocked: hello", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void missingMessageIsShownAsEmpty() {
        Component component = SanctionActionRunner.render("Blocked: {message}", "{message}", null);

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
