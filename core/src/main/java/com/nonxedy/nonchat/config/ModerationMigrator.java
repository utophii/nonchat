package com.nonxedy.nonchat.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Moves the moderation settings of the old config.yml (banned-words, caps-filter, anti-ad, anti-spam)
 * into moderation.yml. Every old rule becomes a single sanction tier that fires at the first strike
 * and keeps whether it blocked the message ('block'), so the behavior stays the same. The old sections are then removed from config.yml, after a backup.
 */
final class ModerationMigrator {

    private static final Logger LOGGER = Logger.getLogger("nonchat");
    private static final List<String> LEGACY_SECTIONS = List.of("banned-words", "caps-filter", "anti-ad", "anti-spam");
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^[\\w-]+:.*");
    private static final String LEGACY_STRIKE_EXPIRE = "30m";

    private ModerationMigrator() {
    }

    /**
     * Creates moderation.yml from the old sections of config.yml
     * @param configFile config.yml
     * @param target moderation.yml to create
     * @param messages Language messages, used for the warning/notification texts that used to live there
     * @param template Bundled moderation.yml, so the new file has its layout and comments; may be null
     * @return true if there was something to migrate and moderation.yml was written
     */
    static boolean migrate(File configFile, File target, PluginMessages messages, String template) {
        if (!configFile.exists()) {
            return false;
        }

        FileConfiguration old = YamlConfiguration.loadConfiguration(configFile);
        if (LEGACY_SECTIONS.stream().noneMatch(old::contains)) {
            return false;
        }

        try {
            YamlConfiguration converted = convert(old, messages);
            Files.writeString(target.toPath(), render(converted, template), StandardCharsets.UTF_8);

            File backup = new File(configFile.getParentFile(), "config.yml.pre-moderation.bak");
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            removeLegacySections(configFile);
            LOGGER.log(Level.INFO, "[nonchat] Moved {0} from config.yml to moderation.yml (backup: {1})",
                    new Object[] {LEGACY_SECTIONS, backup.getName()});
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[nonchat] Could not migrate moderation settings: {0}", e.getMessage());
            target.delete();
            return false;
        }
    }

    /**
     * Renders the converted settings on top of the bundled file, so the result has the same comments
     * and layout as a fresh moderation.yml. Without a usable template a plain file with a short header is written.
     */
    private static String render(YamlConfiguration converted, String template) {
        if (template != null) {
            try {
                return ConfigUpdater.update(template, converted.saveToString(), config -> true).content();
            } catch (InvalidConfigurationException e) {
                LOGGER.log(Level.WARNING, "[nonchat] Bundled moderation.yml is not valid, writing a plain file: {0}",
                        e.getMessage());
            }
        }
        converted.options().setHeader(List.of(
                "Chat moderation settings (moved here from the old config.yml sections).",
                "Each violation kind has sanction 'tiers': once a player has 'strikes' violations within 'expire',",
                "the tier's actions run. 'block: false' only reports a violation. Actions: 'notify-staff' or any console",
                "command (%player_name% is replaced). Durations: number + s/m/h/d."));
        return converted.saveToString();
    }

    static YamlConfiguration convert(FileConfiguration old, PluginMessages messages) {
        YamlConfiguration out = new YamlConfiguration();

        ConfigurationSection words = old.getConfigurationSection("banned-words");
        out.set("banned-words.enabled", old.getBoolean("banned-words.enabled", true));
        out.set("banned-words.block", blocks(old, "banned-words"));
        out.set("banned-words.words", old.isList("banned-words") ? old.getStringList("banned-words")
                : old.getStringList("banned-words.words"));
        out.set("banned-words.whole-words", old.getBoolean("banned-words.whole-words", false));
        out.set("banned-words.exceptions", old.getStringList("banned-words.exceptions"));
        out.set("banned-words.patterns", words != null ? old.getStringList("banned-words.patterns")
                : old.getStringList("banned-patterns"));
        out.set("banned-words.message", text(old, "banned-words.message", messages, "blocked-words",
                "&#ff0000You are not allowed to use this word!"));
        out.set("banned-words.notify-message", text(old, "banned-words.notify-message", messages,
                "banned-words-detected", ""));
        out.set("banned-words.console-notify", old.getBoolean("banned-words.console-notify", true));
        out.set("banned-words.tiers", tiers(old.getStringList("banned-words.actions")));

        out.set("caps.enabled", old.getBoolean("caps-filter.enabled", true));
        out.set("caps.block", blocks(old, "caps-filter"));
        out.set("caps.max-caps-percentage", old.getInt("caps-filter.max-caps-percentage", 70));
        out.set("caps.min-length", old.getInt("caps-filter.min-length", 4));
        out.set("caps.message", text(old, "caps-filter.message", messages, "caps-filter", ""));
        out.set("caps.notify-message", old.getString("caps-filter.notify-message",
                "&c%player_name% used excessive caps: {message}"));
        out.set("caps.console-notify", old.getBoolean("caps-filter.console-notify", false));
        out.set("caps.tiers", tiers(old.contains("caps-filter.actions")
                ? old.getStringList("caps-filter.actions") : List.of()));

        // Before 1.6.6 the ad filter had no action list: staff notification and punishment were separate options
        boolean staffNotify = old.getBoolean("anti-ad.staff-notify", true);
        List<String> adActions = new ArrayList<>();
        if (old.contains("anti-ad.actions")) {
            adActions.addAll(old.getStringList("anti-ad.actions"));
        } else {
            if (staffNotify) {
                adActions.add("notify-staff");
            }
            String punishCommand = old.getString("anti-ad.punish-command", "ban %player_name% advertising");
            if (punishCommand != null && !punishCommand.isEmpty()) {
                adActions.add(punishCommand);
            }
        }
        out.set("advertising.enabled", old.getBoolean("anti-ad.enabled", true));
        out.set("advertising.block", blocks(old, "anti-ad"));
        out.set("advertising.sensitivity", old.getDouble("anti-ad.sensitivity", 0.7));
        out.set("advertising.whitelisted-urls", old.getStringList("anti-ad.whitelisted-urls"));
        out.set("advertising.update-tld-list", old.getBoolean("anti-ad.update-tld-list", true));
        out.set("advertising.tld-list-url", old.getString("anti-ad.tld-list-url",
                "https://data.iana.org/TLD/tlds-alpha-by-domain.txt"));
        out.set("advertising.message", text(old, "anti-ad.message", messages, "blocked-words", ""));
        out.set("advertising.notify-message", old.getString("anti-ad.notify-message",
                "&#FFAFFB[nonchat] &f%player_name% posted advertisement: &#ff0000{message}"));
        out.set("advertising.console-notify", old.getBoolean("anti-ad.console-notify", staffNotify));
        out.set("advertising.tiers", tiers(adActions));

        out.set("spam.enabled", old.getBoolean("anti-spam.enabled", true));
        out.set("spam.repetitive.enabled", old.getBoolean("anti-spam.repetitive.enabled", true));
        out.set("spam.repetitive.threshold", old.getInt("anti-spam.repetitive.threshold", 3));
        out.set("spam.repetitive.time-window", old.getInt("anti-spam.repetitive.time-window", 10));
        out.set("spam.similar.enabled", old.getBoolean("anti-spam.similar.enabled", true));
        out.set("spam.similar.threshold", old.getDouble("anti-spam.similar.threshold", 0.85));
        out.set("spam.similar.time-window", old.getInt("anti-spam.similar.time-window", 30));
        out.set("spam.flood.enabled", old.getBoolean("anti-spam.flood.enabled", true));
        out.set("spam.flood.max-messages", old.getInt("anti-spam.flood.max-messages", 5));
        out.set("spam.flood.time-window", old.getInt("anti-spam.flood.time-window", 5));
        // Each kind of spam had its own actions, so each keeps its own tiers
        spamKind(out, old, messages, "repetitive", "&cPlease do not send the same message repeatedly!");
        spamKind(out, old, messages, "similar", "&cPlease do not send similar messages repeatedly!");
        spamKind(out, old, messages, "flood", "&cYou are sending messages too quickly! Please slow down.");

        return out;
    }

