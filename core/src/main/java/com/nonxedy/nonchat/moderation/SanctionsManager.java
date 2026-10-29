package com.nonxedy.nonchat.moderation;

import java.time.Duration;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import com.nonxedy.nonchat.config.ModerationConfig;
import com.nonxedy.nonchat.util.chat.filters.AdDetector;
import com.nonxedy.nonchat.util.chat.filters.CapsDetector;
import com.nonxedy.nonchat.util.chat.filters.SpamDetector;
import com.nonxedy.nonchat.util.chat.filters.WordBlocker;

/**
 * Central entry point of chat moderation.
 * Every message goes through {@link #process}: the detectors look for a violation and, when one is
 * found, the player gets a {@link Strike}. The sanction tiers of the violation's rule then decide
 * what happens, based on how many valid strikes the player has.
 */
public class SanctionsManager {

    private static final Logger LOGGER = Logger.getLogger("nonchat");
    private static final long SAVE_TIMEOUT_SECONDS = 5;

    private final List<ViolationDetector> detectors;
    private final Function<ViolationType, ViolationRule> ruleProvider;
    private final SanctionActionRunner actionRunner;
    private final Consumer<String> debug;
    private final Map<UUID, List<Strike>> strikes = new ConcurrentHashMap<>();
    private final StrikeStorage storage;
    private final ExecutorService saver;
    private final AtomicBoolean savePending = new AtomicBoolean();

