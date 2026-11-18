package com.nonxedy.nonchat.util.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MentionPatternsTest {
    @AfterEach
    void resetPrefix() {
        MentionPatterns.setBedrockPrefix("");
    }

    private static List<String> names(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    void atMentionMatchesBedrockPrefixedName() {
        MentionPatterns.setBedrockPrefix(".");
        assertEquals(List.of(".Steve", "Alex"), names(MentionPatterns.atMention(), "hi @.Steve and @Alex"));
    }

    @Test
    void prefixIsQuotedAsLiteral() {
        MentionPatterns.setBedrockPrefix("+");
        assertEquals(List.of("+Steve"), names(MentionPatterns.atMention(), "hey @+Steve"));
        MentionPatterns.setBedrockPrefix("*");
        assertEquals(List.of("*Steve"), names(MentionPatterns.atMention(), "hey @*Steve"));
    }

    @Test
    void onlyTheConfiguredPrefixIsAccepted() {
        MentionPatterns.setBedrockPrefix("+");
        assertEquals(List.of(), names(MentionPatterns.atMention(), "hey @.Steve"));
    }

    @Test
    void emptyPrefixKeepsPlainNames() {
        MentionPatterns.setBedrockPrefix("");
        assertEquals(List.of(), names(MentionPatterns.atMention(), "hey @.Steve"));
        assertEquals(List.of("Steve"), names(MentionPatterns.atMention(), "hey @Steve"));
    }

    @Test
    void nullPrefixIsTreatedAsEmpty() {
        MentionPatterns.setBedrockPrefix(null);
        assertEquals(List.of("Steve"), names(MentionPatterns.atMention(), "@Steve"));
    }

    @Test
    void bareMentionMatchesPrefixedAndPlainNames() {
        MentionPatterns.setBedrockPrefix(".");
        assertEquals(List.of(".Steve", "hello", "Alex"),
                names(MentionPatterns.bareMention(), ".Steve hello @Alex"));
    }

    @Test
    void trailingPunctuationIsNotPartOfTheName() {
        MentionPatterns.setBedrockPrefix(".");
        assertEquals(List.of(".Steve"), names(MentionPatterns.atMention(), "@.Steve, ciao"));
    }

    @Test
    void rebuildsWhenPrefixChanges() {
        MentionPatterns.setBedrockPrefix(".");
        assertTrue(MentionPatterns.nameRegex().contains("\\Q.\\E"));
        MentionPatterns.setBedrockPrefix("");
        assertFalse(MentionPatterns.nameRegex().contains("\\Q.\\E"));
    }
}
