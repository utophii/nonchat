package com.nonxedy.nonchat.util.special.mention;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.bossbar.BossBar;

/**
 * Notification shown to a player who was mentioned in chat.
 *
 * <p>Where and how the player is notified is described by a single config string
 * ({@code mentions.mention-message}) written as {@code <type>:<text>}:</p>
 *
 * <ul>
 * <li>{@code message:<text>} - a regular chat message</li>
 * <li>{@code actionbar:<text>} - text above the hotbar</li>
 * <li>{@code title:<text>} - a title on screen ({@code titlebar} is accepted as well)</li>
 * <li>{@code bossbar:<text>} - a boss bar</li>
 * </ul>
 *
 * <p>An empty value (as well as {@code none}, {@code off}, {@code disabled} or
 * {@code false}) disables the notification completely. So does a value without a
 * known type: nothing is guessed, the problem is only reported.</p>
 *
 * <p>Options for a single type can be appended with a {@code |} separator:</p>
 *
 * <pre>
 * title:&amp;#84FFB8You were mentioned!|subtitle:&amp;#ffffffby {player}|fadein:10|stay:70|fadeout:20
 * bossbar:&amp;#84FFB8{player} mentioned you|color:pink|overlay:progress|progress:1.0|duration:5
 * </pre>
 *
 * <p>Parsing never throws. Broken option values are reported through
 * {@link #problems()} so the caller can warn about them once instead of on every
 * mention.</p>
 *
 * @param type     where the notification is shown, {@code null} when disabled
 * @param content  main text of the notification, never {@code null}
 * @param subtitle title subtitle, {@code null} when not configured
 * @param title    title animation timings
 * @param bossBar  boss bar appearance and lifetime
 * @param problems human readable description of everything that was ignored or defaulted
 */
