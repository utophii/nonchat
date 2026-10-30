package com.nonxedy.nonchat.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class StrikeCleanupTest {

    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final List<String> debug = new CopyOnWriteArrayList<>();
    private final SanctionsManager manager;

    StrikeCleanupTest() {
        when(player.getName()).thenReturn("Alice");
        when(player.getUniqueId()).thenReturn(id);
        // "caps ..." messages are CAPS violations, "ad ..." messages are ADVERTISING ones
        ViolationDetector detector = new ViolationDetector() {
            @Override
            public String bypassPermission() {
                return null;
            }

            @Override
            public Optional<Violation> inspect(Player p, String message) {
                ViolationType type = message.startsWith("caps") ? ViolationType.CAPS : ViolationType.ADVERTISING;
                return Optional.of(new Violation(type, id, message));
            }
        };
        manager = new SanctionsManager(List.of(detector),
                type -> new ViolationRule(type, null, null, null, false, true,
                        List.of(new SanctionTier(2, Duration.ofHours(1), List.of()))),
                debug::add, null);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, List<Strike>> strikesOf() throws ReflectiveOperationException {
        Field strikes = SanctionsManager.class.getDeclaredField("strikes");
        strikes.setAccessible(true);
        return (Map<UUID, List<Strike>>) strikes.get(manager);
    }

    private static void waitUntilBlockedOn(Thread thread, Object monitor) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());
            if (info == null) {
                fail("the thread finished without blocking on the list");
            }
            LockInfo lock = info.getLockInfo();
            if (lock != null && lock.getIdentityHashCode() == System.identityHashCode(monitor)) {
                return;
            }
            Thread.onSpinWait();
        }
        fail("the thread never blocked on the list");
    }

    /**
     * Runs {@code meanwhile} while {@code other} is blocked on the monitor, so it has already got the list
     * when {@code meanwhile} changes it. The monitor is released and the thread joined whatever happens.
     */
    private static void overlap(Thread other, Object monitor, Runnable meanwhile) throws InterruptedException {
        other.setDaemon(true);
        try {
            synchronized (monitor) {
                other.start();
                waitUntilBlockedOn(other, monitor);
                meanwhile.run();
            }
        } finally {
            other.join(10_000);
        }
    }

    @Test
    void clearingAllStrikesRemovesThePlayer() throws Exception {
        manager.process(player, "caps one");
        manager.clearStrikes(id);

        assertFalse(strikesOf().containsKey(id));
        manager.process(player, "caps two");
        assertEquals(1, manager.getStrikes(id).size());
    }

    @Test
    void clearingTheLastTypeRemovesThePlayer() throws Exception {
        manager.process(player, "caps one");
        manager.process(player, "ad one");

        manager.clearStrikes(id, ViolationType.CAPS);
        assertTrue(strikesOf().containsKey(id));

        manager.clearStrikes(id, ViolationType.ADVERTISING);
        assertFalse(strikesOf().containsKey(id));
        manager.process(player, "ad two");
        assertEquals(1, manager.getStrikes(id).size());
    }

    @Test
    @Timeout(30)
    void strikeRegisteredWhileTheListIsEmptiedIsKept() throws Exception {
        manager.process(player, "caps one");
        List<Strike> list = strikesOf().get(id);
        Thread registering = new Thread(() -> manager.process(player, "ad one"));

        // The list is already in the other thread's hands when it is emptied and removed
        overlap(registering, list, () -> manager.clearStrikes(id, ViolationType.CAPS));

        assertEquals(List.of(ViolationType.ADVERTISING), manager.getStrikes(id).stream().map(Strike::type).toList());
    }

    @Test
    @Timeout(30)
    void registeringWhileAllStrikesAreClearedMatchesSomeSerialOrder() throws Exception {
        manager.process(player, "caps one");
        List<Strike> list = strikesOf().get(id);
        Thread registering = new Thread(() -> manager.process(player, "caps two"));

        overlap(registering, list, () -> manager.clearStrikes(id));

        // Registered first: the old strike counted for the tier and the clear removed both.
        // Cleared first: the new strike is alone, so no tier was reached and it stays.
        boolean reachedTier = debug.stream().anyMatch(line -> line.contains("reached tier"));
        int left = manager.getStrikes(id).size();
        assertTrue(reachedTier && left == 0 || !reachedTier && left == 1, "tier " + reachedTier + ", strikes left " + left);
    }

    @Test
    @Timeout(30)
    @SuppressWarnings("unchecked")
    void snapshotTakenAfterClearingHasNoStrikesOfThePlayer() throws Exception {
        manager.process(player, "caps one");
        manager.process(player, "ad one");
        List<Strike> list = strikesOf().get(id);
        Method snapshot = SanctionsManager.class.getDeclaredMethod("snapshot");
        snapshot.setAccessible(true);
        AtomicReference<Map<UUID, List<Strike>>> copy = new AtomicReference<>();
        Thread saving = new Thread(() -> {
            try {
                copy.set((Map<UUID, List<Strike>>) snapshot.invoke(manager));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });

        // The snapshot has the list but copies it only after the clear is over
        overlap(saving, list, () -> manager.clearStrikes(id));

        assertTrue(copy.get().getOrDefault(id, List.of()).isEmpty());
    }
}
