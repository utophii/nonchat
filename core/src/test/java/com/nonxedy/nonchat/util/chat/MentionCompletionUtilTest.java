package com.nonxedy.nonchat.util.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MentionCompletionUtilTest {
    private static final Set<String> ONLINE = Set.of("Alex", ".Steve", "+Kate");

    @AfterEach
    void resetPrefix() {
        MentionPatterns.setBedrockPrefix("");
    }

    @Test
    void stripsPrefixedBedrockMention() {
        MentionPatterns.setBedrockPrefix(".");
        assertEquals("hello", MentionCompletionUtil.stripMentions("hello @.Steve", ONLINE::contains));
        assertEquals("hello", MentionCompletionUtil.stripMentions("hello .Steve", ONLINE::contains));
    }

    @Test
    void stripsPlainMentionWhenPrefixConfigured() {
        MentionPatterns.setBedrockPrefix(".");
        assertEquals("hello", MentionCompletionUtil.stripMentions("hello @Alex", ONLINE::contains));
    }

    @Test
    void followsPrefixChangesAtRuntime() {
        MentionPatterns.setBedrockPrefix(".");
        assertEquals("hi @+Kate", MentionCompletionUtil.stripMentions("hi @+Kate", ONLINE::contains));
        MentionPatterns.setBedrockPrefix("+");
        assertEquals("hi", MentionCompletionUtil.stripMentions("hi @+Kate", ONLINE::contains));
    }

    @Test
    void keepsOfflinePlayersAndLinks() {
        MentionPatterns.setBedrockPrefix(".");
        assertEquals("@.Nobody here", MentionCompletionUtil.stripMentions("@.Nobody here", ONLINE::contains));
        assertEquals("see Alex.com", MentionCompletionUtil.stripMentions("see Alex.com", ONLINE::contains));
    }
}
