package com.nonxedy.nonchat.moderation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgressionTest {
    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final Clock clock = mock(Clock.class);
    private final List<String> events = new ArrayList<>();
    private final Instant start = Instant.parse("2026-01-01T00:00:00Z");

    private SanctionsManager manager(File file, int firstThreshold) {
        return manager(file, firstThreshold, 3);
    }

    private SanctionsManager manager(File file, int firstThreshold, int maximum) {
        when(player.getName()).thenReturn("Alice");
        when(player.getUniqueId()).thenReturn(id);
        ViolationDetector detector = new ViolationDetector() {
            public String bypassPermission() { return null; }
            public Optional<Violation> inspect(Player p, String message) {
                return Optional.of(new Violation(ViolationType.valueOf(message), id, message));
            }
        };
        return new SanctionsManager(List.of(detector),
                type -> new ViolationRule(type, null, null, null, false, true, List.of(
                        new SanctionTier(firstThreshold, Duration.ofSeconds(10), List.of()),
                        new SanctionTier(maximum, Duration.ofSeconds(20), List.of()))), events::add, file, clock);
    }

    private void at(long seconds) {
        when(clock.instant()).thenReturn(start.plusSeconds(seconds));
    }

    private void violation(SanctionsManager manager, String type, int expectedTier) {
        events.clear();
        assertTrue(manager.process(player, type));
        assertEquals(expectedTier != 0, events.stream().anyMatch(s -> s.contains("reached tier")));
        if (expectedTier != 0) {
            assertTrue(events.stream().anyMatch(s -> s.contains("reached tier " + expectedTier + " strike(s)")), events.toString());
        }
    }

    @Test
    void maximumRepeatsAndResetsOnlyAfterInactivity() {
        at(0);
        SanctionsManager manager = manager(null, 1);
        violation(manager, "CAPS", 1);
        at(9);
        violation(manager, "CAPS", 1);
        at(18);
        violation(manager, "CAPS", 3);
        // The first strikes are older than both original tier windows.
        at(37);
        violation(manager, "CAPS", 3);
        assertEquals(3, manager.getStrikes(id).size());
        assertTrue(manager.getStrikes(id).stream().allMatch(s -> s.expiresAt().equals(start.plusSeconds(57))));
        at(57);
        assertTrue(manager.getStrikes(id).isEmpty());
        violation(manager, "CAPS", 1);
        assertEquals(1, manager.getStrikes(id).size());
    }

    @Test
    void oldOversizedFilesAreTrimmedAndRepeatedViolationsStayBounded(@TempDir Path dir) {
        at(0);
        File file = dir.resolve("strikes.yml").toFile();
        List<Strike> old = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            old.add(new Strike(id, ViolationType.CAPS, start, start.plusSeconds(20)));
        }
        new StrikeStorage(file, clock).save(java.util.Map.of(id, old));
        SanctionsManager manager = manager(file, 1);
        try {
            assertEquals(3, manager.getStrikes(id).size());
            for (int i = 0; i < 100; i++) {
                violation(manager, "CAPS", 3);
                assertEquals(3, manager.getStrikes(id).size());
            }
        } finally {
            manager.close();
        }
        assertEquals(3, new StrikeStorage(file, clock).load().get(id).size());
    }

    @Test
    void oneStrikeRuleKeepsOnlyOneEntry() {
        at(0);
        SanctionsManager manager = manager(null, 1, 1);
        for (int i = 0; i < 100; i++) {
            violation(manager, "SPAM_FLOOD", 1);
            assertEquals(1, manager.getStrikes(id).size());
        }
    }

    @Test
    void typesExpireIndependentlyAndLowestTierAppliesBeforeThreshold() {
        at(0);
        SanctionsManager manager = manager(null, 2);
        violation(manager, "CAPS", 0);
        at(9);
        violation(manager, "ADVERTISING", 0);
        at(10);
        violation(manager, "CAPS", 0);
        at(18);
        violation(manager, "ADVERTISING", 2);
        at(19);
        violation(manager, "CAPS", 2);
    }

    @Test
    void refreshedDeadlineAndProgressionSurviveRestart(@TempDir Path dir) {
        File file = dir.resolve("strikes.yml").toFile();
        at(0);
        SanctionsManager first = manager(file, 1);
        try {
            violation(first, "CAPS", 1);
            at(9);
            violation(first, "CAPS", 1);
            at(18);
            violation(first, "CAPS", 3);
        } finally {
            first.close();
        }
        at(37);
        SanctionsManager second = manager(file, 1);
        try {
            assertEquals(3, second.getStrikes(id).size());
            violation(second, "CAPS", 3);
        } finally {
            second.close();
        }
        at(57);
        SanctionsManager third = manager(file, 1);
        try {
            assertTrue(third.getStrikes(id).isEmpty());
            violation(third, "CAPS", 1);
        } finally {
            third.close();
        }
    }
}
