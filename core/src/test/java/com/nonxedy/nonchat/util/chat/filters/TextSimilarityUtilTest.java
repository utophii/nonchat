package com.nonxedy.nonchat.util.chat.filters;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextSimilarityUtilTest {

    @Test
    void treatsCaseAndSurroundingWhitespaceAsEqual() {
        assertEquals(1.0, TextSimilarityUtil.calculateSimilarity("  Hello World ", "hello world"));
    }

    @Test
    void returnsExpectedScoreForSingleCharacterEdit() {
        assertEquals(0.8, TextSimilarityUtil.calculateSimilarity("hello", "hallo"), 0.0001);
    }

    @Test
    void returnsZeroWhenEitherValueIsNull() {
        assertEquals(0.0, TextSimilarityUtil.calculateSimilarity(null, "message"));
        assertEquals(0.0, TextSimilarityUtil.calculateSimilarity("message", null));
        assertEquals(0.0, TextSimilarityUtil.calculateSimilarity(null, null));
    }

    @Test
    void differentiatesUnrelatedMessages() {
        assertTrue(TextSimilarityUtil.calculateSimilarity("diamond", "potato") < 0.3);
    }

    @Test
    void treatsEmptyStringsAsIdentical() {
        assertEquals(1.0, TextSimilarityUtil.calculateSimilarity("", ""));
        assertEquals(1.0, TextSimilarityUtil.calculateSimilarity("   ", ""));
    }

    @Test
    void doesNotCollapseInternalWhitespace() {
        assertTrue(TextSimilarityUtil.calculateSimilarity("hello  world", "hello world") < 1.0);
    }

    @Test
    void scoresEditsOnLongerStrings() {
        assertEquals(0.9, TextSimilarityUtil.calculateSimilarity("aaaaaaaaaa", "aaaaaaaaab"), 0.0001);
        assertEquals(0.8, TextSimilarityUtil.calculateSimilarity("aaaaaaaaaa", "aaaaaaaabb"), 0.0001);
    }
}
