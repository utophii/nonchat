package com.nonxedy.nonchat.util.chat.filters;

import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

// Shared response policy for every moderation filter
public record FilterSettings(List<String> actions, String message, String notifyMessage,
                             boolean consoleNotify, String permission) {
    public FilterSettings {
        actions = List.copyOf(actions);
    }

    public boolean blocks() {
        return hasAction("block");
    }

    public boolean hasAction(String name) {
        return actions.stream().anyMatch(action -> action.trim().equalsIgnoreCase(name));
    }

    private static String template(ConfigurationSection config, String path, String fallback) {
        String value = config.getString(path, fallback);
        return "@language".equals(value) ? fallback : value;
    }

    public static FilterSettings read(ConfigurationSection config, String path,
            List<String> defaultActions, String defaultMessage, String defaultNotification,
            boolean defaultConsole, String permission) {
        return new FilterSettings(
                config.contains(path + ".actions") ? config.getStringList(path + ".actions") : defaultActions,
                template(config, path + ".message", defaultMessage),
                template(config, path + ".notify-message", defaultNotification),
                config.getBoolean(path + ".console-notify", defaultConsole), permission);
    }
}
