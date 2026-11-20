package com.nonxedy.nonchat.util.chat.filters;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import com.nonxedy.nonchat.config.PluginMessages;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;

public class LinkDetector {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://|www\\.)?\\S+\\.[a-z]{2,}\\S*)",
            Pattern.CASE_INSENSITIVE);

    private static PluginMessages messages;

    public static void initialize(PluginMessages messages) {
        LinkDetector.messages = messages;
    }

    /**
     * Explicit schemes and a {@code www.} prefix are unambiguous; bare
     * matches are only linkified when they end in a real TLD, so ordinary
     * "word.word" text such as "hello.hello" stays plain.
     */
    private static boolean isLinkCandidate(String candidate) {
        String lower = candidate.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("www.")) {
            return true;
        }
        return TldList.hasKnownTld(candidate);
    }

    public static Component makeLinksClickable(String text) {
        return makeLinksClickable(text, ColorUtil::parseComponent);
    }

    public static Component makeLinksClickable(String text, Player sender) {
        return makeLinksClickable(text, value -> ColorUtil.parseMessageComponent(value, sender));
    }

    private static Component makeLinksClickable(String text, Function<String, Component> parser) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        String cleanText = ColorUtil.stripFormatting(text);
        Matcher matcher = URL_PATTERN.matcher(cleanText);

        if (!matcher.find()) {
            return parser.apply(text);
        }

        matcher.reset();

        List<Component> parts = new ArrayList<>();
        int originalLastEnd = 0;

        while (matcher.find()) {
            String cleanUrl = cleanText.substring(matcher.start(), matcher.end());

            int originalUrlStart = text.indexOf(cleanUrl, originalLastEnd);
            if (originalUrlStart == -1) {
                originalUrlStart = originalLastEnd;
            }
            int originalUrlEnd = originalUrlStart + cleanUrl.length();

            String beforeUrl = text.substring(originalLastEnd, originalUrlStart);
            if (!beforeUrl.isEmpty()) {
                parts.add(parser.apply(beforeUrl));
            }

            Component link = isLinkCandidate(cleanUrl) ? createLinkComponent(cleanUrl) : null;
            if (link != null) {
                parts.add(link);
            } else {
                parts.add(parser.apply(cleanUrl));
            }

            originalLastEnd = originalUrlEnd;
        }

        String afterLastUrl = text.substring(originalLastEnd);
        if (!afterLastUrl.isEmpty()) {
            parts.add(parser.apply(afterLastUrl));
        }

        return Component.join(JoinConfiguration.noSeparators(), parts);
    }

    private static Component createLinkComponent(String url) {
        String clickableUrl = url;
        if (url.toLowerCase().startsWith("www.")) {
            clickableUrl = "https://" + url;
        } else if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
            clickableUrl = "https://" + url;
        }

        // Newer Adventure versions reject such URLs in ClickEvent.openUrl
        try {
            if (new URI(clickableUrl).getHost() == null) return null;
        } catch (URISyntaxException e) {
            return null;
        }

        Component hoverComponent;
        if (messages != null) {
            String hoverText = messages.getString("link-hover").replace("{url}", clickableUrl);
            hoverComponent = ColorUtil.parseComponent(hoverText);
        } else {
            hoverComponent = Component.text("Click to open: " + clickableUrl);
        }

        return Component.text(url)
                .clickEvent(ClickEvent.openUrl(clickableUrl))
                .hoverEvent(HoverEvent.showText(hoverComponent))
                .decoration(TextDecoration.UNDERLINED, true);
    }
}