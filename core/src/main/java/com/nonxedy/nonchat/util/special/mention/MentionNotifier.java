package com.nonxedy.nonchat.util.special.mention;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.nonxedy.nonchat.Nonchat;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

/**
 * Notifies players that somebody mentioned them in chat.
 *
 * <p>The notification itself is fully described by {@code mentions.mention-message}
 * (see {@link MentionNotification}), so it can be a chat message, an action bar
 * text, a title or a boss bar - or nothing at all when the value is empty. Sounds
 * are configured separately in {@code mention-sounds}.</p>
 *
 * <p>Boss bars are tracked per player: a new mention replaces the bar that is
 * still visible instead of stacking another one on top of it, and the bar is
 * hidden again once its configured duration runs out.</p>
 */
public final class MentionNotifier {

    // Upper bound for the problem cache, so a broken config can never grow it forever
    private static final int MAX_REPORTED_PROBLEMS = 64;

    private final Nonchat plugin;
    private final PluginConfig config;
    private final Map<UUID, BossBar> visibleBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> hideTasks = new ConcurrentHashMap<>();
    private final Set<String> reportedProblems = ConcurrentHashMap.newKeySet();
    // Last parsed config value, so one message mentioning many players parses it once
    private volatile Cached cached;

    /**
     * Creates a notifier reading its settings from the plugin configuration.
     *
     * @param plugin plugin instance, used for scheduling and debug logging
     * @param config plugin configuration
     */
    public MentionNotifier(Nonchat plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Shows the configured notification to a mentioned player.
     *
     * @param mentioned player that was mentioned
     * @param sender    player who sent the mention
     */
    public void notifyMentioned(Player mentioned, Player sender) {
        MentionNotification notification = notification();
        reportProblems(notification);
        if (!notification.isEnabled()) {
            return;
        }

        try {
            dispatch(mentioned, sender, notification);
        } catch (Exception e) {
            plugin.logError("Failed to notify " + mentioned.getName() + " about a mention: " + e.getMessage());
        }
    }

    /**
     * Forgets everything tracked for a player, for example when they leave.
     *
     * @param player player to clean up
     */
    public void clear(Player player) {
        removeBar(player.getUniqueId());
    }

    /** Cancels pending boss bar removals, used when the plugin is disabled. */
    public void shutdown() {
        hideTasks.values().forEach(task -> {
            try {
                task.cancel();
            } catch (Exception e) {
                plugin.logError("Failed to cancel a mention boss bar task: " + e.getMessage());
            }
        });
        hideTasks.clear();
        visibleBars.clear();
    }

    /**
     * Parses {@code mentions.mention-message} once per config value; a reload or an
     * edit changes the value and the notification is parsed again.
     */
    private MentionNotification notification() {
        String raw = config.getMentionMessage();
        Cached current = cached;
        if (current != null && current.raw().equals(raw)) {
            return current.notification();
        }

        Cached parsed = new Cached(raw, MentionNotification.parse(raw));
        cached = parsed;
        return parsed.notification();
    }

    private void dispatch(Player mentioned, Player sender, MentionNotification notification) {
        Component content = render(notification.content(), sender);
        switch (notification.type()) {
            case MESSAGE -> MessageUtil.send(mentioned, content);
            case ACTIONBAR -> mentioned.sendActionBar(content);
            case TITLE -> showTitle(mentioned, sender, content, notification);
            case BOSSBAR -> showBossBar(mentioned, content, notification);
        }
    }

    private void showTitle(Player mentioned, Player sender, Component content, MentionNotification notification) {
        Component subtitle = notification.subtitle() == null || notification.subtitle().isEmpty()
                ? Component.empty()
                : render(notification.subtitle(), sender);
        MentionNotification.TitleSettings times = notification.title();
        mentioned.showTitle(Title.title(content, subtitle, times.fadeIn(), times.stay(), times.fadeOut()));
    }

    private void showBossBar(Player mentioned, Component content, MentionNotification notification) {
        MentionNotification.BossBarSettings settings = notification.bossBar();
        BossBar bar = BossBar.bossBar(content, settings.progress(), settings.color(), settings.overlay());
        UUID playerId = mentioned.getUniqueId();

        removeBar(playerId);
        visibleBars.put(playerId, bar);
        mentioned.showBossBar(bar);

        long delay = settings.durationSeconds() * 20L;
        hideTasks.put(playerId, Bukkit.getScheduler().runTaskLater(plugin, () -> removeBar(playerId, bar), delay));
    }

    private void removeBar(UUID playerId) {
        removeBar(playerId, visibleBars.get(playerId));
    }

    private void removeBar(UUID playerId, BossBar bar) {
        if (bar == null) {
            return;
        }

        BukkitTask task = hideTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        // Only hide the bar when it is still the one this player is looking at
        if (!visibleBars.remove(playerId, bar)) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.hideBossBar(bar);
        }
    }

    private Component render(String text, Player sender) {
        return ColorUtil.parseComponent(resolvePlaceholders(text, sender));
    }

    /**
     * Resolves the PlaceholderAPI placeholders of the player who sent the mention,
     * so {@code %player_name%} is the name of that player. Without PlaceholderAPI
     * the text is used exactly as configured.
     */
    private String resolvePlaceholders(String text, Player sender) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return text;
        }

        try {
            return PlaceholderAPI.setPlaceholders(sender, text);
        } catch (Exception e) {
            plugin.logError("Error processing mention notification placeholders: " + e.getMessage());
            return text;
        }
    }

    /** Warns about a broken config value once per problem instead of on every mention. */
    private void reportProblems(MentionNotification notification) {
        if (notification.problems().isEmpty()) {
            return;
        }
        if (reportedProblems.size() > MAX_REPORTED_PROBLEMS) {
            reportedProblems.clear();
        }

        for (String problem : notification.problems()) {
            if (reportedProblems.add(problem)) {
                Bukkit.getLogger().warning("[nonchat] mentions.mention-message: " + problem);
            }
        }
    }

    /** A config value together with the notification parsed from it. */
    private record Cached(String raw, MentionNotification notification) {}
}
