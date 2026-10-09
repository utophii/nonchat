package com.nonxedy.nonchat.util.chat.filters;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;

// Executes all Bukkit side effects on the server thread; block decisions remain synchronous
public final class FilterActionHandler {
    private FilterActionHandler() {}

    public static boolean handle(PluginConfig config, Player player, String message, FilterSettings settings) {
        if (player == null) return settings.blocks();
        Runnable task = () -> {
            if (settings.message() != null && !settings.message().isEmpty()) {
                MessageUtil.send(player, render(player, settings.message(), message));
            }
            if (settings.notifyMessage() != null && !settings.notifyMessage().isEmpty()) {
                Component notification = render(player, settings.notifyMessage(), message);
                if (settings.hasAction("notify-staff")) {
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> p.hasPermission(settings.permission()) || p.isOp())
                            .forEach(p -> MessageUtil.send(p, notification));
                }
                if (settings.consoleNotify()) MessageUtil.send(Bukkit.getConsoleSender(), notification);
            }
            for (String action : settings.actions()) {
                String command = action.trim();
                if (command.isEmpty() || command.equalsIgnoreCase("block")
                        || command.equalsIgnoreCase("notify-staff")) continue;
                // Never interpolate raw chat text into console commands
                command = resolve(player, command);
                if (command.startsWith("/")) command = command.substring(1);
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (RuntimeException e) {
                    config.getPlugin().getLogger().warning("Failed to execute filter action: " + e.getMessage());
                }
            }
        };
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(config.getPlugin(), task);
        return settings.blocks();
    }

    private static String resolve(Player player, String template) {
        String text = template.replace("{player}", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%player_uuid%", player.getUniqueId().toString());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                text = PlaceholderAPI.setPlaceholders(player, text);
            } catch (RuntimeException e) {
                Bukkit.getLogger().warning("[nonchat] Filter placeholder error: " + e.getMessage());
            }
        }
        return text;
    }

    private static Component render(Player player, String template, String message) {
        // Parse only trusted templates. Flagged chat is literal text
        Component component = ColorUtil.parseComponentCached(resolve(player,
                template.replace("%message%", "{message}")));
        return component.replaceText(builder -> builder.matchLiteral("{message}")
                .replacement(Component.text(message == null ? "[empty]" : message)));
    }
}
