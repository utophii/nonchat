package com.nonxedy.nonchat.util.chat.filters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdDetectorTest {

    private static boolean flags(float sensitivity, String message) {
        AdDetector detector = new AdDetector(null, sensitivity, null, false, null);
        return detector.shouldFilter(null, message, name -> false);
    }

    @Test
    void doesNotFlagOrdinaryDottedWords() {
        assertFalse(flags(0.7f, "hello.hello"));
        assertFalse(flags(0.7f, "hello.hello how are you"));
        assertFalse(flags(0.7f, "check file.name.txt please"));
    }

    @Test
    void doesNotFlagVersionNumbers() {
        assertFalse(flags(0.7f, "i play on 1.21.11"));
    }

    @Test
    void flagsBareDomainsWithRealTld() {
        assertTrue(flags(0.7f, "come to mc.example.com"));
        assertTrue(flags(0.7f, "best server discord.gg/abc123"));
    }

    @Test
    void flagsIpAddresses() {
        assertTrue(flags(0.7f, "connect to 192.168.1.5:25565"));
    }

    @Test
    void flagsExplicitUrlsEvenWithUnknownTld() {
        assertTrue(flags(0.7f, "go to http://hello.hello"));
        assertTrue(flags(0.0f, "go to http://hello.hello"));
    }

    @Test
    void sensitivityBelowBareHostThresholdIgnoresBareDomains() {
        assertFalse(flags(0.1f, "come to mc.example.com"));
        assertTrue(flags(0.25f, "come to mc.example.com"));
    }
}
