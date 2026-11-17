package com.nonxedy.nonchat.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SanctionsManagerDebugTest {

    private final List<String> lines = new ArrayList<>();
    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);

    private SanctionsManager manager(int strikesRequired) {
        when(player.getName()).thenReturn("Alice");
        when(player.getUniqueId()).thenReturn(id);

        ViolationDetector detector = new ViolationDetector() {
            @Override
            public String bypassPermission() {
                return null;
            }

            @Override
            public Optional<Violation> inspect(Player p, String message) {
                return Optional.of(new Violation(ViolationType.CAPS, id, message));
            }
        };
        ViolationRule rule = new ViolationRule(ViolationType.CAPS, null, null, null, false, true,
                List.of(new SanctionTier(strikesRequired, Duration.ofMinutes(5), List.of("block"))));
        return new SanctionsManager(List.of(detector), type -> rule, lines::add, null);
    }

    @Test
    void logsViolationAndTierReached() {
        assertTrue(manager(1).process(player, "HELLO"));

        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("Alice violated CAPS"));
        assertTrue(lines.get(1).contains("reached tier 1 strike(s)"));
        assertTrue(lines.get(2).contains("No warning message"));
    }

    @Test
    void logsWhenNoTierIsReachedYet() {
        assertTrue(manager(3).process(player, "HELLO"));

        assertTrue(lines.get(1).contains("no tier to apply"));
        assertTrue(lines.get(1).contains("valid strikes: 1"));
    }
}