    private static void spamKind(YamlConfiguration out, FileConfiguration old, PluginMessages messages,
                                 String kind, String defaultWarning) {
        String path = "anti-spam." + kind;
        out.set("spam." + kind + ".block", blocks(old, path));
        out.set("spam." + kind + ".message", old.getString(path + ".message", defaultWarning));
        out.set("spam." + kind + ".notify-message", text(old, path + ".notify-message", messages,
                "spam-detected-" + kind, ""));
        // Console notification used to be one switch for every kind
        out.set("spam." + kind + ".console-notify",
                old.getBoolean(path + ".console-notify", old.getBoolean("anti-spam.console-notify", true)));
        out.set("spam." + kind + ".tiers", tiers(old.getStringList(path + ".actions")));
    }

    /**
     * Reads a message template; a missing value or "@language" means the text of the language file
     */
    private static String text(FileConfiguration old, String path, PluginMessages messages,
                               String languageKey, String fallback) {
        String value = old.getString(path);
        if (value != null && !value.equals("@language")) {
            return value;
        }
        String translated = messages != null ? messages.getString(languageKey) : null;
        return translated != null ? translated : fallback;
    }

    /**
     * Whether an old rule blocked the message: with an action list (1.6.6) only when it contains 'block',
     * without one (1.6.5) always
     */
    private static boolean blocks(FileConfiguration old, String path) {
        return !old.isList(path + ".actions")
                || old.getStringList(path + ".actions").stream().anyMatch(ModerationMigrator::isBlock);
    }

    private static boolean isBlock(String action) {
        return action.trim().equalsIgnoreCase("block");
    }

    // 'block' is not an action any more: it became the rule's 'block' setting
    private static List<Map<String, Object>> tiers(List<String> actions) {
        Map<String, Object> tier = new LinkedHashMap<>();
        tier.put("strikes", 1);
        tier.put("expire", LEGACY_STRIKE_EXPIRE);
        tier.put("actions", new ArrayList<>(actions.stream().filter(action -> !isBlock(action)).toList()));
        return List.of(tier);
    }

    /**
     * Removes the legacy sections from config.yml keeping the rest of the file (and its comments) untouched
     */
    private static void removeLegacySections(File configFile) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8));
        for (String section : LEGACY_SECTIONS) {
            removeSection(lines, section);
        }
        Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
    }

    private static void removeSection(List<String> lines, String key) {
        int keyLine = indexOfKey(lines, key);
        if (keyLine < 0) {
            return;
        }

        // The section ends at the next top-level key; the comment block right above that key belongs to it
        int end = keyLine + 1;
        while (end < lines.size() && !TOP_LEVEL_KEY.matcher(lines.get(end)).matches()) {
            end++;
        }
        while (end > keyLine + 1 && isCommentOrBlank(lines.get(end - 1))) {
            end--;
        }

        // The comment block right above the key is its header
        int start = keyLine;
        while (start > 0 && isCommentOrBlank(lines.get(start - 1))) {
            start--;
        }

        lines.subList(start, end).clear();
    }

    private static int indexOfKey(List<String> lines, String key) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + ":")) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isCommentOrBlank(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }
}
