package com.nonxedy.nonchat.util.chat.filters;

import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.api.MessageFilter;
import com.nonxedy.nonchat.config.PluginConfig;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;

public class AdDetector implements MessageFilter {
    private static final Pattern pattern = Pattern.compile("([\\w+]+://)?([\\w-]+\\.)*[\\w-]+[.:]\\w+([/?=&#.]?[\\w-]+)*/?", Pattern.CASE_INSENSITIVE);

    private final List<String> whitelistedUrls;
    private final float sensitivity;
    private final String punishCommand;
    private final boolean staffNotify;
    private final String notifyMessage;

    public AdDetector(PluginConfig config, float sensitivity, String punishCommand,
                      boolean staffNotify, String notifyMessage) {
        this.whitelistedUrls = config.getAntiAdWhitelistedUrls();
        this.sensitivity = Math.max(0f, Math.min(1f, sensitivity));
        this.punishCommand = punishCommand;
        this.staffNotify = staffNotify;
        this.notifyMessage = notifyMessage;
    }

    @Override
    public boolean shouldFilter(Player player, String message) {
        if (player.hasPermission("nonchat.ad.bypass")) {
            return false;
        }

        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String matched = matcher.group();
            if (!isWhitelisted(matched)) {
                notifyStaff(player, message);
                return true;
            }
        }

        // Additional checks based on sensitivity
        if (sensitivity > 0.5f) {
            if (detectCommonAdTerms(message)) {
                notifyStaff(player, message);
                return true;
            }
        }

        return false;
    }

    private boolean isWhitelisted(String url) {
        for (String whitelisted : whitelistedUrls) {
            if (url.equals(whitelisted)) {
                return true;
            }
        }
        return false;
    }

    private boolean detectCommonAdTerms(String message) {
        String lower = message.toLowerCase();
        return lower.contains("join") && lower.contains("server") ||
               lower.contains("ip") && lower.contains("play");
    }

    private String resolvePlaceholders(Player player, String text) {
        if (player == null) return text;
        
        // Try PlaceholderAPI first if available
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (ClassNotFoundException e) {
            // Fall back to standard placeholders if PAPI not available
            return text.replace("%player_name%", player.getName())
                      .replace("%player_uuid%", player.getUniqueId().toString());
        }
    }

    private void notifyStaff(Player player, String message) {
        if (staffNotify) {
            String notification = notifyMessage;

            // Resolve PlaceholderAPI placeholders on the template (e.g. %player_name%)
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    notification = PlaceholderAPI.setPlaceholders(player, notification);
                } catch (Exception e) {
                    Bukkit.getLogger().log(Level.WARNING, "&#FFAFFB[nonchat] &cError processing notify-message placeholders: {0}", e.getMessage());
                }
            }

            // Insert the flagged message last, so user input never passes through placeholder parsing
            notification = notification.replace("{message}", message);

            Component notificationComponent = ColorUtil.parseComponentCached(notification);
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("nonchat.ad.notify") || p.isOp())
                .forEach(p -> MessageUtil.send(p, notificationComponent));

            // Log to console
            MessageUtil.send(Bukkit.getConsoleSender(), notificationComponent);
        }
        
        // Execute configured punishment command with resolved placeholders
        if (punishCommand != null && !punishCommand.isEmpty()) {
            String resolvedCommand = resolvePlaceholders(player, punishCommand);
            try {
                // Run command sync on main thread
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("nonchat"), () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedCommand);
                });
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().log(Level.WARNING, "&#FFAFFB[nonchat] &cFailed to execute punish command: {0}", e.getMessage());
            }
        }
    }
}
