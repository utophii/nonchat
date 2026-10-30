package com.nonxedy.nonchat.moderation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

import com.nonxedy.nonchat.config.ModerationConfig;

/**
 * Builds the {@link ViolationRule} of each violation from moderation.yml:
 * <pre>
 * advertising:           # also: spam, banned-words, caps
 *   message: "..."       # warning sent to the player
 *   notify-message: "..." # 'notify-staff' text
 *   tiers:
 *     - strikes: 3
 *       expire: 1h       # number + s/m/h/d
 *       actions: ["kick %player_name%"]
 * </pre>
 * Spam has a section per kind (repetitive, similar, flood).
 * Rules are built on every violation, so config reloads are picked up immediately.
 */
public class ModerationRuleProvider implements Function<ViolationType, ViolationRule> {

    private static final Logger LOGGER = Logger.getLogger("nonchat");

    private final ModerationConfig config;

    public ModerationRuleProvider(ModerationConfig config) {
        this.config = config;
    }

    @Override
    public ViolationRule apply(ViolationType type) {
        return switch (type) {
            case BANNED_WORD -> rule(type, section("banned-words"),
                    "nonchat.wordblocker.notify", consoleNotify("banned-words", true));
            case CAPS -> rule(type, section("caps"), "nonchat.caps.notify", consoleNotify("caps", false));
            case SPAM_REPETITIVE -> spamRule(type, "repetitive");
            case SPAM_SIMILAR -> spamRule(type, "similar");
            case SPAM_FLOOD -> spamRule(type, "flood");
            case ADVERTISING -> rule(type, section("advertising"),
                    "nonchat.ad.notify", consoleNotify("advertising", true));
        };
    }

    private ViolationRule spamRule(ViolationType type, String kind) {
        ConfigurationSection spam = section("spam");
        ConfigurationSection kindSection = spam != null ? spam.getConfigurationSection(kind) : null;
        boolean fallback = spam == null || spam.getBoolean("console-notify", true);
        boolean consoleNotify = kindSection != null ? kindSection.getBoolean("console-notify", fallback) : fallback;
        return rule(type, kindSection, "nonchat.spam.notify", consoleNotify);
    }

    private boolean consoleNotify(String key, boolean fallback) {
        ConfigurationSection section = section(key);
        return section != null ? section.getBoolean("console-notify", fallback) : fallback;
    }

    /**
     * @param own Section with the message, notify-message and tiers of this violation; may be null
     */
    private ViolationRule rule(ViolationType type, ConfigurationSection own,
                               String permission, boolean consoleNotify) {
        List<SanctionTier> tiers = own != null && own.isList("tiers")
                ? parseTiers(type, own.getMapList("tiers"))
                : List.of();

        if (own == null) {
            // No section (e.g. a spam kind missing from the file): no messages and no tiers
            return new ViolationRule(type, null, null, permission, consoleNotify, true, tiers);
        }

        String warn = own.getString("message");
        if (type == ViolationType.CAPS && warn != null) {
            warn = warn.replace("{percentage}", String.valueOf(config.getMaxCapsPercentage()));
        }
        return new ViolationRule(type, warn, own.getString("notify-message"), permission, consoleNotify,
                own.getBoolean("block", true), tiers);
    }

    private ConfigurationSection section(String key) {
        return config.getSection(key);
    }

    private List<SanctionTier> parseTiers(ViolationType type, List<Map<?, ?>> entries) {
        List<SanctionTier> tiers = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            Object strikes = entry.get("strikes");
            Duration expire = parseDuration(String.valueOf(entry.get("expire")));
            if (!(strikes instanceof Number number) || number.intValue() < 1 || expire == null) {
                LOGGER.log(Level.WARNING, "[nonchat] Invalid moderation tier for {0} (needs strikes >= 1 and expire like 10m): {1}",
                        new Object[] {type, entry});
                continue;
            }

            List<String> actions = new ArrayList<>();
            if (entry.get("actions") instanceof List<?> list) {
                list.forEach(action -> actions.add(String.valueOf(action)));
            }
            tiers.add(new SanctionTier(number.intValue(), expire, actions));
        }
        return tiers;
    }

    /**
     * Parses a duration like 30s, 10m, 1h or 7d
     * @return The duration, or null if the text is not valid
     */
    static Duration parseDuration(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.length() < 2) {
            return null;
        }

        try {
            long amount = Long.parseLong(value.substring(0, value.length() - 1));
            if (amount < 1) {
                return null;
            }
            return switch (value.charAt(value.length() - 1)) {
                case 's' -> Duration.ofSeconds(amount);
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                default -> null;
            };
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }
}