public record MentionNotification(Type type, String content, String subtitle,
                                  TitleSettings title, BossBarSettings bossBar,
                                  List<String> problems) {

    /** Default title fade in, in ticks. */
    public static final int DEFAULT_FADE_IN = 10;
    /** Default title stay, in ticks. */
    public static final int DEFAULT_STAY = 70;
    /** Default title fade out, in ticks. */
    public static final int DEFAULT_FADE_OUT = 20;
    /** Default boss bar lifetime, in seconds. */
    public static final int DEFAULT_BOSS_BAR_DURATION = 5;

    /** Longest accepted title timing, in ticks (one minute). */
    private static final int MAX_TICKS = 1200;
    /** Longest accepted boss bar lifetime, in seconds (ten minutes). */
    private static final int MAX_DURATION_SECONDS = 600;

    private static final TitleSettings DEFAULT_TITLE =
            new TitleSettings(DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);
    private static final BossBarSettings DEFAULT_BOSS_BAR = new BossBarSettings(
            BossBar.Color.PINK, BossBar.Overlay.PROGRESS, BossBar.MAX_PROGRESS, DEFAULT_BOSS_BAR_DURATION);
    private static final MentionNotification DISABLED =
            new MentionNotification(null, "", null, DEFAULT_TITLE, DEFAULT_BOSS_BAR, List.of());
    /** Values that explicitly turn the notification off. */
    private static final List<String> DISABLED_WORDS = List.of("none", "off", "disabled", "false");

    public MentionNotification {
        content = content == null ? "" : content;
        title = title == null ? DEFAULT_TITLE : title;
        bossBar = bossBar == null ? DEFAULT_BOSS_BAR : bossBar;
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    /**
     * Parses the {@code mentions.mention-message} value.
     *
     * @param raw configured notification, may be {@code null} or empty
     * @return parsed notification, disabled when there is nothing to show
     */
    public static MentionNotification parse(String raw) {
        if (raw == null) {
            return DISABLED;
        }

        String value = raw.trim();
        if (value.isEmpty() || DISABLED_WORDS.contains(normalize(value))) {
            return DISABLED;
        }

        List<String> problems = new ArrayList<>();
        int typeSeparator = value.indexOf(':');
        String prefix = typeSeparator < 0 ? "" : value.substring(0, typeSeparator);
        Type type = Type.from(prefix);
        if (type == null) {
            problems.add(typeSeparator < 0
                    ? "missing \"<type>:\" prefix, the notification is disabled"
                    : "unknown notification type \"" + prefix.trim() + "\", the notification is disabled");
            return disabled(problems);
        }

        String[] segments = value.substring(typeSeparator + 1).split("\\|", -1);
        String content = segments[0].trim();
        if (content.isEmpty()) {
            problems.add("no text after \"" + prefix.trim() + ":\", the notification is disabled");
            return disabled(problems);
        }

        String subtitle = null;
        TitleSettings title = DEFAULT_TITLE;
        BossBarSettings bossBar = DEFAULT_BOSS_BAR;

        for (int index = 1; index < segments.length; index++) {
            String segment = segments[index].trim();
            if (segment.isEmpty()) {
                continue;
            }

            int optionSeparator = segment.indexOf(':');
            String option = optionSeparator < 0 ? "" : normalize(segment.substring(0, optionSeparator));
            String argument = optionSeparator < 0 ? segment : segment.substring(optionSeparator + 1).trim();

            switch (option) {
                case "subtitle" -> {
                    if (applies(type, Type.TITLE, option, problems)) {
                        subtitle = argument;
                    }
                }
                case "fadein", "stay", "fadeout" -> {
                    if (applies(type, Type.TITLE, option, problems)) {
                        title = withTiming(title, option, ticks(argument, timing(option), option, problems));
                    }
                }
                case "color" -> {
                    if (applies(type, Type.BOSSBAR, option, problems)) {
                        bossBar = new BossBarSettings(color(argument, problems), bossBar.overlay(),
                                bossBar.progress(), bossBar.durationSeconds());
                    }
                }
                case "overlay" -> {
                    if (applies(type, Type.BOSSBAR, option, problems)) {
                        bossBar = new BossBarSettings(bossBar.color(), overlay(argument, problems),
                                bossBar.progress(), bossBar.durationSeconds());
                    }
                }
                case "progress" -> {
                    if (applies(type, Type.BOSSBAR, option, problems)) {
                        bossBar = new BossBarSettings(bossBar.color(), bossBar.overlay(),
                                progress(argument, problems), bossBar.durationSeconds());
                    }
                }
                case "duration" -> {
                    if (applies(type, Type.BOSSBAR, option, problems)) {
                        bossBar = new BossBarSettings(bossBar.color(), bossBar.overlay(), bossBar.progress(),
                                number(argument, DEFAULT_BOSS_BAR_DURATION, option, 1, MAX_DURATION_SECONDS, problems));
                    }
                }
                case "" -> {
                    // "title:Main|Subtitle" is a shortcut for "title:Main|subtitle:Subtitle".
                    if (type == Type.TITLE && subtitle == null) {
                        subtitle = segment;
                    } else {
                        problems.add("ignoring \"" + segment + "\": options must be written as \"<option>:<value>\"");
                    }
                }
                default -> problems.add("ignoring unknown option \"" + option + "\"");
            }
        }

        return new MentionNotification(type, content, subtitle, title, bossBar, problems);
    }

    /**
     * A disabled notification, used when the config value is empty.
     *
     * @return notification that shows nothing
     */
    public static MentionNotification disabled() {
        return DISABLED;
    }

    /**
     * A disabled notification that still carries the reason it was rejected.
     *
     * @param problems what is wrong with the configured value
     * @return notification that shows nothing
     */
    private static MentionNotification disabled(List<String> problems) {
        return new MentionNotification(null, "", null, DEFAULT_TITLE, DEFAULT_BOSS_BAR, problems);
    }

    /**
     * Checks whether something has to be shown to the mentioned player.
     *
     * @return true when a notification is configured
     */
    public boolean isEnabled() {
        return type != null;
    }

    private static boolean applies(Type configured, Type required, String option, List<String> problems) {
        if (configured == required) {
            return true;
        }
        problems.add("ignoring \"" + option + "\": it only applies to "
                + required.name().toLowerCase(Locale.ROOT) + " notifications");
        return false;
    }

    private static TitleSettings withTiming(TitleSettings settings, String option, int value) {
        return switch (option) {
            case "fadein" -> new TitleSettings(value, settings.stay(), settings.fadeOut());
            case "stay" -> new TitleSettings(settings.fadeIn(), value, settings.fadeOut());
            default -> new TitleSettings(settings.fadeIn(), settings.stay(), value);
        };
    }

    private static int timing(String option) {
        return switch (option) {
            case "fadein" -> DEFAULT_FADE_IN;
            case "stay" -> DEFAULT_STAY;
            default -> DEFAULT_FADE_OUT;
        };
    }

    private static int ticks(String raw, int defaultValue, String option, List<String> problems) {
        return number(raw, defaultValue, option, 0, MAX_TICKS, problems);
    }

    private static int number(String raw, int defaultValue, String option, int min, int max, List<String> problems) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                problems.add("\"" + option + "\" must be between " + min + " and " + max + ", using " + defaultValue);
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException e) {
            problems.add("\"" + option + "\" must be a number, using " + defaultValue);
            return defaultValue;
        }
    }

    private static float progress(String raw, List<String> problems) {
        try {
            float value = Float.parseFloat(raw.trim());
            if (value < BossBar.MIN_PROGRESS || value > BossBar.MAX_PROGRESS) {
                problems.add("\"progress\" must be between 0.0 and 1.0, using " + BossBar.MAX_PROGRESS);
                return BossBar.MAX_PROGRESS;
            }
            return value;
        } catch (NumberFormatException e) {
            problems.add("\"progress\" must be a number, using " + BossBar.MAX_PROGRESS);
            return BossBar.MAX_PROGRESS;
        }
    }

    private static BossBar.Color color(String raw, List<String> problems) {
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            problems.add("unknown boss bar color \"" + raw.trim() + "\", using "
                    + DEFAULT_BOSS_BAR.color().name().toLowerCase(Locale.ROOT));
            return DEFAULT_BOSS_BAR.color();
        }
    }

    private static BossBar.Overlay overlay(String raw, List<String> problems) {
        try {
            return BossBar.Overlay.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            problems.add("unknown boss bar overlay \"" + raw.trim() + "\", using "
                    + DEFAULT_BOSS_BAR.overlay().name().toLowerCase(Locale.ROOT));
            return DEFAULT_BOSS_BAR.overlay();
        }
    }

    /**
     * Normalizes a config keyword so {@code Boss_Bar}, {@code boss-bar} and
     * {@code BOSSBAR} all mean the same thing.
     */
    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Where the notification is displayed. */
    public enum Type {
        /** A regular chat message. */
        MESSAGE,
        /** Text above the hotbar. */
        ACTIONBAR,
        /** A title in the middle of the screen. */
        TITLE,
        /** A boss bar at the top of the screen. */
        BOSSBAR;

        /**
         * Resolves the type written in front of the notification text.
         *
         * @param prefix configured prefix, for example {@code actionbar}
         * @return matching type or {@code null} when the prefix is not recognized
         */
        public static Type from(String prefix) {
            if (prefix == null) {
                return null;
            }
            return switch (normalize(prefix)) {
                case "message", "msg", "chat" -> MESSAGE;
                case "actionbar", "action-bar" -> ACTIONBAR;
                case "title", "titlebar", "title-bar" -> TITLE;
                case "bossbar", "boss-bar" -> BOSSBAR;
                default -> null;
            };
        }
    }

    /**
     * Title animation timings.
     *
     * @param fadeIn  fade in time in ticks
     * @param stay    time the title stays on screen in ticks
     * @param fadeOut fade out time in ticks
     */
    public record TitleSettings(int fadeIn, int stay, int fadeOut) {}

    /**
     * Boss bar appearance and lifetime.
     *
     * @param color           bar color
     * @param overlay         bar overlay (segmented styles)
     * @param progress        filled part of the bar, from 0.0 to 1.0
     * @param durationSeconds seconds the bar stays visible before it is hidden again
     */
    public record BossBarSettings(BossBar.Color color, BossBar.Overlay overlay, float progress, int durationSeconds) {}
}