    /**
     * @param detectors Detectors, checked in order; the first violation found wins
     * @param ruleProvider Resolves the rule for a violation type
     * @param debug Receives a line for every violation, strike and executed action (may do nothing)
     * @param strikesFile File where the strikes are kept across restarts; null to keep them in memory only
     */
    public SanctionsManager(List<ViolationDetector> detectors,
                            Function<ViolationType, ViolationRule> ruleProvider,
                            Consumer<String> debug,
                            File strikesFile) {
        this.detectors = List.copyOf(detectors);
        this.ruleProvider = ruleProvider;
        this.debug = debug;
        this.actionRunner = new SanctionActionRunner(debug);

        if (strikesFile != null) {
            this.storage = new StrikeStorage(strikesFile);
            this.saver = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "nonchat-strike-saver");
                thread.setDaemon(true);
                return thread;
            });
            storage.load().forEach((playerId, list) -> strikes.put(playerId, new ArrayList<>(list)));
            debug.accept("Loaded strikes of " + strikes.size() + " player(s) from " + strikesFile.getName());
        } else {
            this.storage = null;
            this.saver = null;
        }
    }

    /**
     * Creates the manager with the built-in detectors and the rules read from moderation.yml
     */
    public static SanctionsManager createDefault(ModerationConfig config) {
        return createDefault(config, line -> { });
    }

    /**
     * Same as {@link #createDefault(ModerationConfig)}, sending the debug lines to the given consumer
     */
    public static SanctionsManager createDefault(ModerationConfig config, Consumer<String> debug) {
        return createDefault(config, debug, null);
    }

    /**
     * Same as {@link #createDefault(ModerationConfig, Consumer)}, keeping the strikes in the given file
     * so they survive restarts
     */
    public static SanctionsManager createDefault(ModerationConfig config, Consumer<String> debug, File strikesFile) {
        return new SanctionsManager(
                List.of(
                        new WordBlocker(config),
                        new CapsDetector(config),
                        new SpamDetector(config),
                        new AdDetector(config)),
                new ModerationRuleProvider(config),
                debug,
                strikesFile);
    }

    /**
     * Checks a message and applies the sanctions if it violates a rule
     * @param player Player who sent the message
     * @param message The message as typed by the player
     * @return true if the message must be blocked, false if it is allowed
     */
    public boolean process(Player player, String message) {
        for (ViolationDetector detector : detectors) {
            String bypass = detector.bypassPermission();
            if (bypass != null && player.hasPermission(bypass)) {
                continue;
            }

            Optional<Violation> violation = detector.inspect(player, message);
            // A rule with 'block: false' only reports, so the remaining detectors still check the message
            if (violation.isPresent() && sanction(player, violation.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the strikes of a player that are still valid
     */
    public List<Strike> getStrikes(UUID playerId) {
        List<Strike> list = strikes.get(playerId);
        if (list == null) {
            return List.of();
        }
        Instant now = Instant.now();
        synchronized (list) {
            return list.stream().filter(s -> !s.isExpired(now)).toList();
        }
    }

    /**
     * Removes every strike of a player
     */
    public void clearStrikes(UUID playerId) {
        strikes.remove(playerId);
        saveAsync();
    }

    /**
     * Removes the strikes of a player for one kind of violation
     * @return How many valid strikes were removed
     */
    public int clearStrikes(UUID playerId, ViolationType type) {
        List<Strike> list = strikes.get(playerId);
        if (list == null) {
            return 0;
        }

        int removed;
        Instant now = Instant.now();
        synchronized (list) {
            removed = (int) list.stream().filter(s -> s.type() == type && !s.isExpired(now)).count();
            list.removeIf(s -> s.type() == type);
        }
        saveAsync();
        return removed;
    }

    /**
     * Stops the background saver, waits for its pending saves and writes the strikes to disk.
     * Call when the plugin is disabled.
     * The wait is limited, so a stuck disk cannot hang the shutdown: when it runs out or the thread is
     * interrupted, the pending saves are dropped and the strikes are still written once, but a save
     * that is already running may finish after this method returns.
     */
    public void close() {
        if (storage == null) {
            return;
        }
        saver.shutdown();
        boolean interrupted = false;
        try {
            if (!saver.awaitTermination(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.log(Level.WARNING, "[nonchat] Strike saver did not stop in {0}s, saving anyway",
                        SAVE_TIMEOUT_SECONDS);
                saver.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.warning("[nonchat] Interrupted while waiting for the strike saver, saving anyway");
            saver.shutdownNow();
            interrupted = true;
        }
        saveStrikes();
        // Restored after saving: an interrupted thread would fail the file write
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // Saves are coalesced: violations arriving while a save is queued are covered by that save
    private void saveAsync() {
        if (storage == null || saver.isShutdown()) {
            return;
        }
        if (savePending.compareAndSet(false, true)) {
            saver.execute(() -> {
                savePending.set(false);
                saveStrikes();
            });
        }
    }

    // The snapshot is taken under the storage lock, so whoever writes last has the newest strikes
    private void saveStrikes() {
        synchronized (storage) {
            storage.save(snapshot());
        }
    }

    private Map<UUID, List<Strike>> snapshot() {
        Map<UUID, List<Strike>> copy = new java.util.HashMap<>();
        strikes.forEach((playerId, list) -> {
            synchronized (list) {
                copy.put(playerId, List.copyOf(list));
            }
        });
        return copy;
    }

    /**
     * Registers the violation and runs its sanctions
     * @return true if the message must be blocked
     */
    private boolean sanction(Player player, Violation violation) {
        debug.accept(player.getName() + " violated " + violation.type() + ": \"" + violation.message() + "\"");

        ViolationRule rule = ruleProvider.apply(violation.type());
        if (rule == null) {
            debug.accept("No rule for " + violation.type() + ", nothing applied");
            return true;
        }

        SanctionTier tier = registerStrike(violation, rule);
        saveAsync();
        if (tier != null) {
            debug.accept(player.getName() + " reached tier " + tier.strikesRequired() + " strike(s) / "
                    + tier.expire() + " of " + violation.type() + ", actions: " + tier.actions());
            actionRunner.run(player, violation, rule, tier.actions());
        } else {
            debug.accept(player.getName() + " has no tier to apply for " + violation.type()
                    + " (valid strikes: " + validStrikes(violation) + ", tiers: " + rule.tiers().size() + ")");
        }
        actionRunner.warn(player, violation, rule);
        if (!rule.block()) {
            debug.accept("Message of " + player.getName() + " allowed: " + violation.type() + " has block: false");
        }
        return rule.block();
    }

    /**
     * Adds a strike for the violation and returns the heaviest tier the player now qualifies for
     * @return The tier to apply, or null if no tier has enough strikes yet
     */
    private long validStrikes(Violation violation) {
        return getStrikes(violation.playerId()).stream().filter(s -> s.type() == violation.type()).count();
    }

    private SanctionTier registerStrike(Violation violation, ViolationRule rule) {
        if (rule.tiers().isEmpty()) {
            return null;
        }

        Instant now = Instant.now();
        Duration longestExpire = rule.tiers().stream()
                .map(SanctionTier::expire)
                .max(Comparator.naturalOrder())
                .orElse(Duration.ZERO);

        List<Strike> list = strikes.computeIfAbsent(violation.playerId(), id -> new ArrayList<>());
        synchronized (list) {
            list.removeIf(s -> s.isExpired(now));
            list.add(new Strike(violation.playerId(), violation.type(), now, now.plus(longestExpire)));

            // TODO: decide whether a fired tier should consume its strikes; for now it fires on every violation
            return rule.tiers().stream()
                    .sorted(Comparator.comparingInt(SanctionTier::strikesRequired).reversed())
                    .filter(tier -> countStrikes(list, violation.type(), now.minus(tier.expire())) >= tier.strikesRequired())
                    .findFirst()
                    .orElse(null);
        }
    }

    private long countStrikes(List<Strike> list, ViolationType type, Instant since) {
        return list.stream()
                .filter(s -> s.type() == type && !s.issuedAt().isBefore(since))
                .count();
    }
}
