package com.nonxedy.nonchat.util.chat.filters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nonxedy.nonchat.config.PluginConfig;

class AdDetectorTest {

    private static boolean flags(float sensitivity, String message) {
        return flags(sensitivity, List.of(), message);
    }

    private static boolean flags(float sensitivity, List<String> urlRules, String message) {
        PluginConfig config = mock(PluginConfig.class);
        when(config.getAntiAdWhitelistedUrls()).thenReturn(urlRules);
        AdDetector detector = new AdDetector(config, sensitivity, null, false, null);
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

    @Test
    void whitelistsAConfiguredDomainAndItsSubdomains() {
        List<String> rules = List.of("example.com");

        assertFalse(flags(0.7f, rules, "join example.com"));
        assertFalse(flags(0.7f, rules, "join mc.example.com"));
        assertFalse(flags(0.7f, rules, "join https://eu.mc.example.com:25565/play"));
    }

    @Test
    void anExplicitSubdomainRuleAlsoCoversItsDescendants() {
        List<String> rules = List.of("mc.example.com");

        assertFalse(flags(0.7f, rules, "join mc.example.com"));
        assertFalse(flags(0.7f, rules, "join eu.mc.example.com"));
        assertTrue(flags(0.7f, rules, "join other.example.com"));
    }

    @Test
    void subdomainWhitelistDoesNotMatchLookalikeDomains() {
        List<String> rules = List.of("example.com");

        assertTrue(flags(0.7f, rules, "join notexample.com"));
        assertTrue(flags(0.7f, rules, "join evil-example.com"));
    }

    @Test
    void pathSpecificWhitelistRemainsScopedToThatUrl() {
        List<String> rules = List.of("discord.gg/NAWsxe3J3R");

        assertFalse(flags(0.7f, rules, "join discord.gg/NAWsxe3J3R"));
        assertTrue(flags(0.7f, rules, "join discord.gg/another-invite"));
    }

    @Test
    void wildcardRuleAppliesOnlyToSubdomains() {
        List<String> rules = List.of("*.example.com");

        assertTrue(flags(0.7f, rules, "join example.com"));
        assertFalse(flags(0.7f, rules, "join mc.example.com"));
    }
}
