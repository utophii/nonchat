package com.nonxedy.nonchat.util.chat.filters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

class LinkDetectorTest {

    private static boolean hasClickEvent(Component component) {
        if (component instanceof TextComponent text && text.clickEvent() != null) {
            return true;
        }
        return component.children().stream().anyMatch(LinkDetectorTest::hasClickEvent);
    }

    @Test
    void doesNotLinkifyOrdinaryDottedWords() {
        assertFalse(hasClickEvent(LinkDetector.makeLinksClickable("hello.hello")));
        assertFalse(hasClickEvent(LinkDetector.makeLinksClickable("hello.hello how are you")));
        assertFalse(hasClickEvent(LinkDetector.makeLinksClickable("check file.name.txt please")));
    }

    @Test
    void linkifiesRealDomains() {
        assertTrue(hasClickEvent(LinkDetector.makeLinksClickable("visit google.com now")));
        assertTrue(hasClickEvent(LinkDetector.makeLinksClickable("join mc.example.com/join")));
        assertTrue(hasClickEvent(LinkDetector.makeLinksClickable("discord.gg/abc123")));
    }

    @Test
    void linkifiesExplicitUrlsEvenWithUnknownTld() {
        assertTrue(hasClickEvent(LinkDetector.makeLinksClickable("http://hello.hello")));
        assertTrue(hasClickEvent(LinkDetector.makeLinksClickable("www.something.weird")));
    }

    @Test
    void keepsVersionNumbersPlain() {
        assertFalse(hasClickEvent(LinkDetector.makeLinksClickable("i play on 1.21.11")));
    }
}
