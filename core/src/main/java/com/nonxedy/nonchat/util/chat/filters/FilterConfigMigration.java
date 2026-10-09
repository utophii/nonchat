package com.nonxedy.nonchat.util.chat.filters;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

// Runs before default keys are merged, so legacy choices take precedence
public final class FilterConfigMigration {
    private FilterConfigMigration() {}

    public static boolean migrate(ConfigurationSection config) {
        boolean changed = false;
        if (config.isConfigurationSection("anti-ad")) {
            boolean notify = config.getBoolean("anti-ad.staff-notify", true);
            if (!config.contains("anti-ad.actions")) {
                List<String> actions = new ArrayList<>(List.of("block"));
                if (notify) actions.add("notify-staff");
                String command = config.getString("anti-ad.punish-command", "ban %player_name% advertising");
                if (!command.isBlank()) actions.add(command);
                config.set("anti-ad.actions", actions);
                changed = true;
            }
            if (!config.contains("anti-ad.console-notify")) {
                config.set("anti-ad.console-notify", notify);
                changed = true;
            }
        }
        if (config.contains("anti-spam.console-notify")) {
            for (String type : List.of("repetitive", "similar", "flood")) {
                String key = "anti-spam." + type + ".console-notify";
                if (!config.contains(key)) {
                    config.set(key, config.getBoolean("anti-spam.console-notify"));
                    changed = true;
                }
            }
        }
        return changed;
    }
}
