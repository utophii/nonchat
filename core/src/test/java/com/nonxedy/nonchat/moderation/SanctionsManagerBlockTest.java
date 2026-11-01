package com.nonxedy.nonchat.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import com.nonxedy.nonchat.util.core.messages.MessageUtil;
import net.kyori.adventure.text.Component;

class SanctionsManagerBlockTest {

    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);

    SanctionsManagerBlockTest() {
        when(player.getName()).thenReturn("Alice");
        when(player.getUniqueId()).thenReturn(id);
    }

    private ViolationDetector detects(ViolationType type, String word) {
        return new ViolationDetector() {
            @Override
            public String bypassPermission() {
                return null;
            }

            @Override
            public Optional<Violation> inspect(Player p, String message) {
                return message.contains(word) ? Optional.of(new Violation(type, id, message)) : Optional.empty();
            }
        };
    }

    private SanctionsManager manager(Map<ViolationType, Boolean> block) {
        return new SanctionsManager(
                List.of(detects(ViolationType.CAPS, "HELLO"), detects(ViolationType.ADVERTISING, "example.com")),
                type -> new ViolationRule(type, null, null, null, false, block.get(type),
                        List.of(new SanctionTier(1, Duration.ofMinutes(5), List.of()))),
                line -> { }, null);
    }

    @Test
    void consoleLoggingIsIndependentOfTiersAndStaffActions() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        PluginManager plugins = mock(PluginManager.class);
        try (var bukkit = mockStatic(Bukkit.class); var messages = mockStatic(MessageUtil.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());

            List<List<SanctionTier>> cases = List.of(
                    List.of(),
                    List.of(new SanctionTier(3, Duration.ofMinutes(5), List.of())),
                    List.of(new SanctionTier(1, Duration.ofMinutes(5), List.of())),
                    List.of(new SanctionTier(1, Duration.ofMinutes(5), List.of("notify-staff"))));
            for (List<SanctionTier> tiers : cases) {
                for (boolean enabled : List.of(true, false)) {
                    messages.clearInvocations();
                    SanctionsManager manager = new SanctionsManager(
                            List.of(detects(ViolationType.CAPS, "HELLO")),
                            type -> new ViolationRule(type, null, "{message}", "staff", enabled, true, tiers),
                            line -> { }, null);

                    assertTrue(manager.process(player, "HELLO"));

                    if (enabled) {
                        messages.verify(() -> MessageUtil.send(console, Component.text("HELLO")));
                    }
                    messages.verifyNoMoreInteractions();
                }
            }
        }
    }

    @Test
    void nonBlockingRuleLetsTheMessageThroughButStillGivesAStrike() {
        SanctionsManager manager = manager(Map.of(ViolationType.CAPS, false, ViolationType.ADVERTISING, true));

        assertFalse(manager.process(player, "HELLO"));
        assertEquals(1, manager.getStrikes(id).size());
    }

    @Test
    void laterDetectorsStillRunAfterANonBlockingViolation() {
        SanctionsManager manager = manager(Map.of(ViolationType.CAPS, false, ViolationType.ADVERTISING, true));

        assertTrue(manager.process(player, "HELLO example.com"));
        assertEquals(2, manager.getStrikes(id).size());
    }

    @Test
    void blockingRuleStopsAtTheFirstViolation() {
        SanctionsManager manager = manager(Map.of(ViolationType.CAPS, true, ViolationType.ADVERTISING, true));

        assertTrue(manager.process(player, "HELLO example.com"));
        assertEquals(List.of(ViolationType.CAPS), manager.getStrikes(id).stream().map(Strike::type).toList());
    }
}
