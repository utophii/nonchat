package com.nonxedy.nonchat.moderation;

import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nonxedy.nonchat.util.core.colors.ColorUtil;
import com.nonxedy.nonchat.util.core.messages.MessageUtil;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;

/**
 * Executes the side effects of a sanction: staff notifications, console commands and player warnings.
 * Single place for what each filter used to implement on its own.
 */
class SanctionActionRunner {

    private final Consumer<String> debug;

    SanctionActionRunner(Consumer<String> debug) {
        this.debug = debug;
    }

    /**
     * Runs the actions of a tier
     * @param player Player who committed the violation
     * @param violation The detected violation
     * @param rule Rule of the violation
     * @param actions 'notify-staff' or console commands; 'block' (set per rule) and 'warn' (always sent) are skipped
     */
    void run(Player player, Violation violation, ViolationRule rule, List<String> actions) {
        for (String action : actions) {
            if (action.equalsIgnoreCase("block") || action.equalsIgnoreCase("warn")) {
                // Blocking is the rule's 'block' setting and the warning is always sent by the SanctionsManager
                continue;
            }
            if (action.equalsIgnoreCase("notify-staff")) {
                debug.accept("Action notify-staff for " + player.getName());
                notifyStaff(player, violation, rule);
            } else {
                executeCommand(player, action);
            }
        }
    }

    /**
     * Sends the rule's warning message to the player, if configured
     */
    void warn(Player player, Violation violation, ViolationRule rule) {
        String warnMessage = rule.warnMessage();
        if (warnMessage == null || warnMessage.isEmpty()) {
            debug.accept("No warning message for " + violation.type());
            return;
        }
        debug.accept("Warning sent to " + player.getName());

        MessageUtil.send(player, render(resolvePlaceholders(player, warnMessage), "%message%", violation.message()));
    }

    private void notifyStaff(Player player, Violation violation, ViolationRule rule) {
        String template = rule.staffNotifyMessage();
        if (template == null || template.isEmpty()) {
            return;
        }

        Component component = render(resolvePlaceholders(player, template), "{message}", violation.message());

        String permission = rule.staffNotifyPermission();
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> (permission != null && p.hasPermission(permission)) || p.isOp())
                .forEach(p -> MessageUtil.send(p, component));

        if (rule.consoleNotify()) {
            MessageUtil.send(Bukkit.getConsoleSender(), component);
        }
    }

    private void executeCommand(Player player, String command) {
        String resolvedCommand = toConsoleCommand(resolvePlaceholders(player, command));
        if (resolvedCommand.isEmpty()) {
            return;
        }

        debug.accept("Action command: " + resolvedCommand);
        try {
            // Run command sync on main thread
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("nonchat"), () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedCommand);
            });
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().log(Level.WARNING, "&#FFAFFB[nonchat] &cFailed to execute sanction command: {0}", e.getMessage());
        }
    }

    /**
     * Strips the leading slash that configs often include: dispatchCommand expects the bare command
     * @return The command to dispatch, or an empty string if there is nothing to run
     */
    static String toConsoleCommand(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
    }

    private String resolvePlaceholders(Player player, String text) {
        if (player == null || text == null) return text;

        // Check if PlaceholderAPI is loaded
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                return PlaceholderAPI.setPlaceholders(player, text);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "&#FFAFFB[nonchat] &cError processing placeholders: {0}", e.getMessage());
            }
        }

        // Fall back to standard placeholders if PAPI not available
        return text.replace("%player_name%", player.getName())
                .replace("%player_uuid%", player.getUniqueId().toString());
    }

    /**
     * Parses a trusted template and inserts the flagged message as literal text, so formatting
     * and MiniMessage tags typed by the player (e.g. click events) are never parsed
     * @param template Template with placeholders already resolved
     * @param token Token replaced by the message
     * @param message The flagged message
     */
    static Component render(String template, String token, String message) {
        return ColorUtil.parseComponentCached(template)
                .replaceText(builder -> builder.matchLiteral(token)
                        .replacement(Component.text(orEmpty(message))));
    }

    private static String orEmpty(String message) {
        return message != null ? message : "[empty]";
    }
}
