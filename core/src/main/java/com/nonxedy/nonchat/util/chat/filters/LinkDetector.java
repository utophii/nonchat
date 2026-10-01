package com.nonxedy.nonchat.util.chat.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        String cleanText = ColorUtil.stripFormatting(text);
        Matcher matcher = URL_PATTERN.matcher(cleanText);

        if (!matcher.find()) {
            return ColorUtil.parseComponent(text);
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
                parts.add(ColorUtil.parseComponent(beforeUrl));
            }

            if (isLinkCandidate(cleanUrl)) {
                parts.add(createLinkComponent(cleanUrl));
            } else {
                parts.add(ColorUtil.parseComponent(cleanUrl));
            }

            originalLastEnd = originalUrlEnd;
        }

        String afterLastUrl = text.substring(originalLastEnd);
        if (!afterLastUrl.isEmpty()) {
            parts.add(ColorUtil.parseComponent(afterLastUrl));
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