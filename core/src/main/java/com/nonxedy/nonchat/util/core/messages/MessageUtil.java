package com.nonxedy.nonchat.util.core.messages;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;

/**
 * Sends Adventure components natively.
 * Hover/click events are preserved when sending Adventure Components directly.
 *
 * <p>Requires Paper 1.21.6+ where all Adventure event message APIs are available
 * natively - no legacy String fallbacks are needed anymore.
 */
public final class MessageUtil {

    private MessageUtil() {
    }

    public static void send(@NotNull CommandSender sender, @NotNull Component message) {
        // Native Adventure support on modern Paper - hover and click events are preserved
        sender.sendMessage(message);
    }

    public static void broadcast(@NotNull Component message) {
        Bukkit.broadcast(message);
    }

    public static void joinMessage(@NotNull PlayerJoinEvent event, @NotNull Component message) {
        event.joinMessage(message);
    }

    public static void quitMessage(@NotNull PlayerQuitEvent event, @NotNull Component message) {
        event.quitMessage(message);
    }

    public static void deathMessage(@NotNull PlayerDeathEvent event, @Nullable Component message) {
        event.deathMessage(message);
    }
}
