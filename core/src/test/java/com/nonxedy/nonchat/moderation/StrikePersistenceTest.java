package com.nonxedy.nonchat.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class StrikePersistenceTest {

    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);

    private SanctionsManager manager(File file, int strikesRequired) {
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
                List.of(new SanctionTier(strikesRequired, Duration.ofHours(1), List.of())));
        return new SanctionsManager(List.of(detector), type -> rule, line -> { }, file);
    }

    @Test
    void strikesSurviveARestart(@TempDir Path dir) {
        File file = dir.resolve("strikes.yml").toFile();

        SanctionsManager first = manager(file, 3);
        first.process(player, "ONE");
        first.process(player, "TWO");
        first.close();

        SanctionsManager second = manager(file, 3);
        assertEquals(2, second.getStrikes(id).size());
        assertEquals(ViolationType.CAPS, second.getStrikes(id).get(0).type());
        second.close();
    }

    @Test
    void clearedStrikesStayCleared(@TempDir Path dir) {
        File file = dir.resolve("strikes.yml").toFile();

        SanctionsManager first = manager(file, 3);
        first.process(player, "ONE");
        first.clearStrikes(id);
        first.close();

        assertTrue(manager(file, 3).getStrikes(id).isEmpty());
    }

    @Test
    void expiredAndBrokenEntriesAreIgnored(@TempDir Path dir) throws Exception {
        File file = dir.resolve("strikes.yml").toFile();
        long now = Instant.now().toEpochMilli();
        Files.writeString(file.toPath(), String.join("\n",
                id + ":",
                "  - {type: CAPS, issued: " + (now - 7200_000) + ", expires: " + (now - 3600_000) + "}",
                "  - {type: NOPE, issued: " + now + ", expires: " + (now + 3600_000) + "}",
                "  - {type: CAPS, issued: " + now + ", expires: " + (now + 3600_000) + "}",
                "not-a-uuid:",
                "  - {type: CAPS, issued: 1, expires: 2}",
                ""));

        Map<UUID, List<Strike>> loaded = new StrikeStorage(file).load();

        assertEquals(1, loaded.size());
        assertEquals(1, loaded.get(id).size());
    }

    @Test
    void clearingOneTypeKeepsTheOthers(@TempDir Path dir) {
        File file = dir.resolve("strikes.yml").toFile();
        SanctionsManager manager = manager(file, 3);
        manager.process(player, "ONE");
        manager.process(player, "TWO");

        assertEquals(0, manager.clearStrikes(id, ViolationType.ADVERTISING));
        assertEquals(2, manager.getStrikes(id).size());

        assertEquals(2, manager.clearStrikes(id, ViolationType.CAPS));
        assertTrue(manager.getStrikes(id).isEmpty());
        manager.close();
        assertTrue(manager(file, 3).getStrikes(id).isEmpty());
    }

    private static ExecutorService saverOf(SanctionsManager manager) throws ReflectiveOperationException {
        Field saver = SanctionsManager.class.getDeclaredField("saver");
        saver.setAccessible(true);
        return (ExecutorService) saver.get(manager);
    }

    private static void block(ExecutorService saver, CountDownLatch release) {
        saver.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // close() waits for the saver with a timed wait; reaching it proves it did not return early
    private static void waitUntilClosing(Thread closing) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Thread.State state = closing.getState();
            if (state == Thread.State.TIMED_WAITING) {
                return;
            }
            if (state == Thread.State.TERMINATED) {
                fail("close() returned without waiting for the saver");
            }
            Thread.onSpinWait();
        }
        fail("close() never started waiting for the saver");
    }

    @Test
    @Timeout(30)
    void closeWaitsForPendingSaves(@TempDir Path dir) throws Exception {
        File file = dir.resolve("strikes.yml").toFile();
        SanctionsManager manager = manager(file, 3);
        ExecutorService saver = saverOf(manager);
        CountDownLatch release = new CountDownLatch(1);
        Thread closing = new Thread(manager::close);
        closing.setDaemon(true);
        try {
            block(saver, release);
            manager.process(player, "ONE");
            manager.process(player, "TWO");

            closing.start();
            waitUntilClosing(closing);
        } finally {
            release.countDown();
            closing.join(10_000);
        }
        assertFalse(closing.isAlive(), "close() did not finish");

        assertTrue(saver.isTerminated());
        assertEquals(2, new StrikeStorage(file).load().get(id).size());
    }

    @Test
    @Timeout(30)
    void closeStillSavesWhenInterrupted(@TempDir Path dir) throws Exception {
        File file = dir.resolve("strikes.yml").toFile();
        SanctionsManager manager = manager(file, 3);
        ExecutorService saver = saverOf(manager);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean stillInterrupted = new AtomicBoolean();
        Thread closing = new Thread(() -> {
            manager.close();
            stillInterrupted.set(Thread.currentThread().isInterrupted());
        });
        closing.setDaemon(true);
        try {
            block(saver, release);
            manager.process(player, "ONE");

            closing.start();
            waitUntilClosing(closing);
            closing.interrupt();
        } finally {
            release.countDown();
            closing.join(10_000);
        }
        assertFalse(closing.isAlive(), "close() did not finish");

        assertTrue(stillInterrupted.get(), "the interruption is not swallowed");
        assertTrue(saver.awaitTermination(10, TimeUnit.SECONDS));
        List<Strike> saved = new StrikeStorage(file).load().get(id);
        assertEquals(1, saved.size());
        assertEquals(ViolationType.CAPS, saved.get(0).type());
    }
}
