package com.nonxedy.nonchat.util.chat.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TldListTest {

    @AfterEach
    void restoreDefaults() {
        TldList.updateRegistry(null);
    }

    @Test
    void rejectsFakeTlds() {
        assertFalse(TldList.isKnownTld("hello"));
        assertFalse(TldList.hasKnownTld("hello.hello"));
        assertFalse(TldList.hasKnownTld("file.name.txt"));
        assertFalse(TldList.hasKnownTld("version.rc5"));
    }

    @Test
    void acceptsRealDomains() {
        assertTrue(TldList.hasKnownTld("google.com"));
        assertTrue(TldList.hasKnownTld("mc.example.com/join"));
        assertTrue(TldList.hasKnownTld("discord.gg/abc123"));
        assertTrue(TldList.hasKnownTld("WWW.Google.COM"));
        assertTrue(TldList.hasKnownTld("google.com,"));
        assertTrue(TldList.hasKnownTld("(discord.gg/abc)"));
    }

    @Test
    void bundledRegistryCoversRareRealTlds() {
        assertTrue(TldList.isKnownTld("museum"));
        assertTrue(TldList.hasKnownTld("server.museum"));
        assertTrue(TldList.isPlausibleHost("play.africa"));
    }

    @Test
    void registryIsReplaceableAtRuntime() throws Exception {
        // A fresh registry (150+ entries) fully replaces the active one.
        java.util.Set<String> fresh = new java.util.HashSet<>();
        for (int i = 0; i < 150; i++) {
            fresh.add("tld" + i);
        }
        fresh.add("com");
        assertTrue(TldList.updateRegistry(fresh));
        assertTrue(TldList.isKnownTld("com"));
        assertTrue(TldList.isKnownTld("tld42"));
        assertFalse(TldList.isKnownTld("ru")); // not in the fresh registry

        // null restores the bundled snapshot.
        assertTrue(TldList.updateRegistry(null));
        assertTrue(TldList.isKnownTld("ru"));
        assertFalse(TldList.isKnownTld("tld42"));
    }

    @Test
    void rejectsSuspiciouslySmallRegistry() {
        // A truncated download or an error page must never disable detection.
        assertFalse(TldList.updateRegistry(List.of("com", "net", "org")));
        // The active registry is untouched.
        assertTrue(TldList.isKnownTld("ru"));
        assertTrue(TldList.isKnownTld("com"));
    }

    @Test
    void parsesIanaFormat() throws Exception {
        String sample = """
                # Version 2026010100, Last Updated Thu Jan  1 00:00:00 2026 UTC
                COM

                net
                RU
                """;
        java.util.Set<String> parsed = TldList.parseRegistry(
                new java.io.ByteArrayInputStream(sample.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(java.util.Set.of("com", "net", "ru"), parsed);
    }

    @Test
    void isKnownTldIsCaseInsensitive() {
        assertTrue(TldList.isKnownTld("COM"));
        assertTrue(TldList.isKnownTld("Ru"));
        assertFalse(TldList.isKnownTld(null));
    }

    @Test
    void plausibleHostAcceptsIpv4AndRealTldsOnly() {
        assertTrue(TldList.isPlausibleHost("192.168.1.5:25565"));
        assertTrue(TldList.isPlausibleHost("mc.example.com"));
        assertFalse(TldList.isPlausibleHost("hello.hello"));
        assertFalse(TldList.isPlausibleHost("1.21.11"));
    }

    @Test
    void extractTldIgnoresSchemeUserinfoPortPathAndPunctuation() {
        assertEquals("com",
                TldList.extractTld("https://user:pass@Mc.Example.COM:8080/path?q=1#frag"));
        assertEquals("gg", TldList.extractTld("(discord.gg/abc)"));
        assertEquals("com", TldList.extractTld("google.com,"));
        assertNull(TldList.extractTld("1.21.11"));
        assertNull(TldList.extractTld("nodots"));
        assertNull(TldList.extractTld(""));
        assertNull(TldList.extractTld(null));
    }
}
